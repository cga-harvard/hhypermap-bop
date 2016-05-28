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
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrException
import java.math.BigInteger
import java.util.*
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("/tweets")
@Produces(MediaType.APPLICATION_JSON)
class SearchWebService(
        val solrClient: SolrClient) {

  val ID_FIELD = "id"
  val TIME_FILTER_FIELD = "created_at"
  val TIME_SORT_FIELD = "id" // re-use 'id' which is a tweet id which is timestamp based
  val GEO_FILTER_FIELD = "coord_rpt"
  val GEO_SORT_FIELD = "coord"

  @Path("/search")
  @GET
  @Timed
  fun search(
          @QueryParam("q.text") qText: NonEmptyStringParam,
          @QueryParam("q.time") qTime: NonEmptyStringParam,
          @QueryParam("q.geo") qGeo: NonEmptyStringParam,
          // TODO more, q.geoPath q.lang, ...

          @QueryParam("d.docs.limit") @DefaultValue("0") @Min(0) @Max(1000) aDocsLimit: Int,
          @QueryParam("d.docs.sort") @DefaultValue("score") aDocsSort: DocSortEnum

      ): SearchResponse {

    val solrQuery = SolrQuery()
    solrQuery.requestHandler = "/select/bop/search"

    // -- Query Constraints 'q' and 'fq' (query and filter queries)
    // q.text:
    qText.get().ifPresent {
      solrQuery.query = it
      // TODO will wind up in 'fq'?  If not we should use fq if no relevance sort
    }

    // q.time
    qTime.get().ifPresent {
      parseSolrRangeAsPair(it)// will throw if fails to parse
      solrQuery.addFilterQuery("{!field f=$TIME_FILTER_FIELD}$it")
      // TODO determine which subset of shards to use ("shards" param)
      // TODO add caching header if we didn't need to contact the realtime shard? expire at 1am
    }

    // q.geo
    qGeo.get().ifPresent {
      parseGeoBox(it) // will throw if fails to parse.
      solrQuery.addFilterQuery("$GEO_FILTER_FIELD:$it")// lucene syntax
    }

    //TODO q.geoPath

    //TODO q.lang

    // -- Docs

    solrQuery.rows = aDocsLimit

    if (solrQuery.rows > 0) {
      setSort(aDocsSort, qGeo, qText, solrQuery)
    }

    // -- EXECUTE

    // We can route this request to where the realtime shard is for stability/consistency and
    //  since that machine is different than the others
    //solrQuery.set("_route_", "realtime") TODO

    val solrResp: QueryResponse?
    try {
      solrResp = solrClient.query(solrQuery);
    } catch(e: SolrException) {
      throw WebApplicationException(e.message, e.code()) // retain http code
    }

    return SearchResponse(
            aMatchDocs = solrResp.results.numFound,
            // if didn't ask for docs, we return no list at all
            dDocs = if (solrQuery.rows > 0) solrResp.results.map { docToMap(it) } else null
    )
  }

  private fun setSort(aDocsSort: DocSortEnum, qGeo: NonEmptyStringParam, qText: NonEmptyStringParam, solrQuery: SolrQuery) {
    val sort = if (aDocsSort == DocSortEnum.score && qText.get().isPresent == false) {//score requires query string
      DocSortEnum.time // fall back on time even if asked for score (should we error instead?)
    } else {
      aDocsSort
    }
    when (sort) {
      DocSortEnum.score -> solrQuery.addSort("score", SolrQuery.ORDER.desc)
        //TODO also sort by time after score?

      DocSortEnum.time -> solrQuery.addSort(TIME_SORT_FIELD, SolrQuery.ORDER.desc)

      DocSortEnum.distance -> {
        solrQuery.addSort("geodist()", SolrQuery.ORDER.asc)
        solrQuery.set("sfield", GEO_SORT_FIELD)
        if (qGeo.get().isPresent == false) {
          throw WebApplicationException("Can't sort by distance without q.geo", 400)
        }
        solrQuery.set("pt", toLatLon(parseGeoBox(qGeo.get().get()).center))
      }
    }
  }

  private fun docToMap(doc: SolrDocument): Map<String, Any> {
    val map = LinkedHashMap<String, Any>()
    for ((name, value) in doc) {
      if (name[0] == '_') { // e.g. _version_
        continue;
      }
      val newValue = when (name) {
        // convert id original twitter id
        ID_FIELD -> solrIdToTweetId(value)
        else -> value
      }
      map.put(name, newValue)
    }
    return map
  }

  // This is the reverse of what we do in a Solr URP on ingest (Solr Long is signed)
  private fun solrIdToTweetId(value: Any): String =
          (BigInteger.valueOf(value as Long) - BigInteger.valueOf(Long.MIN_VALUE)).toString()

  data class SearchResponse (
          @get:com.fasterxml.jackson.annotation.JsonProperty("a.matchDocs") val aMatchDocs : Long,
          @get:com.fasterxml.jackson.annotation.JsonProperty("d.docs") val dDocs : List<Map<String,Any>>?
          //...
  )

  enum class DocSortEnum {score, time, distance}


}

