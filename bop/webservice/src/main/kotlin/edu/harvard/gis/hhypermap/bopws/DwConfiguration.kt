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

package edu.harvard.gis.hhypermap.bopws

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.Configuration
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.hibernate.validator.constraints.NotEmpty

/**
 * Dropwizard Configuration
 */
class DwConfiguration : Configuration() {

  @JsonProperty // JSON
  val indent: Boolean = false

  @JsonProperty("swagger")
  val swaggerBundleConfiguration: SwaggerBundleConfiguration = SwaggerBundleConfiguration().apply {
    resourcePackage = SearchWebService::class.java.`package`.name
  }

  @JsonProperty // CORS: see Jetty CrossOriginFilter servlet params
  var cors: Map<String, String>? = hashMapOf(
          "allowedOrigins" to "*",
          "allowedHeaders" to "*",
          "allowedMethods" to "OPTIONS,GET,HEAD" // all we need
  )

  @JsonProperty
  var jersey: Map<String, String>? = null

  //TODO could use a factory approach; see Configuration.DefaultServerFactory

  @JsonProperty
  var solrZkHost: String? = null // single zk host:port

  /** For experimental purposes. Mutually exclusive with solrZkHost. */
  @JsonProperty
  var solrUrl: String? = null

  @NotEmpty @JsonProperty
  var solrCollection: String? = null

  fun newSolrClient() : SolrClient {
    if (solrZkHost != null) {
      if (solrUrl != null) {
        throw Exception("solrUrl is mutually exclusive with solrZkHost")
      }
      return CloudSolrClient.Builder().withZkHost(solrZkHost).build().apply {
        defaultCollection = solrCollection
      }
    } else {
      var url = solrUrl ?: "http://localhost:8983/solr"
      if (solrCollection != null)
        url += "/$solrCollection"
      return HttpSolrClient.Builder(url).build()
    }
  }

}