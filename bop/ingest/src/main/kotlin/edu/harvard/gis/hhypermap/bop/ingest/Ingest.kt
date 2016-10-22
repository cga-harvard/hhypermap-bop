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

package edu.harvard.gis.hhypermap.bop.ingest

import com.fasterxml.jackson.databind.node.ObjectNode
import edu.harvard.gis.hhypermap.bop.kafkastreamsbase.DwApplication
import edu.harvard.gis.hhypermap.bop.kafkastreamsbase.JsonSerde
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.LongDeserializer
import org.apache.solr.common.SolrInputDocument
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit

/** Consume records from Kafka and send to Solr  */
class Ingest(mainArgs: Array<String>) :
        DwApplication<IngestDwConfiguration>(mainArgs, IngestDwConfiguration::class.java) {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) = Ingest(args).run()
  }

  fun run() {
    val solrClient = dwConfig.newSolrClient()
    addCloseHook(solrClient)

    // We will commit ourselves since we know when Solr has the docs; Kafka doesn't know
    dwConfig.kafkaConsumerConfig[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"

    val kafkaConsumer = KafkaConsumer(dwConfig.kafkaConsumerConfig,
            LongDeserializer(), JsonSerde().deserializer())
    addCloseHook(kafkaConsumer)


    kafkaConsumer.subscribe(Collections.singleton(dwConfig.kafkaSourceTopic))

    // this approach measures durations accurately; it's not to actually denote current time
    fun currentTimeMs() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())

    // note: we commit offsets to Kafka after a configured time threshold passes since the most
    //   recent non-committed record is returned from poll.
    var nextCommitTimeMs = -1L
    while (true) {
      // calc how long poll() can take
      val pollTimeoutMs = if (nextCommitTimeMs == -1L)
        Long.MAX_VALUE // forever
      else
        Math.max(0, nextCommitTimeMs - currentTimeMs()) // until next commit time
      val records = kafkaConsumer.poll(pollTimeoutMs)

      // calc next commit time if it's not set and we have records
      if (nextCommitTimeMs == -1L && records.isEmpty == false) {
        nextCommitTimeMs = currentTimeMs() + dwConfig.kafkaOffsetCommitIntervalMs
      }

      // convert records to Solr docs and send to Solr asynchronously
      for (record in records) { // usually one per Kafka partition/topic
        val jsonNode = record.value()
        try {
          solrClient.add(dwConfig.solrCollection, jsonToSolrInputDoc(jsonNode as ObjectNode))
        } catch(e: Exception) {
          log.error("Bad tweet format? $jsonNode")
          throw e
        }
      }

      // maybe commit offsets
      if (currentTimeMs() >= nextCommitTimeMs) {
        log.debug("Flushing queue to Solr then committing Kafka offsets")
        solrClient.blockUntilFinished() // unique method of ConcurrentUpdateSolrClient
        kafkaConsumer.commitSync()
        nextCommitTimeMs = -1L
      }
    }
  }
}

fun jsonToSolrInputDoc(objectNode: ObjectNode): SolrInputDocument {
  //  val createdAtDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
  /** ex: ["foo","bar","baz"] => ["/foo","/foo/bar","/foo/bar/baz"] */
  fun pathTokenizeForFaceting(parts: List<String>): List<String> {
    val buf = StringBuilder()
    return parts.map {
      buf.append('/').append(it)
      buf.toString()
    }
  }

  val doc = SolrInputDocument()

  doc.addField("id", objectNode["id_str"].asLong())

  // populate createdAt using either timestamp_ms or created_at
  val createdAtInst = objectNode["timestamp_ms"].asLong().let {timeLong ->
    Instant.ofEpochMilli(timeLong)
  }
  doc.addField("created_at", Date.from(createdAtInst))
  val user_utc_offset = objectNode["user"]["utc_offset"]?.asLong() // in secs
  if (user_utc_offset != null) {
    val localDt = createdAtInst.plusSeconds(user_utc_offset).atOffset(ZoneOffset.UTC)
    val duration = Duration.between(LocalTime.MIDNIGHT, localDt.toLocalTime())
    doc.addField("minuteOfDayByUserTimeZone", duration.toMinutes())
  }

  doc.addField("coord", objectNode["coordinates"]["coordinates"].let { lonLatArr ->
    "${lonLatArr[1].asDouble()},${lonLatArr[0].asDouble()}" // put in "lat,lon" order
  })

  doc.addField("text", objectNode["text"].textValue())

  doc.addField("user_name", objectNode["user"]["screen_name"].textValue())

  doc.addField("lang", objectNode["lang"].textValue())

  //HCGA Extensions:
  doc.addField("sentiment_pos", objectNode["hcga_sentiment"].textValue().let {
    when(it) {
      "pos" -> true
      "neg" -> false
      else -> throw RuntimeException("Bad sentiment: $it")
    }
  })
  //TODO explicit happy heatmap

  var prefix = "geoadmin_admin2"
  objectNode["hcga_$prefix"].let { arr ->
    doc.addField("${prefix}_count", arr.size())
    arr[0]?.let { obj ->
      obj["id"].textValue().split('-').let {parts ->
        val paths = pathTokenizeForFaceting(parts)
        doc.addField(prefix, paths.last())
        paths.forEachIndexed { i, path ->
          doc.addField("${prefix}_${i}_pathdv", path)
        }
      }
      obj["txt"].textValue().split('_').let {parts ->
        val paths = pathTokenizeForFaceting(parts)
        doc.addField("${prefix}_txt", paths.last())
        paths.forEachIndexed { i, path ->
          doc.addField("${prefix}_txt_${i}_pathdv", path)
        }
      }
    }
  }
  prefix = "geoadmin_us_census_tract"
  objectNode["hcga_$prefix"].let { arr ->
    doc.addField("${prefix}_count", arr.size())
    arr[0]?.let { obj ->
      doc.addField(prefix, obj["tract"].textValue())
    }
  }
  prefix = "geoadmin_us_ma_census_block"
  objectNode["hcga_$prefix"].let { arr ->
    doc.addField("${prefix}_count", arr.size())
    arr[0]?.let { obj ->
      doc.addField(prefix, obj["block"].textValue())
      doc.addField("${prefix}_townName", obj["townName"].textValue())
    }
  }

  return doc
}