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

import com.codahale.metrics.health.HealthCheck
import org.apache.solr.client.solrj.SolrClient

/**
 * Dropwizard HealthCheck
 */
class DwHealthCheck(val solrClient: SolrClient) : HealthCheck() {

  companion object {
    const val NAME : String = "SolrPing";
  }

  override fun check(): Result {
    val pingResponse = solrClient.ping()
    val msg = "Solr ping response: $pingResponse"
    if (pingResponse.status == 0) {
      return Result.healthy(msg)
    } else {
      return Result.unhealthy(msg)
    }
  }
}