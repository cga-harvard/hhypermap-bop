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

package edu.harvard.gis.hhypermap.bop.enrich

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import edu.harvard.gis.hhypermap.bop.kafkastreamsbase.StreamBase
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.kstream.ValueMapper
import org.apache.lucene.document.StoredField
import org.apache.solr.client.solrj.StreamingResponseCallback
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.params.ModifiableSolrParams
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

open class Enrich(mainArgs: Array<String>) :
        StreamBase<EnrichDwConfiguration>(mainArgs, EnrichDwConfiguration::class.java) {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) { Enrich(args).run() }
  }

  override fun buildStreams(streamsConfig: StreamsConfig, keySerde: Serde<Long>, valueSerde: Serde<ObjectNode>): KafkaStreams {

    val valueMappers: MutableList<ValueMapper<ObjectNode, ObjectNode>> = mutableListOf()
    dwConfig.sentimentServer?.let {
      val enricher = SentimentEnricher(dwConfig, it)
      addCloseHook(enricher)
      valueMappers.add(enricher)
    }
    for (col in dwConfig.geoAdminCollections) {
      val enricher = GeoAdminEnricher(dwConfig, col)
      addCloseHook(enricher)
      valueMappers.add(enricher)
    }
    log.info("Enrichers: $valueMappers")


    val builder = KStreamBuilder()
    builder.stream<Long, ObjectNode>(keySerde, valueSerde, dwConfig.kafkaSourceTopic!!).let {
      // TODO write a parallel ValueMapper? or can Kafka Streams do this already?
      var res = it
      for (valueMapper in valueMappers) {
        res = res.mapValues(valueMapper)
      }
      res
    }.to(keySerde, valueSerde, dwConfig.kafkaDestTopic!!)

    return KafkaStreams(builder, streamsConfig)
  }

  class SentimentEnricher(etlConfig: EnrichDwConfiguration, sentServer: String)
      : ValueMapper<ObjectNode, ObjectNode>, AutoCloseable {
    val log = LoggerFactory.getLogger(this.javaClass)
    val sentimentAnalyzer = SentimentAnalyzer(sentServer)
    val shouldRecompute = etlConfig.sentimentRecompute
    val SENTIMENT_KEY = "hcga_sentiment"

    override fun close() {
      sentimentAnalyzer.close()
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

  class GeoAdminEnricher(etlConfig: EnrichDwConfiguration, coll: String)
      : ValueMapper<ObjectNode, ObjectNode>, AutoCloseable {
    val log = LoggerFactory.getLogger(this.javaClass)
    val solrTimer = StreamBase.METRIC_REGISTRY.timer("enrich.geoadmin.$coll.solr")!!
    val shouldRecompute = etlConfig.geoAdminRecompute
    val solrClient = etlConfig.newSolrClient(etlConfig.geoAdminSolrConnectionString +coll)
    val jsonKey = "hcga_geoadmin_" + coll.toLowerCase()

    override fun close() {
      solrClient.close()
    }

    override fun apply(tweet: ObjectNode): ObjectNode {
      if (shouldRecompute || ! tweet.has(jsonKey)) {
        log.trace("Geo Admin {} enriching: processing {}", jsonKey, tweet)
        val coordLonLatArray = tweet.get("coordinates")?.get("coordinates") as ArrayNode?
                ?: throw RuntimeException("Expected coordinates/coordinates in this data")
        val lon = coordLonLatArray.get(0).asDouble()
        val lat = coordLonLatArray.get(1).asDouble()

        val params = ModifiableSolrParams()
        params.set("qt", "/hcga_enrich") // sets 'fl'
        params.set("q", "{!field cache=false f=WKT}Intersects(POINT($lon $lat))") // x y order for WKT
        params.set("rows", 50) // if we reach 50, that'd be very unexpected
        //TODO sort somehow?  put that in request handler

        // FYI see SOLR-5969 distributed tracing.
        params.set("tweetId", tweet.get("id_str").asText()!!) // for tracing/debugging

        // do it!
        val jsonResultArray = tweet.putArray(jsonKey)
        val queryResponse = solrClient.queryAndStreamResponse(params, object : StreamingResponseCallback() {
          override fun streamDocListInfo(numFound: Long, start: Long, maxScore: Float?) {
            if (numFound > 50) {
              log.warn("Found high responses: $numFound and we likely dropped some.")
            }
          }

          override fun streamSolrDocument(doc: SolrDocument) {
            // add object holding the field=value of the doc (assume string value)
            val obj = jsonResultArray.addObject()
            for ((f, v) in doc) {
              val strValue: String
              if (v is StoredField) { // EmbeddedSolrServer
                strValue = v.stringValue()
              } else if (v is Number || v is String) {
                strValue = v.toString()
              } else {
                throw RuntimeException("Unexpected type, field=$f class=${v.javaClass} val=$v")
              }
              obj.put(f,strValue)
            }
          }
        })

        solrTimer.update(queryResponse.elapsedTime, TimeUnit.MILLISECONDS)

      } else {
        log.trace("Sentiment enriching: skipping {}", tweet)
      }
      return tweet
    }
  }

}
