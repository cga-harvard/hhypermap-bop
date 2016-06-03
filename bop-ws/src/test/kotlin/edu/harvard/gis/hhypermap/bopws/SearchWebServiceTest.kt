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

import com.fasterxml.jackson.databind.JsonNode
import io.dropwizard.testing.junit.ResourceTestRule
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.common.SolrInputDocument
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.net.URI
import java.time.Instant
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

//import org.junit.Assert.*

class SearchWebServiceTest {
  companion object {
    val solrClient = HttpSolrClient("http://localhost:8983/solr/bop-tests") // collection must exist already

    @JvmField @ClassRule val resources = ResourceTestRule.builder()
            .addProvider(DwApplication.DTPExceptionMapper)
            .addResource(SearchWebService(solrClient)).build()

    @JvmStatic @BeforeClass fun beforeClass() {
      solrClient.add(listOf(
              doc("2015-04-01T12:00:00Z", "41.5,-70", "Apple fruit"),
              doc("2015-04-02T12:00:00Z", "42.5,-70", "Orange fruit fruit fruit"),
              doc("2015-04-03T12:00:00Z", "43.5,-70", "Lemon fruit")
      ))
      solrClient.commit()
    }

    @JvmStatic @AfterClass fun afterClass() {
      solrClient.deleteByQuery("*:*")
      solrClient.commit()
      solrClient.close()
    }

    fun doc(created_at: String, coordLatLon: String, text: String ): SolrInputDocument =
            SolrInputDocument().apply {
              setField("text", text)
              setField("created_at", created_at.toString())
              setField("coord", coordLatLon)
              setField("id", Instant.parse(created_at).toEpochMilli()) // good enough
            }
  }

  // TEXT

  @Test fun testTextFilter() {
    // implicit AND default
    reqJson(uri("/tweets/search", "q.text" to "Apple fruit")).let {
      assertEquals(1, it["a.matchDocs"].asInt())
    }
    // let user choose explicit OR
    reqJson(uri("/tweets/search", "q.text" to "Apple OR Orange")).let {
      assertEquals(2, it["a.matchDocs"].asInt())
    }
  }// tests explicit 'OR', and

  @Test fun testTextScoreOrder()
          = reqJson(uri("/tweets/search", "q.text" to "fruit", "d.docs.limit" to "2")).let {
    assertEquals(3, it["a.matchDocs"].asInt(), it.toString())
    assertEquals("Orange fruit fruit fruit", it["d.docs"][0]["text"].textValue(), it.toString())
  }

  // TIME

  @Test fun testTimeFilter() {
    reqJson(uri("/tweets/search", "q.time" to "[2015-04-01 TO 2015-04-03]")).let {
      assertEquals(2, it["a.matchDocs"].asInt())
    }
    reqJson(uri("/tweets/search", "q.time" to "[2015-04-03T00:00:00 TO *]")).let {
      assertEquals(1, it["a.matchDocs"].asInt())
    }
    assertReq400(uri("/tweets/search", "q.time" to "[2015-04-03T00:00:00Z TO *]")) // 'Z' bad format
  }

  @Test fun testTimeOrder()
          = reqJson(uri("/tweets/search", "q.text" to "fruit", "d.docs.limit" to "99", "d.docs.sort" to "time")).let {
    assertEquals("Lemon fruit", it["d.docs"][0]["text"].textValue(), it.toString())
  }

  @Test fun testTimeFacets() {
    reqJson(uri("/tweets/search", "a.time.limit" to "1000", "a.time.gap" to "P1D",
            "a.time.filter" to "[2015-04-01 TO 2015-04-03]"))["a.time"].let {
      assertEquals("P1D", it.get("gap").textValue())
      assertEquals(2, it["counts"].size())
      assertEquals(1, it["counts"][0]["count"].asInt())
      assertEquals(1, it["counts"][1]["count"].asInt())
    }
    // just change the gap
    reqJson(uri("/tweets/search", "a.time.limit" to "1000", "a.time.gap" to "PT1H",
            "a.time.filter" to "[2015-04-01 TO 2015-04-03]"))["a.time"].let {
      assertEquals("PT1H", it.get("gap").textValue())
      assertEquals(2, it["counts"].size())
    }
  }

  @Test fun testTimeFacetsComputeGap() {
    // test no gap; guess days
    fun assertGap(expectGap: String, start: String, end: String, limit: Int = 100) {
      reqJson(uri("/tweets/search", "a.time.limit" to limit.toString(),
              "a.time.filter" to "[$start TO $end]"))["a.time"].let {
        assertEquals(Gap.parseISO8601(expectGap), Gap.parseISO8601(it.get("gap").textValue()))
      }
    }
    assertGap("PT1H", "2015-04-01", "2015-04-02")// 2 days -> 1 hour gap
    assertGap("P1D", "2015-04-01", "2015-04-20")// 20 days -> 1 day gap
    assertGap("P7D", "2015-04-01", "2015-05-10")// >30 days -> 1 week gap
    assertGap("P7D", "2015-01-01", "2015-12-31")// 360 days / 100 limit -> 1 week gap still (not 4d)
    assertGap("P72D", "2015-01-01", "2015-12-26", limit =  5)// ~360 days / 5 limit -> 72 days
    assertGap("P1D", "2015-04-01", "2015-04-02", limit = 5)// small limit; don't choose hours
  }

  // GEO

  @Test fun testGeoFilter()
          = reqJson(uri("/tweets/search", "q.geo" to "[42,-80 TO 50,-60]")).let {
    assertEquals(2, it["a.matchDocs"].asInt())
  }

  @Test fun testGeoDistOrder()
          = reqJson(uri("/tweets/search",
          "q.geo" to "[30,-80 TO 50,-60]", "d.docs.limit" to "1", "d.docs.sort" to "distance")).let {
    // middle point is 40,-70; box grabs all docs
    assertEquals(3, it["a.matchDocs"].asInt(), it.toString())
    assertEquals("Apple fruit", it["d.docs"][0]["text"].asText(), it.toString())
  }

  @Test fun testGeoHeatmapFacets() {
    var firstGridLevel = -1
    var firstCells = -1
    val uri = uri("/tweets/search", "a.hm.limit" to "10",
            "a.geo.filter" to "[30,-80 TO 50,-60]")
    reqJson(uri)["a.hm"].let {
      firstGridLevel = it["gridLevel"].asInt()
      firstCells = it["rows"].asInt() * it["columns"].asInt()
      assertTrue { firstCells <= 10 && firstCells >= Math.floor(10.0 / 4.0) } // quad tree
      val countSum = it["counts_ints2D"].sumBy { row -> row.sumBy { col -> col.asInt() } }
      assertEquals(3, countSum)
    }
    // test with next detailed grid level
    reqJson(uri.queryParam("a.hm.limit", "1") //limit is effectively ignored
            .queryParam("a.hm.gridLevel", firstGridLevel.plus(1).toString()))["a.hm"].let {
      assertEquals(firstGridLevel + 1, it["gridLevel"].asInt())
      assertEquals(firstCells * 4, it["rows"].asInt() * it["columns"].asInt())
    }
  }

  private fun uri(path: String, vararg queryParams: Pair<String,String>): UriBuilder
    = UriBuilder.fromPath(path).apply { for ((n,v) in queryParams) queryParam(n, v) }

  private fun reqJson(uriBuilder: UriBuilder): JsonNode {
    val rsp: Response = resources.client().target(uriBuilder).request().get() // HTTP GET
    val json = try {
      assert(200 == rsp.status, {"Bad Status: ${rsp.status}, entity: ${rsp.readEntity(Any::class.java)}"})
      rsp.readEntity(JsonNode::class.java)
    } finally {
      rsp.close() //note: doesn't implement Closeable but has close method
    }
    assertDataAgnostic(uriBuilder, json)
    return json
  }

  /** has assertions that are agnostic to the actual data */
  private fun assertDataAgnostic(uriBuilder: UriBuilder, json: JsonNode) {
    val queryParams = URI(uriBuilder.toString()).query
            .split('&').map { it.substringBefore('=') to it.substringAfter('=') }.toMap()
    val dDocsLimit = queryParams["d.docs.limit"]?.toInt()
    if (dDocsLimit != null) {
      assertEquals(Math.min(dDocsLimit, json["a.matchDocs"].asInt()), json["d.docs"].size())
    } else {
      assertFalse(json.hasNonNull("d.docs"))
    }
    val aTimeLimit = queryParams["a.time.limit"]?.toInt()
    if (aTimeLimit != null) {
      queryParams["a.time.gap"]?.let {
        assertEquals(Gap.parseISO8601(it), Gap.parseISO8601(json["a.time"]["gap"].textValue()))
      }
    } else {
      assertFalse(json.hasNonNull("a.time"))
    }
  }

  private fun assertReq400(uriBuilder: UriBuilder) {
    val rsp: Response = resources.client().target(uriBuilder).request().get() // HTTP GET
    try {
      assertEquals(400, rsp.status, rsp.toString())
    } finally {
      rsp.close()
    }
  }

}