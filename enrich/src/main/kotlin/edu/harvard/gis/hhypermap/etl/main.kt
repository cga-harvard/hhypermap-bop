/*
 * Copyright 2016 President and Fellows of Harvard College
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.harvard.gis.hhypermap.etl

import com.codahale.metrics.JmxReporter
import com.codahale.metrics.MetricRegistry
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.dropwizard.configuration.ConfigurationSourceProvider
import io.dropwizard.configuration.FileConfigurationSourceProvider
import io.dropwizard.configuration.ResourceConfigurationSourceProvider
import io.dropwizard.configuration.YamlConfigurationFactory
import io.dropwizard.jackson.Jackson
import io.dropwizard.logging.BootstrapLogging
import io.dropwizard.validation.BaseValidator
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.kstream.ValueMapper
import org.apache.solr.client.solrj.StreamingResponseCallback
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.params.ModifiableSolrParams
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

val log = LoggerFactory.getLogger("edu.harvard.gis.hhypermap.etl")!!

val METRIC_REGISTRY = MetricRegistry()

object CLOSE_HOOKS {
  val hooks = ArrayDeque<() -> Unit>()

  init {
    Runtime.getRuntime().addShutdownHook(Thread(null, {
      CLOSE_HOOKS.runCloseHooks()
    }, "closeHookRunner"))
  }

  fun add(hook: ()-> Unit) {
    synchronized(hooks) {
      hooks.addLast(hook)
    }
  }

  fun runCloseHooks() {
    log.debug("Running close hooks")
    synchronized(hooks) { // only run close hooks in serial
      while (true) {
        val hook = hooks.pollLast() ?: break
        try {
          hook.invoke()
        } catch (e: Exception) {
          log.error(e.toString(), e)
        }
      }
    }
  }

}

// We re-use DropWizard infrastructure (YAML config, validation, logging, JMX metrics) even
//    though we aren't a web-service.

/** Main CLI entrypoint */
fun main(args: Array<String>) {
  check(args.size <= 1, {"Accepts optionally the yaml config file"})
  val configFile: File? = if (args.size == 1) File(args[0]) else null

  BootstrapLogging.bootstrap()

  val jmxReporter = JmxReporter.forRegistry(METRIC_REGISTRY).build()
  jmxReporter.start()
  CLOSE_HOOKS.add {jmxReporter.stop()}

  val etlConfig = buildConfig(configFile)

  etlConfig.loggingConfig.configure(METRIC_REGISTRY, "etl")
  CLOSE_HOOKS.add {etlConfig.loggingConfig.stop()}

  val kafkaStreams = buildStreams(etlConfig)
  kafkaStreams.start()
  try {
    val monitor = Object()
    synchronized(monitor) {
      monitor.wait()//forever until interrupted
    }
  } finally {
    kafkaStreams.close()
  }
}

fun buildConfig(configFile: File? = null): EtlConfiguration {
  val configProvider: ConfigurationSourceProvider
  val configPath: String
  if (configFile != null) {
    configProvider = FileConfigurationSourceProvider()
    configPath = configFile.toString()
  } else {
    // TODO take optional String arg and see if it's a URL?
    configProvider = ResourceConfigurationSourceProvider()
    configPath = "etl.yml"
  }
  // propagate env to sys props so DropWizard will see it
  val propertyPrefix = "dw." //sysprop & env prefix
  for ((prefName, value) in System.getenv()) {
    if (prefName.startsWith(propertyPrefix) && System.getProperty(prefName) == null) {
      System.setProperty(prefName, value)
    }
  }
  return YamlConfigurationFactory<EtlConfiguration>(
          EtlConfiguration::class.java,
          BaseValidator.newValidator(),
          Jackson.newObjectMapper(),
          propertyPrefix)
          .build(configProvider, configPath)
}

fun buildStreams(etlConfig: EtlConfiguration): KafkaStreams {
  etlConfig.kafkaStreamsConfig["key.serde"] = Serdes.LongSerde::class.java
  etlConfig.kafkaStreamsConfig["value.serde"] = JsonSerde::class.java
  val streamsConfig = StreamsConfig(etlConfig.kafkaStreamsConfig)

  val enrichers: MutableList<ValueMapper<ObjectNode,ObjectNode>> = mutableListOf()
  enrichers.add( SentimentEnricher(etlConfig) )
  for (col in etlConfig.geoAdminCollections) {
    enrichers.add(GeoAdminEnricher(etlConfig, col))
  }

  return buildStreams(streamsConfig, etlConfig.kafkaSourceTopic!!, etlConfig.kafkaDestTopic!!,
          enrichers)
}

fun buildStreams(streamsConfig: StreamsConfig, sourceTopic: String, destTopic: String,
                 valueMappers: List<ValueMapper<ObjectNode,ObjectNode>>): KafkaStreams {
  val builder = KStreamBuilder()

  @Suppress("UNCHECKED_CAST")
  val keySerde: Serde<Long> = streamsConfig.keySerde() as Serde<Long>
  @Suppress("UNCHECKED_CAST")
  val valueSerde: Serde<ObjectNode> = streamsConfig.valueSerde() as Serde<ObjectNode>

  builder.stream<Long, ObjectNode>(keySerde, valueSerde, sourceTopic).let {
    // TODO write a parallel ValueMapper? or can Kafka Streams do this already?
    var res = it
    for (valueMapper in valueMappers) {
      res = res.mapValues(valueMapper)
    }
    res
  }.to(keySerde, valueSerde, destTopic)

  return KafkaStreams(builder, streamsConfig)
}

val SENTIMENT_KEY = "hcga_sentiment"

class SentimentEnricher(etlConfig: EtlConfiguration) : ValueMapper<ObjectNode,ObjectNode> {
  val sentimentAnalyzer = SentimentAnalyzer(etlConfig.sentimentServer!!)
  val shouldRecompute = etlConfig.sentimentRecompute

  init {
    CLOSE_HOOKS.add {sentimentAnalyzer.close()}
  }

  override fun apply(tweet: ObjectNode): ObjectNode {
    if (shouldRecompute || ! tweet.has(SENTIMENT_KEY)) {
      log.trace("Sentiment enriching: processing {}", tweet)
      val tweetText = tweet.get("text").textValue()
      val sentimentSymbol = sentimentAnalyzer.calcSentiment(tweetText).toString() // assume right format
      tweet.put(SENTIMENT_KEY, sentimentSymbol)
    } else {
      log.trace("Sentiment enriching: skipping {}", tweet)
    }
    return tweet
  }
}

class GeoAdminEnricher(etlConfig: EtlConfiguration,
                                coll: String) : ValueMapper<ObjectNode,ObjectNode> {
  val shouldRecompute = etlConfig.geoAdminRecompute
  val solrClient = etlConfig.newSolrClient(etlConfig.geoAdminSolrConnectionString +coll)
  val jsonKey = "hcga_geoadmin_" + coll.toLowerCase()
  init {
    CLOSE_HOOKS.add {solrClient.close()}
  }

  override fun apply(tweet: ObjectNode): ObjectNode {
    if (shouldRecompute || ! tweet.has(jsonKey)) {
      log.trace("Geo Admin {} enriching: processing {}", jsonKey, tweet)
      val coordLonLatArray = tweet.get("coordinates")?.get("coordinates") as ArrayNode?
              ?: throw RuntimeException("Expected coordinates/coordinates in this data")
      val lon = coordLonLatArray.get(0).asDouble()!!
      val lat = coordLonLatArray.get(1).asDouble()!!

      val params = ModifiableSolrParams()
      params.set("qt", "/hcga_enrich") // sets 'fl'
      params.set("q", "{!cache=false field f=WKT}Intersects(POINT($lon $lat))")
      params.set("rows", 50) // if we reach 50, that'd be very unexpected
      //TODO sort somehow?  put that in request handler

      // FYI see SOLR-5969 distributed tracing.
      params.set("tweetId", tweet.get("id_str").asText()!!) // for tracing/debugging

      // do it!
      val jsonResultArray = tweet.putArray(jsonKey)
      solrClient.queryAndStreamResponse(params, object: StreamingResponseCallback() {
        override fun streamDocListInfo(numFound: Long, start: Long, maxScore: Float?) {
          if (numFound > 50) {
            log.warn("Found high responses: $numFound and we likely dropped some.")
          }
        }

        override fun streamSolrDocument(doc: SolrDocument) {
          // add object holding the field=value of the doc (assume string value)
          val obj = jsonResultArray.addObject()
          for ((f,v) in doc) {
            obj.put(f, v as String)
          }
        }
      })

    } else {
      log.trace("Sentiment enriching: skipping {}", tweet)
    }
    return tweet
  }
}