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
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty // IntelliJ wants to remove this but it's needed!
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.FacetField
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrException
import org.apache.solr.common.params.FacetParams
import org.apache.solr.common.util.NamedList
import org.locationtech.spatial4j.shape.Rectangle
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.Pattern
import javax.validation.constraints.Size // IntelliJ wants to remove this but it's needed!
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.StreamingOutput

//  Solr fields: (TODO make configurable?)
private val ID_FIELD = "id"
private val TIME_FILTER_FIELD = "created_at"
private val TIME_SORT_FIELD = "id" // re-use 'id' which is a tweet id which is timestamp based
private val GEO_FILTER_FIELD = "coord_rpt"
private val GEO_HEATMAP_FIELD = "coord_rpt" // and assume units="degrees" here
private val GEO_SORT_FIELD = "coord" // and assume units="kilometers" here
private val TEXT_FIELD = "text"
private val USER_FIELD = "user_name"

private fun String.parseGeoBox() = parseGeoBox(this)

@Api
@Path("/tweets") // TODO make path configurable == Collection
class SearchWebService(
        val solrClient: SolrClient) {

  // note: I'd prefer immutable; Jersey seems to handle it but Swagger is confused (NPE)
  data class ConstraintsParams(
    @set:QueryParam("q.text") @set:Size(min = 1)
    @set:ApiParam("Constrains docs by keyword search query.")
    var qText: String? = null,

    @set:QueryParam("q.user") @set:Size(min = 1)
    @set:ApiParam("Constrains docs by matching exactly a certain user")
    var qUser: String? = null,

    @set:QueryParam("q.time") @set:Pattern(regexp = """\[(\S+) TO (\S+)\]""")
    @set:ApiParam("Constrains docs by time range.  Either side can be '*' to signify" +
            " open-ended. Otherwise it must be in either format as given in the example." +
            " UTC time zone is implied.", // TODO add separate TZ param?
            example = "[2013-03-01 TO 2013-04-01T00:00:00]")
    var qTime: String? = null,

    @set:QueryParam("q.geo") @set:Pattern(regexp = """\[(\S+,\S+) TO (\S+,\S+)\]""")
    @set:ApiParam("A rectangular geospatial filter in decimal degrees going from the lower-left" +
            " to the upper-right.  The coordinates are in lat,lon format.",
            example = "[-90,-180 TO 90,180]")
    var qGeo: String? = null

    // TODO more, q.geoPath q.lang, ...
  ) {

    @get:JsonIgnore internal val qGeoRect: Rectangle? by lazy {
      qGeo?.parseGeoBox()
    }

    fun applyToSolrQuery(solrQuery: SolrQuery) {
      // q.text:
      if (qText != null) {
        solrQuery.query = qText
        // TODO will wind up in 'fq'?  If not we should use fq if no relevance sort
      }

      // q.user
      if (qUser != null) {
        solrQuery.addFilterQuery("{!field f=$USER_FIELD tag=$USER_FIELD}$qUser")
      }

      // q.time
      if (qTime != null) {
        val (leftInst, rightInst) = parseDateTimeRange(qTime) // parses multiple formats
        val leftStr = leftInst?.toString() ?: "*" // normalize to Solr
        val rightStr = rightInst?.toString() ?: "*" // normalize to Solr
        // note: tag to exclude in a.time
        solrQuery.addFilterQuery("{!field tag=$TIME_FILTER_FIELD f=$TIME_FILTER_FIELD}" +
                "[$leftStr TO $rightStr]")
        // TODO determine which subset of shards to use ("shards" param)
        // TODO add caching header if we didn't need to contact the realtime shard? expire at 1am
      }

      // q.geo
      if (qGeo != null) {
        qGeoRect // side effect of validating qGeo
        // note: can't use {!field} since it's the Lucene QParser that parses ranges
        // note: tag to exclude in a.hm
        solrQuery.addFilterQuery("{!lucene tag=$GEO_FILTER_FIELD df=$GEO_FILTER_FIELD}$qGeo")
      }

      //TODO q.geoPath

      //TODO q.lang
    }
  }

  @Path("/search")
  @ApiOperation(value = "Search/analytics endpoint; highly configurable. Not for bulk doc retrieval.",
          notes = """The q.* parameters qre query/constraints that limit the matching documents.
 The d.* params control returning the documents. The a.* params are faceting/aggregations on a
 field of the documents.  The *.limit params limit how many top values/docs to return.  Some of
 the formatting and response structure has strong similarities with Apache Solr, unsurprisingly.""")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Timed
  fun search(

          @BeanParam
          qConstraints: ConstraintsParams,

          @QueryParam("d.docs.limit") @DefaultValue("0") @Min(0) @Max(100)
          @ApiParam("How many documents to return in the search results.")
          aDocsLimit: Int,

          @QueryParam("d.docs.sort") @DefaultValue("score")
          @ApiParam("How to order the documents before returning the top X." +
                  " 'score' is keyword search relevancy. 'time' is time descending." +
                  " 'distance' is the distance between the doc and the middle of q.geo.")
          aDocsSort: DocSortEnum,

          @QueryParam("a.time.limit") @DefaultValue("0") @Min(0) @Max(1000)
          @ApiParam("Non-0 triggers time/date range faceting. This value is the" +
                  " maximum number of time ranges to return when a.time.gap is unspecified." +
                  " This is a soft maximum; less will usually be returned. A suggested value is" +
                  " 100." +
                  " Note that a.time.gap effectively ignores this value." +
                  " See Solr docs for more details on the query/response format." +
                  " The counts ignore the q.time filter if present.")
          aTimeLimit: Int,

          @QueryParam("a.time.gap") @Pattern(regexp = """P((\d+[YMWD])|(T\d+[HMS]))""")
          @ApiParam("The consecutive time interval/gap for each time range.  Ignores a.time.limit." +
                  "The format is based on a subset of the ISO-8601 duration format.", example = "P1D")
          aTimeGap: String?,

          @QueryParam("a.time.filter") @Pattern(regexp = """\[(\S+) TO (\S+)\]""")
          @ApiParam("From what time range to divide by a.time.gap into intervals.  Defaults to" +
                  " q.time and otherwise 90 days.")
          aTimeFilter: String?,

          @QueryParam("a.hm.limit") @DefaultValue("0") @Min(0) @Max(10000)
          @ApiParam("Non-0 triggers heatmap/grid faceting.  This number is a soft maximum on the" +
                  "number of cells it should have." +
                  " There may be as few as 1/4th this number in return.  Note that a.hm.gridLevel" +
                  " can effectively ignore this value." +
                  " The response heatmap contains a counts grid that can be null or contain null" +
                  " rows when all its values would be 0.  " +
                  " See Solr docs for more details on the response format." +
                  " The counts ignore the q.geo filter if present.")
          aHmLimit: Int,

          @QueryParam("a.hm.gridLevel") @Min(1) @Max(100)
          @ApiParam("To explicitly specify the grid level, e.g. to let a user ask for greater or" +
                  " courser resolution than the most recent request.  Ignores a.hm.limit.")
          aHmGridLevel: Int?,

          @QueryParam("a.hm.filter") @Pattern(regexp = """\[(\S+,\S+) TO (\S+,\S+)\]""")
          @ApiParam("From what region to plot the heatmap. Defaults to q.geo or otherwise the" +
                  " world.")
          aHmFilter: String?,

          @QueryParam("a.text.limit") @DefaultValue("0") @Min(0) @Max(1000)
          @ApiParam("Returns the most frequently occurring words.  WARNING: There is usually a" +
                  " significant performance hit in this due to the extremely high cardinality.")
          aTextLimit: Int,

          @QueryParam("a.user.limit") @DefaultValue("0") @Min(0) @Max(1000)
          @ApiParam("Returns the most frequently occurring users." +
                  " The counts ignore the q.user filter if present.")
          aUserLimit: Int

  ): SearchResponse {
    // note: The DropWizard *Param classes have questionable value with Kotlin given null types so
    //  we don't use them

    val solrQuery = SolrQuery()
    solrQuery.requestHandler = "/select/bop/search"

    qConstraints.applyToSolrQuery(solrQuery)

    // a.docs
    requestDocs(aDocsLimit, aDocsSort, qConstraints, solrQuery)

    // Aggregations/Facets

    // a.time
    if (aTimeLimit > 0) {
      requestTimeFacet(aTimeLimit, aTimeFilter ?: qConstraints.qTime, aTimeGap, solrQuery)
    }

    // a.hm
    if (aHmLimit > 0) {
      requestHeatmapFacet(aHmLimit, aHmFilter ?: qConstraints.qGeo, aHmGridLevel, solrQuery)
    }

    // a.text
    if (aTextLimit > 0) {
      requestFieldFacet(TEXT_FIELD, aTextLimit, solrQuery, exFilter = false)
    }

    // a.user
    if (aUserLimit > 0) {
      requestFieldFacet(USER_FIELD, aUserLimit, solrQuery)
    }

    // -- EXECUTE

    // We can route this request to where the realtime shard is for stability/consistency and
    //  since that machine is different than the others
    //solrQuery.set("_route_", "realtime") TODO
    solrQuery.add("debug", "timing")

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
            aTime = SearchResponse.TimeFacet.fromSolr(solrResp),
            aHm = SearchResponse.HeatmapFacet.fromSolr(solrResp),
            aText = SearchResponse.fieldValsFacetFromSolr(solrResp, TEXT_FIELD),
            aUser = SearchResponse.fieldValsFacetFromSolr(solrResp, USER_FIELD),
            timing = SearchResponse.getTimingFromSolr(solrResp)
    )
  }

  private fun requestDocs(aDocsLimit: Int, aDocsSort: DocSortEnum, qConstraints: ConstraintsParams, solrQuery: SolrQuery) {
    solrQuery.rows = aDocsLimit
    if (solrQuery.rows == 0) {
      return
    }

    // Set Sort:
    val sort = if (aDocsSort == DocSortEnum.score && qConstraints.qText == null) {//score requires query string
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
        val distPoint = qConstraints.qGeoRect?.center
                ?: throw WebApplicationException("Can't sort by distance without q.geo", 400)
        solrQuery.set("pt", toLatLon(distPoint.center))
      }
    }
  }

  private fun requestTimeFacet(aTimeLimit: Int, aTimeFilter: String?, aTimeGap: String?, solrQuery: SolrQuery) {
    val now = Instant.now()
    val (_startInst, _endInst) = parseDateTimeRange(aTimeFilter)
    val startInst = _startInst ?: now.minus(90, ChronoUnit.DAYS)
    val endInst = _endInst ?: now

    val rangeDuration = Duration.between(startInst, endInst)
    if (rangeDuration.isNegative) {
      throw WebApplicationException("date ordering problem: $aTimeFilter", 400)
    }

    val gap = when (aTimeGap) {
      null -> Gap.computeGap(rangeDuration, aTimeLimit)
      else -> Gap.parseISO8601(aTimeGap)
    }

    // verify the gap provided won't have too many bars
    if (rangeDuration.toMillis() / gap.toMillis() > 1000) {
      throw WebApplicationException("Gap $aTimeGap is too small for this range $aTimeFilter")
    }

    solrQuery.apply {
      set(FacetParams.FACET, true)
      add(FacetParams.FACET_RANGE, "{!ex=$TIME_FILTER_FIELD}$TIME_FILTER_FIELD") // exclude q.time
      add("f.$TIME_FILTER_FIELD.${FacetParams.FACET_RANGE_START}", startInst.toString())
      add("f.$TIME_FILTER_FIELD.${FacetParams.FACET_RANGE_END}", endInst.toString())
      add("f.$TIME_FILTER_FIELD.${FacetParams.FACET_RANGE_GAP}", gap.toSolr())
    }
  }

  private fun requestHeatmapFacet(aHmLimit: Int, aHmFilter: String?, aHmGridLevel: Int?, solrQuery: SolrQuery) {
    solrQuery.setFacet(true)
    solrQuery.set("facet.heatmap", "{!ex=$GEO_FILTER_FIELD}$GEO_HEATMAP_FIELD")
    val hmRectStr = aHmFilter ?: "[-90,-180 TO 90,180]"
    solrQuery.set("facet.heatmap.geom", hmRectStr)
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

  private fun requestFieldFacet(field: String, limit: Int, solrQuery: SolrQuery, exFilter: Boolean = true) {
    solrQuery.setFacet(true)
    solrQuery.add("facet.field", if (exFilter) "{! ex=$field}$field" else field)
    solrQuery.set("f.$field.facet.limit", limit)
    // we let params on the Solr side tune this further if desired
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
      if (newValue !is String && newValue !is Number) {
        throw Exception("field $name has unexpected value type ${newValue.javaClass}")
      }
      map.put(name, newValue)
    }
    return map
  }

  // This is the reverse of what we do in a Solr URP on ingest (Solr Long is signed)
  private fun solrIdToTweetId(value: Any): String =
          (BigInteger.valueOf(value as Long) - BigInteger.valueOf(Long.MIN_VALUE)).toString()

  @JsonPropertyOrder("a.matchDocs", "d.docs", "a.time", "a.hm", "a.user", "a.text", "timing")
  data class SearchResponse (
          @get:JsonProperty("a.matchDocs") val aMatchDocs: Long,
          @get:JsonProperty("d.docs") val dDocs: List<Map<String,Any>>?,
          @get:JsonProperty("a.time") val aTime: TimeFacet?,
          @get:JsonProperty("a.hm") val aHm: HeatmapFacet?,
          @get:JsonProperty("a.user") val aUser: List<FacetValue>?,
          @get:JsonProperty("a.text") val aText: List<FacetValue>?,
          @get:JsonProperty("timing") val timing: Timing
  ) {

    companion object {
      fun fieldValsFacetFromSolr(solrResp: QueryResponse, field: String): List<FacetValue>? {
        val facetField: FacetField = solrResp.getFacetField(field) ?: return null
        return facetField.values.map { FacetValue(it.name, it.count.toLong()) }
      }

      @Suppress("UNCHECKED_CAST")
      fun getTimingFromSolr(solrResp: QueryResponse): Timing {
        val tree = convertSolrTimingTree("QTime", solrResp.debugMap["timing"] as NamedList<Any>)
        if (tree != null && Math.abs(solrResp.qTime.toLong() - tree.millis) > 5) {
          log.warn("QTime != debug.timing.time: ${solrResp.qTime.toLong() - tree.millis}")
        }
        return Timing("callSolr.elapsed", solrResp.elapsedTime, listOfNotNull(tree));
      }

      @Suppress("UNCHECKED_CAST")
      private fun convertSolrTimingTree(label: String, namedList: NamedList<Any>): Timing? {
        val millis = (namedList.remove("time") as Double).toLong() // note we remove it
        if (millis == 0L) { // avoid verbosity; lots of 0's is typical
          return null;
        }
        val subs = namedList.map { convertSolrTimingTree(it.key, it.value as NamedList<Any>) }
        return Timing(label, millis, subs.filterNotNull())
      }
    }

    data class FacetValue(val value: String, val count: Long)

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class Timing(val label: String, val millis: Long, val subs: List<Timing> = emptyList())

    data class TimeFacet( // TODO document/fix Date type
            val start: String,//Date
            val end: String,//Date
            val gap: String,
            val counts: List<FacetValue>
    ) {
      companion object {
        fun fromSolr(solrResp: QueryResponse): TimeFacet? {
          val rng = solrResp.facetRanges?.firstOrNull { it.name == TIME_FILTER_FIELD } ?: return null
          return TimeFacet(
                  start = (rng.start as Date).toInstant().toString(),
                  end = (rng.end as Date).toInstant().toString(),
                  gap = Gap.parseSolr(rng.gap as String).toISO8601(),
                  counts = rng.counts.map { FacetValue(it.value, it.count.toLong()) }
          )
        }
      }
    }

    data class HeatmapFacet(
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
          fun fromSolr(solrResp: QueryResponse): HeatmapFacet? {
            val hmNl = solrResp.response
                    .findRecursive("facet_counts", "facet_heatmaps", GEO_HEATMAP_FIELD) as NamedList<Any>?
                    ?: return null
            // TODO consider doing this via a reflection utility; must it be Kotlin specific?
            return HeatmapFacet(gridLevel = hmNl.get("gridLevel") as Int,
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

  }// class SearchResponse

  enum class DocSortEnum {score, time, distance}

  @Path("/export")
  @ApiOperation(value = "Search export endpoint for bulk doc retrieval.",
                notes = """The q.* parameters qre query/constraints that limit the
                matching documents. Documents come back sorted by time descending.
                The response format is text/csv -- comma separated.  There is a header row.
                Values are enclosed in
                double-quotes (") if it contains a double-quote, comma, or newline.  Embedded
                double-quotes are escaped with another double-quote, thus: foo"bar becomes
                "foo""bar". """)
  @GET
  @Produces("text/json") // TODO HACK!  Lie so that validation errors can be mapped to 400
  @Timed
  fun export(@BeanParam
             qConstraints: ConstraintsParams,

             @QueryParam("d.docs.limit") @Min(1) @Max(100) // TODO increase once we have authentication
             @ApiParam("How many documents to return.")
             aDocsLimit: Int
  ): Response {
    val solrQuery = SolrQuery()
    solrQuery.requestHandler = "/select/bop/export"

    qConstraints.applyToSolrQuery(solrQuery)

    requestDocs(aDocsLimit, DocSortEnum.time, qConstraints, solrQuery)

    solrQuery.set("echoParams", "all") // so that we can read 'fl' (we configured Solr to have this)

    val solrResp: QueryResponse
    try {
      solrResp = solrClient.query(solrQuery);
    } catch(e: SolrException) {
      throw WebApplicationException(e.message, e.code()) // retain http code
    }

    // assume this echos params on the server to include 'fl' (we arranged for this in solrconfig)
    val flStr = (solrResp.header.findRecursive("params", "fl")
            ?: throw Exception("Expected echoParams=all and 'fl' to be set")) as String
    val fieldList = flStr.split(',')

    val streamingOutput = StreamingOutput { outputStream: OutputStream ->
      val writer = outputStream.writer() //defaults to UTF8 in Kotlin
      fun OutputStreamWriter.writeEscaped(str: String)
              = StringEscapeUtils.ESCAPE_CSV.translate(str, this) // commons-lang3

      // write header
      for ((index, f) in fieldList.withIndex()) {
        if (index != 0) writer.write(','.toInt())
        writer.writeEscaped(f)
      }
      writer.write('\n'.toInt())

      // loop docs
      for (doc in solrResp.results) {
        val map = docToMap(doc)
        // write doc
        for ((index, f) in fieldList.withIndex()) {
          if (index != 0) writer.write(','.toInt())
          map[f]?.let { writer.writeEscaped(it.toString()) }
        }
        writer.write('\n'.toInt())
      }
      writer.flush()
    }

    return Response.ok(streamingOutput, "text/csv;charset=utf-8") // TODO or JSON eventually
            .header("Content-Disposition", "attachment") // don't show in-line in browser
            .build()

    // TODO use Solr StreamingResponseCallback. Likely then need another thread & Pipe

    // TODO "Content-Disposition","attachment"
    // TODO support JSON (.json in path?)
    // TODO support Solr cursorMark
    // TODO only let one request do this at a time (metered, or perhaps with authorization)
    // TODO iterate the shards in time descending instead of all at once

  }

}
