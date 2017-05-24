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

import java.time.Duration
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.*

/** Similar to a JDK [Duration] & [Period] but has just one quantity & unit (simpler) and is one
 * class not two that aren't quite the same. */
data class Gap private constructor (val quantity: Long, val unit: ChronoUnit) {
  companion object {
    fun parseISO8601(str: String): Gap {
      val quantity: Long
      val unit: ChronoUnit
      if (str.contains('T')) { // less than a day
        val match = Regex("""PT(\d+)([HMS])""").matchEntire(str)
                ?: throw DateTimeParseException("Doesn't match pattern", str, 0)
        quantity = match.groupValues[1].toLong()
        unit = when(match.groupValues[2]) {
          "H" -> HOURS
          "M" -> MINUTES
          "S" -> SECONDS
          else -> throw IllegalStateException()
        }
      } else {
        val match = Regex("""P(\d+)([YMWD])""").matchEntire(str)
                ?: throw DateTimeParseException("Doesn't match pattern", str, 0)
        quantity = match.groupValues[1].toLong()
        unit = when(match.groupValues[2]) {
          "Y" -> YEARS
          "M" -> MONTHS
          "W" -> WEEKS
          "D" -> DAYS
          else -> throw IllegalStateException()
        }
      }
      return of(quantity, unit)
    }

    fun parseSolr(solrGap: String): Gap {
      val match = Regex("""\+(\d+)(\w+)""").matchEntire(solrGap) ?: throw Exception("bad gap?: $solrGap")
      val quantity = match.groupValues[1].toLong()
      val unit = valueOf(match.groupValues[2].toUpperCase())
      // note: I could use Period.of... then .toString() but it's actually more work
      return of(quantity, unit)
    }

    private val UNITS = listOf(SECONDS, MINUTES, HOURS,
            DAYS, WEEKS, MONTHS, YEARS)

    /** Factory method, which normalizes to the ultimate quanitty & unit. */
    fun of(quantity: Long, unit: ChronoUnit): Gap {
      // We normalize the arguments (e.g. 7 days becomes 1 week)
      if (quantity == 0L) {
        return Gap(quantity, DAYS)// normalize to DAYS (doesn't matter)
      }
      val idx = UNITS.indexOf(unit)
      if (idx == -1) { // convert to a supported unit then normalize recursively
        of(unit.duration.multipliedBy(quantity).toMillis() * 1000, SECONDS)
      }
      if (quantity > 1 && unit != YEARS) {
        // see if fits evenly into the next larger unit
        val millis = unit.duration.toMillis() * quantity
        val nextUnit = UNITS[idx + 1]
        val nextUnitMillis = nextUnit.duration.toMillis()
        if ((millis / nextUnitMillis) * nextUnitMillis == millis) {
          return of(millis / nextUnitMillis, nextUnit) // recursive
        }
      }
      return Gap(quantity, unit)
    }

    /** Compute a gap that seems reasonable, considering natural time units and limit */
    fun computeGap(rangeDuration: Duration, limit: Int): Gap = when {
      //TODO support 10-min, min, 10-sec, sec
      rangeDuration.toHours() < Math.min(limit, 24 * 4) -> Gap(1, HOURS) // < 4 days worth
      rangeDuration.toDays() < Math.min(limit, 7 * 4) -> Gap(1, DAYS) // < 4 weeks worth of days
      rangeDuration.toDays() / 7 < Math.min(limit, 100) -> Gap(1, WEEKS) // weeks but not more than 100
      //TODO support month, year, X-years (instead of X-days)
      else -> {
        val days = Math.ceil(rangeDuration.toDays().toDouble() / limit).toLong()
        of(Math.max(7, days), DAYS) // no shorter than week nonetheless
      }
    }
  }

  fun toSolr(): String {
    return if (unit == WEEKS) { // Solr doesn't support that
      "+${quantity * 7}DAYS"
    } else {
      "+${quantity}${unit.name.toUpperCase()}"
    }
  }

  fun toISO8601(): String {
    val prefix = if (unit < DAYS) "PT" else "P"
    return "$prefix$quantity${unit.name.first()}"
  }

  fun toMillis(): Long = unit.duration.toMillis() * quantity

  override fun toString(): String = toISO8601()
}
