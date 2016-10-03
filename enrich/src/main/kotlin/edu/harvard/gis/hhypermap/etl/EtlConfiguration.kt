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
import org.apache.solr.core.CoreContainer
import org.apache.solr.core.NodeConfig
import org.apache.solr.core.SolrResourceLoader
import org.hibernate.validator.constraints.NotEmpty
import java.nio.file.Paths
import java.util.*
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern

class EtlConfiguration {

  @JsonProperty
  @NotEmpty
  var kafkaSourceTopic: String? = null

  @JsonProperty
  @NotEmpty
  var kafkaDestTopic: String? = null

  private var _kafkaStreamsConfig: MutableMap<String,Any> = mutableMapOf()
  val kafkaStreamsConfig: MutableMap<String,Any>
    @JsonProperty("kafkaStreams")
    @NotNull
    get() {
      // rewrite hyphens to periods. We do this so we can use sys & env prop overrides without
      // DropWizard interpreting the '.' as a sub-object

      // It's bad practice to update in a getter... but not sure what's better
      _kafkaStreamsConfig = _kafkaStreamsConfig.mapKeysTo(
              LinkedHashMap(_kafkaStreamsConfig.size),
              { it.key.replace('-', '.') } )
      return _kafkaStreamsConfig
    }

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

  var geoAdminCollections = listOf("ADMIN2", "US_CENSUS_TRACT", "US_MA_CENSUS_BLOCK")

  @NotEmpty @JsonProperty("geoAdmin.solrConnectionString")
  @Pattern(regexp = "(http|https|cloud|embedded)://(.+)/")
  var geoAdminSolrConnectionString: String? = null

  fun newSolrClient(solrConnectionString: String) : SolrClient {
    // note: we make the collection (at the end) required.  Embedded requires it.
    val match = Regex("""(\w+)://(.+)/([^/]+)""").matchEntire(solrConnectionString)
            ?: throw RuntimeException("solrConnectionString doesn't match pattern")
    val (type, where, coll) = match.destructured
    return when (type) {
      "http", "https" -> HttpSolrClient.Builder(solrConnectionString).build()
      "cloud" -> CloudSolrClient.Builder().withZkHost(where).build().apply { defaultCollection = coll }
      "embedded" -> {
        // Instead of simply invoking EmbeddedSolrServer's convenience constructor, we want to avoid
        //   Solr loading all the cores here -- we just want one.  Arguably ESS should do this.
        // ignore solr.xml & /lib -- just grab this one core
        val corePath = Paths.get(where, coll) // assume coll == dir name
        val resourceLoader = SolrResourceLoader(corePath)
        val config: NodeConfig = NodeConfig.NodeConfigBuilder(null, resourceLoader).build()
        val cc = CoreContainer(config)
        cc.load()
        EmbeddedSolrServer(cc, coll)
      }
      else -> throw RuntimeException("Unknown type: $type")
    }
  }



}
