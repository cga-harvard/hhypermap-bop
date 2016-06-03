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

import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.shape.Point
import org.locationtech.spatial4j.shape.Rectangle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.ws.rs.WebApplicationException

val SOLR_RANGE_PATTERN = Regex("""\[(\S+) TO (\S+)\]""").toPattern()

fun parseSolrRangeAsPair(str: String): Pair<String, String> {
  val matcher = SOLR_RANGE_PATTERN.matcher(str)
  if (matcher.matches()) {
    return Pair(matcher.group(1), matcher.group(2))
  } else {
    throw WebApplicationException("Regex $SOLR_RANGE_PATTERN couldn't parse $str")
  }
}

// Time stuff

fun parseDateTimeRange(aTimeFilter: String?): Pair<Instant?, Instant?> {
  val (startStr, endStr) = parseSolrRangeAsPair(aTimeFilter ?: "[* TO *]")
  return Pair(parseDateTime(startStr), parseDateTime(endStr)).apply {
    if (first != null && second != null && (first as Instant).isAfter(second)) {
      throw WebApplicationException("Start must come before End: $aTimeFilter", 400)
    }
  }
}

fun parseDateTime(str: String): Instant? {
  return when {
    str == "*" -> null // open-ended
    str.contains('T') -> LocalDateTime.parse(str).toInstant(ZoneOffset.UTC) // "2016-05-15T00:00:00"
    else -> LocalDate.parse(str).atStartOfDay(ZoneOffset.UTC).toInstant() // "2016-05-15"
  }
}


// Spatial stuff:

val SPATIAL4J_CTX = SpatialContext.GEO
val spatial4jShapeFactory = SPATIAL4J_CTX.shapeFactory

fun toLatLon(center: Point): String {
  return "${center.y},${center.x}"
}

/** [lat,lon TO lat,lon] */
fun parseGeoBox(geoBoxStr: String): Rectangle {
  val (fromPtStr, toPtStr) = parseSolrRangeAsPair(geoBoxStr)
  val fromPt = parseLatLon(fromPtStr)
  val toPt = parseLatLon(toPtStr)
  return spatial4jShapeFactory.rect(fromPt, toPt);
}

fun parseLatLon(latLonStr: String): Point {
  try {
    val cIdx = latLonStr.indexOf(',');
    val lat = latLonStr.substring(0, cIdx).toDouble()
    val lon = latLonStr.substring(cIdx + 1).toDouble()
    return spatial4jShapeFactory.pointXY(lon, lat)
  } catch(e: Exception) {
    throw WebApplicationException(e.toString(), 400)
  }
}