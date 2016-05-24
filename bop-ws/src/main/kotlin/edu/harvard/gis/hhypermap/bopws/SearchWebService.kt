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

import com.codahale.metrics.annotation.Timed
import io.dropwizard.jersey.params.NonEmptyStringParam
import org.apache.solr.client.solrj.SolrClient

import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
class SearchWebService(val solrClient: SolrClient) {

  // todo extension function on NonEmptyStringParam

  @Path("/search")
  @GET
  @Timed
  fun search(@QueryParam("q.text") qText : NonEmptyStringParam): SearchResponse {
    return SearchResponse("hello world: query= $qText defCol=$solrClient")
  }
}
