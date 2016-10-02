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

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.logging.DefaultLoggingFactory
import io.dropwizard.logging.LoggingFactory
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.hibernate.validator.constraints.NotEmpty
import java.nio.file.Paths
import javax.validation.constraints.NotNull

class EtlConfiguration {

  @JsonProperty
  @NotEmpty
  var kafkaSourceTopic: String? = null

  @JsonProperty
  @NotEmpty
  var kafkaDestTopic: String? = null

  @JsonProperty("kafkaStreams")
  @NotNull
  val kafkaStreamsConfig: MutableMap<String,Any> = mutableMapOf()

  @JsonProperty("logging")
  @NotNull
  val loggingConfig: LoggingFactory = DefaultLoggingFactory()

  @JsonProperty("sentiment.recompute")
  var sentimentRecompute = false

  @JsonProperty("sentiment.server")
  @NotEmpty
  var sentimentServer: String? = null

  @JsonProperty("geoAdmin.recompute")
  val geoAdminRecompute = false

  // see setup-Solr.sh where this is duplicated
  var geoAdminCollections = listOf("ADMIN2", "US_CENSUS_TRACT", "US_MA_CENSUS_BLOCK")

  @NotEmpty @JsonProperty("geoAdmin.solrConnectionString")
  var geoAdminSolrConnectionString: String? = null

  fun newSolrClient(solrConnectionString: String) : SolrClient {
    // note: we make the collection required.  Embedded always needs it.
    val match = Regex("""(\w+)://(.+)/([^/]+)""").matchEntire(solrConnectionString)
            ?: throw RuntimeException("solrConnectionString doesn't match pattern")
    val (type, where, coll) = match.destructured
    return when (type) {
      "http", "https" -> HttpSolrClient.Builder(solrConnectionString).build()
      "cloud" -> CloudSolrClient.Builder().withZkHost(where).build().apply { defaultCollection = coll }
      "embedded" -> EmbeddedSolrServer(Paths.get(where), coll)
      else -> throw RuntimeException("Unknown type: $type")
    }
  }


}
