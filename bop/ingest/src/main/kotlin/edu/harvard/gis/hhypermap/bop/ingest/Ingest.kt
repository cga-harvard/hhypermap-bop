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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import edu.harvard.gis.hhypermap.bop.kafkastreamsbase.DwStreamApplication
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.solr.common.SolrInputDocument
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*


class Ingest(mainArgs: Array<String>) :
        DwStreamApplication<IngestDwConfiguration>(mainArgs, IngestDwConfiguration::class.java) {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) { Ingest(args).run() }
  }

  override fun buildStreams(streamsConfig: StreamsConfig,
                            keySerde: Serde<Long>, valueSerde: Serde<ObjectNode>): KafkaStreams {
    val solrClient = dwConfig.newSolrClient()
    addCloseHook(solrClient)

    val builder = KStreamBuilder()
    builder.stream<Long, ObjectNode>(keySerde, valueSerde, dwConfig.kafkaSourceTopic!!)
            .foreach { key, objectNode ->
              //TODO it seems in-flight messages here could get lost in the event of a crash.
              // So instead of ConcurrentUpdateSolrClient... we do batching here
              val doc = try {
                jsonToSolrInputDoc(objectNode)
              } catch (e: Exception) {
                log.error("Bad tweet format? $key $objectNode")
                throw e
              }
              solrClient.add(dwConfig.solrCollection, doc)
    }
    return KafkaStreams(builder, streamsConfig)
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