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
import edu.harvard.gis.hhypermap.bop.kafkastreamsbase.DwKafkaStreamsApplication
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.kstream.ValueMapper
import org.apache.kafka.streams.processor.StreamPartitioner
import org.apache.lucene.document.StoredField
import org.apache.solr.client.solrj.StreamingResponseCallback
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.params.ModifiableSolrParams
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

open class Enrich(mainArgs: Array<String>) :
        DwKafkaStreamsApplication<EnrichDwConfiguration>(mainArgs, EnrichDwConfiguration::class.java) {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) { Enrich(args).run() }
  }

  // called by the constructor
  override fun buildStreams(streamsConfig: StreamsConfig, keySerde: Serde<Long>, valueSerde: Serde<ObjectNode>): KafkaStreams {

    val valueMappers: MutableList<ValueMapper<ObjectNode, ObjectNode>> = mutableListOf()
    dwConfig.sentimentServer?.let {
      val enricher = SentimentEnricher(dwConfig, it, streamsConfig.getInt(StreamsConfig.NUM_STREAM_THREADS_CONFIG) ?: 1)
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

    // we let sourceTopic be a comma delimited list
    val sourceTopics = dwConfig.kafkaSourceTopic!!.split(',').toTypedArray()
    builder.stream<Long, ObjectNode>(keySerde, valueSerde, *sourceTopics).let {
      // TODO write a parallel ValueMapper? or can Kafka Streams do this already?
      var res = it
      for (valueMapper in valueMappers) {
        res = res.mapValues(valueMapper)
      }
      // set the "key" to be the tweet ID
      res.selectKey { curKey, objectNode -> objectNode["id_str"].asLong() }
      // notice custom partitioner:
    }.to(keySerde, valueSerde, partitioner(), dwConfig.kafkaDestTopic!!)

    return KafkaStreams(builder, streamsConfig)
  }

  fun partitioner() = StreamPartitioner<Long?, ObjectNode> { k, objectNode, numPartitions ->
    // Partition by month, assuming a particular epoch month.
    // partition 0 is special for bad data; we can't put into an ideal partition
    val epoch = 2012 * 12 + 10 // October 2012
    try {
      val dtTime = Instant.ofEpochMilli(objectNode["timestamp_ms"].asLong()).atOffset(ZoneOffset.UTC)!!
      // note: dtTime.month.value has january at 1.
      val partition: Int = dtTime.year * 12 + dtTime.month.value - epoch + 1 // first partition month is 1
      if (partition >= numPartitions) {
        log.debug("Not enough partitions; have {}, need {}", numPartitions, partition + 1)
        0
      } else if (partition < 1) {
        log.debug("Old tweet: {}; will put in partition 1", dtTime)
        1
      } else {
        partition
      }
    } catch (e: Exception) {
      log.debug(e.toString(), e) // 'warn' might be too noisy?
      0
    }
  }

  class SentimentEnricher(etlConfig: EnrichDwConfiguration, sentServer: String, threads: Int)
      : ValueMapper<ObjectNode, ObjectNode>, AutoCloseable {
    val log = LoggerFactory.getLogger(this.javaClass)
    val shouldRecompute = etlConfig.sentimentRecompute
    val SENTIMENT_KEY = "hcga_sentiment"

    // We create an array of SentimentAnalyzer, one for each thread, pre-initialized.
    val analyzers = ArrayBlockingQueue<SentimentAnalyzer>(threads)
    init {
      log.info("Creating $threads SentimentAnalyzer clients")
      val executor = Executors.newFixedThreadPool(threads)
      try {
        for (i in 1..threads) {
          executor.submit {
            try {
              analyzers.add(SentimentAnalyzer(sentServer))
            } catch (e : Exception) {
              log.error(e.toString(), e)
              executor.shutdownNow()
            }
          }
        }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.MINUTES)
      } finally {
        executor.shutdownNow()
        if (analyzers.size != threads) {
          close()
        }
      }
      check(!analyzers.isEmpty())
    }

    override fun close() {
      for (analyzer in analyzers) {
        try {
          analyzer.close()
        } catch(e: Exception) {
          log.error(e.toString(), e)
        }
      }
    }

    override fun apply(tweet: ObjectNode): ObjectNode {
      if (shouldRecompute || ! tweet.has(SENTIMENT_KEY)) {
        log.trace("Sentiment enriching: processing {}", tweet)
        val tweetText = tweet.get("text").textValue()

        val sentimentSymbol: String
        val analyzer = analyzers.poll(20, TimeUnit.SECONDS)
                ?: throw TimeoutException("analyzers timed out. Size: " + analyzers.size)
        try {
          sentimentSymbol = analyzer.calcSentiment(tweetText).toString() // assume right format
          analyzers.add(analyzer) // only put back into queue if succeeded
        } catch (e : Exception) {
          analyzer.close()
          throw e
        }
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
    val solrTimer = METRIC_REGISTRY.timer("enrich.geoadmin.$coll.solr")!!
    val shouldRecompute = etlConfig.geoAdminRecompute
    val solrClient = etlConfig.newSolrClient(etlConfig.geoAdminSolrConnectionString +coll)
    val jsonKey = "hcga_geoadmin_" + coll.toLowerCase()

    init {
      solrClient.ping()
    }

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
        try {
          val queryResponse = solrClient.queryAndStreamResponse(params, object : StreamingResponseCallback() {
            lateinit var jsonResultArray: ArrayNode
            override fun streamDocListInfo(numFound: Long, start: Long, maxScore: Float?) {
              jsonResultArray = tweet.putArray(jsonKey)
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
                obj.put(f, strValue)
              }
            }
          })

          solrTimer.update(queryResponse.elapsedTime, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {//SolrServerException when embedding; possible different otherwise?
          // This is a known issue; don't halt processing:
          if (e.message!!.contains("InvalidShapeException: Ring Self-intersection")) {
            log.warn("$jsonKey Couldn't process tweet with coord $coordLonLatArray because: $e", e)
          } else {
            throw e
          }
        }

      } else {
        log.trace("Sentiment enriching: skipping {}", tweet)
      }
      return tweet
    }
  }

}
