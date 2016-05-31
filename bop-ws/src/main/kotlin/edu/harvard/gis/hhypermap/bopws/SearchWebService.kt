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
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrException
import org.apache.solr.common.util.NamedList
import org.locationtech.spatial4j.shape.Point
import org.locationtech.spatial4j.shape.Rectangle
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.Size
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

//  Solr fields:
private val ID_FIELD = "id"
private val TIME_FILTER_FIELD = "created_at"
private val TIME_SORT_FIELD = "id" // re-use 'id' which is a tweet id which is timestamp based
private val GEO_FILTER_FIELD = "coord_rpt"
private val GEO_HEATMAP_FIELD = "coord_rpt" // and assume units="degrees" here
private val GEO_SORT_FIELD = "coord" // and assume units="kilomters" here

private fun String.parseGeoBox() = parseGeoBox(this)

@Path("/tweets")
@Produces(MediaType.APPLICATION_JSON)
class SearchWebService(
        val solrClient: SolrClient) {

  @Path("/search")
  @GET
  @Timed
  fun search(
          @QueryParam("q.text") @Size(min = 1) qText: String?,
          @QueryParam("q.time") @Size(min = 1) qTime: String?,
          @QueryParam("q.geo")  @Size(min = 1) qGeo: String?,
          // TODO more, q.geoPath q.lang, ...

          @QueryParam("d.docs.limit") @DefaultValue("0") @Min(0) @Max(1000) aDocsLimit: Int,
          @QueryParam("d.docs.sort") @DefaultValue("score")                 aDocsSort: DocSortEnum,

          @QueryParam("a.time.limit") @DefaultValue("0") @Min(0) @Max(10000)  aTimeLimit: Int,
          @QueryParam("a.time.gap") @Size(min = 1)                            aTimeGap: String?,
          @QueryParam("a.time.filter") @Size(min = 1)                         aTimeFilter: String?,

          @QueryParam("a.hm.limit") @DefaultValue("0") @Min(0) @Max(10000)  aHmLimit: Int,
          @QueryParam("a.hm.gridLevel") @Min(1)                             aHmGridLevel: Int?,
          @QueryParam("a.hm.filter") @Size(min = 1)                         aHmFilter: String?

          ): SearchResponse {
    // note: The DropWizard *Param classes have questionable value with Kotlin given null types so
    //  we don't use them

    val solrQuery = SolrQuery()
    solrQuery.requestHandler = "/select/bop/search"

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
      requestSort(aDocsSort, qGeoRect?.center, qText != null, solrQuery)
    }

    // a.time
    if (aTimeLimit > 0) {
      requestDateFacets(aTimeLimit, aTimeFilter ?: qTime, aTimeGap, solrQuery)
    }

    // a.hm
    if (aHmLimit > 0) {
      requestHeatmap(aHmLimit, aHmFilter ?: qGeo, aHmGridLevel, solrQuery)
    }

    // -- EXECUTE

    // We can route this request to where the realtime shard is for stability/consistency and
    //  since that machine is different than the others
    //solrQuery.set("_route_", "realtime") TODO

    val solrResp: QueryResponse
    try {
      solrResp = solrClient.query(solrQuery);
    } catch(e: SolrException) {
      throw WebApplicationException(e.message, e.code()) // retain http code
    }

    return SearchResponse(
            aMatchDocs = solrResp.results.numFound,
            // if didn't ask for docs, we return no list at all
            dDocs = if (solrQuery.rows > 0) solrResp.results.map { docToMap(it) } else null,
            aTime = SearchResponse.DateFacet.fromSolr(solrResp),
            aHm = SearchResponse.Heatmap.fromSolr(solrResp)
    )
  }

  private fun requestSort(aDocsSort: DocSortEnum, distPoint: Point?, hasQuery: Boolean, solrQuery: SolrQuery) {
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

  private fun requestDateFacets(aTimeLimit: Int, aTimeFilter: String?, aTimeGap: String?, solrQuery: SolrQuery) {
    val (startStr, endStr) = parseSolrRangeAsPair(aTimeFilter ?: "[* TO *]")
    val startInst = parseDateTime(startStr)
    val endInst = parseDateTime(endStr)

    // TODO auto calculate gap from aTimeLimit
    val gapStr = aTimeGap ?: "+1DAY"

    val now = Instant.now()
    //TODO round start & end str's to the gap unit but only when it's non '*'
    solrQuery.addDateRangeFacet(TIME_FILTER_FIELD,
            Date.from(startInst ?: now.minus(90, ChronoUnit.DAYS)),
            Date.from(endInst ?: now),
            gapStr)
  }

  private fun parseDateTime(str: String): Instant? {
    return when {
      str == "*" -> null // open-ended
      str.last() == 'Z' -> Instant.parse(str) // "2016-05-15T00:00:00Z"
      else -> LocalDate.parse(str).atStartOfDay(ZoneOffset.UTC).toInstant() // "2016-05-15"
    }
  }

  private fun requestHeatmap(aHmLimit: Int, aHmFilter: String?, aHmGridLevel: Int?, solrQuery: SolrQuery) {
    solrQuery.setFacet(true)
    solrQuery.set("facet.heatmap", GEO_HEATMAP_FIELD)
    val hmRectStr = aHmFilter ?: "[-90,-180 TO 90,180]"
    solrQuery.set("facet.heatmap.geom", hmRectStr);
    if (aHmGridLevel != null) {
      // note: aHmLimit is ignored in this case
      solrQuery.set("facet.heatmap.gridLevel", aHmGridLevel)
    } else {
      // Calculate distErr that will approximate aHmLimit many cells as an upper bound
      val hmRect: Rectangle = hmRectStr.parseGeoBox()
      val degreesSideLen = (hmRect.width + hmRect.height) / 2.0 // side len of square (in degrees units)
      val cellsSideLen = Math.sqrt(aHmLimit.toDouble()) // side len of square (in target cell units)
      val cellSideLenInDegrees = degreesSideLen / cellsSideLen * 2
      // Note: the '* 2' is complicated.  Basically distErr is a maximum error (actual error may
      //   be smaller). This has the effect of choosing the minimum number of cells for a target
      //   resolution.  So *2 assumes quad tree (double side length to next level)
      //   and will tend to choose a more coarse level.
      // Note: assume units="degrees" on this field type
      solrQuery.set("facet.heatmap.distErr", cellSideLenInDegrees.toFloat().toString())
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
          @get:JsonProperty("a.matchDocs") val aMatchDocs: Long,
          @get:JsonProperty("d.docs") val dDocs: List<Map<String,Any>>?,
          @get:JsonProperty("a.time") val aTime: DateFacet?,
          @get:JsonProperty("a.hm") val aHm: Heatmap?
          //...
  ) {

    data class FacetValue(val value: String, val count: Long)

    data class DateFacet( // TODO fix Date type
            val start: String,//Date
            val end: String,//Date
            val gap: String,
            val counts: List<FacetValue>
    ) {
      companion object {
        fun fromSolr(solrResp: QueryResponse): DateFacet? {
          val rng = solrResp.facetRanges?.firstOrNull { it.name == TIME_FILTER_FIELD } ?: return null
          return DateFacet(
                  start = (rng.start as Date).toInstant().toString(),
                  end = (rng.end as Date).toInstant().toString(),
                  gap = rng.gap as String,
                  counts = rng.counts.map { FacetValue(it.value, it.count.toLong()) }
          )
        }
      }
    }

    data class Heatmap(
            val gridLevel: Int,
            val rows: Int,
            val columns: Int,
            val minX: Double, val maxX: Double, val minY: Double, val maxY: Double,
            //TODO api document implication of nulls in this grid
            val counts_ints2D: List<List<Int>>?,
            val projection: String
    ) {
        companion object {
          @Suppress("UNCHECKED_CAST")
          fun fromSolr(solrResp: QueryResponse): Heatmap? {
            val hmNl = solrResp.response
                    .findRecursive("facet_counts", "facet_heatmaps", GEO_HEATMAP_FIELD) as NamedList<Any>?
                    ?: return null
            // TODO consider doing this via a reflection utility; must it be Kotlin specific?
            return Heatmap(gridLevel = hmNl.get("gridLevel") as Int,
                    rows = hmNl.get("rows") as Int,
                    columns = hmNl.get("columns") as Int,
                    minX = hmNl.get("minX") as Double,
                    maxX = hmNl.get("maxX") as Double,
                    minY = hmNl.get("minY") as Double,
                    maxY = hmNl.get("maxY") as Double,
                    counts_ints2D = hmNl.get("counts_ints2D") as List<List<Int>>?,
                    projection = "EPSG:4326") // wgs84.  TODO switch to web mercator?
          }
        }
    }
  }

  enum class DocSortEnum {score, time, distance}


}

