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
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrException
import org.locationtech.spatial4j.shape.Point
import org.locationtech.spatial4j.shape.Rectangle
import java.math.BigInteger
import java.util.*
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.Size
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
          @QueryParam("q.text") @Size(min = 1) qText: String?,
          @QueryParam("q.time") @Size(min = 1) qTime: String?,
          @QueryParam("q.geo")  @Size(min = 1) qGeo: String?,
          // TODO more, q.geoPath q.lang, ...

          @QueryParam("d.docs.limit") @DefaultValue("0") @Min(0) @Max(1000) aDocsLimit: Int,
          @QueryParam("d.docs.sort") @DefaultValue("score")                 aDocsSort: DocSortEnum
          ): SearchResponse {
    // note: The DropWizard *Param classes have questionable value with Kotlin given null types so
    //  we don't use them

    val solrQuery = SolrQuery()
    solrQuery.requestHandler = "/select/bop/search"

    fun String.parseGeoBox() = parseGeoBox(this)

    // -- Query Constraints 'q' and 'fq' (query and filter queries)
    // q.text:
    if (qText != null) {
      solrQuery.query = qText
      // TODO will wind up in 'fq'?  If not we should use fq if no relevance sort
    }

    // q.time
    if (qTime != null) {
      parseSolrRangeAsPair(qTime)// will throw if fails to parse
      solrQuery.addFilterQuery("{!field f=$TIME_FILTER_FIELD}$qTime")
      // TODO determine which subset of shards to use ("shards" param)
      // TODO add caching header if we didn't need to contact the realtime shard? expire at 1am
    }

    // q.geo
    val qGeoRect: Rectangle?
    if (qGeo != null) {
      qGeoRect = qGeo.parseGeoBox()
      solrQuery.addFilterQuery("$GEO_FILTER_FIELD:$qGeo")// lucene query syntax
    } else {
      qGeoRect = null
    }

    //TODO q.geoPath

    //TODO q.lang

    // -- Docs
    solrQuery.rows = aDocsLimit

    if (solrQuery.rows > 0) {
      setSort(aDocsSort, qGeoRect?.center, qText != null, solrQuery)
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

  private fun setSort(aDocsSort: DocSortEnum, distPoint: Point?, hasQuery: Boolean, solrQuery: SolrQuery) {
    val sort = if (aDocsSort == DocSortEnum.score && hasQuery) {//score requires query string
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
        if (distPoint == null) {
          throw WebApplicationException("Can't sort by distance without q.geo", 400)
        }
        solrQuery.set("pt", toLatLon(distPoint.center))
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

