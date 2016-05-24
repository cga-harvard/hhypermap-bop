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
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.hibernate.validator.constraints.NotEmpty

/**
 * Dropwizard Configuration
 */
class DwConfiguration : Configuration() {

  //TODO could use a factory approach; see Configuration.DefaultServerFactory

  @NotEmpty @JsonProperty
  var solrZkHost: String? = null

  @NotEmpty @JsonProperty
  var solrCollection: String? = null

  fun newSolrClient() : SolrClient {
    return CloudSolrClient(solrZkHost).apply {
      defaultCollection = solrCollection
    }
  }

}