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
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
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
              doc("2015-04-01T12:00:00Z", "41.5,-70", "Apple fruit", "Alex", true),
              doc("2015-04-02T12:00:00Z", "42.5,-70", "Orange fruit fruit fruit", "Otto", false), // neg
              doc("2015-04-03T12:00:00Z", "43.5,-70", "Lemon fruit", "Luke", true)
      ))
      solrClient.commit()
    }

    @JvmStatic @AfterClass fun afterClass() {
      solrClient.deleteByQuery("*:*")
      solrClient.commit()
      solrClient.close()
    }

    fun doc(created_at: String, coordLatLon: String, text: String, userName: String, posSent: Boolean): SolrInputDocument =
            SolrInputDocument().apply {
              setField("text", text)
              setField("created_at", created_at.toString())
              setField("coord", coordLatLon)
              setField("user_name", userName)
              setField("id", Instant.parse(created_at).toEpochMilli()) // good enough
              setField("sentiment_pos", posSent)
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

  @Test fun testUserFilterFacet() {
    // implicit AND default
    reqJson(uri("/tweets/search", "q.user" to "Otto", "a.user.limit" to "10")).let {
      assertEquals(1, it["a.matchDocs"].asInt())
      assertEquals(3, it["a.user"].size());// due to multi-select faceting, we show all 3, not 1
    }

  }// tests explicit 'OR', and

  @Test fun testTextScoreOrder()
          = reqJson(uri("/tweets/search", "q.text" to "fruit", "d.docs.limit" to "2")).let {
    assertEquals(3, it["a.matchDocs"].asInt(), it.toString())
    assertEquals("Orange fruit fruit fruit", it["d.docs"][0]["text"].textValue(), it.toString())
    // test returns date string in expected format
    assertEquals("2015-04-02T12:00:00Z", it["d.docs"][0]["created_at"].textValue(), it.toString())
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
    // note q.time is disjoint from a.time.filter but we still get results
    reqJson(uri("/tweets/search", "q.time" to "[2000-01-01 TO 2001-01-01]",
            "a.time.limit" to "1000", "a.time.gap" to "P1D",
            "a.time.filter" to "[2015-04-01 TO 2015-04-03]"))["a.time"].let {
      assertEquals("P1D", it["gap"].textValue())
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

  @Test fun testGeoFilter() {
    reqJson(uri("/tweets/search", "q.geo" to "[42,-80 TO 50,-60]")).let {
      assertEquals(2, it["a.matchDocs"].asInt())
    }
    // bad spatial (-800 lon)
    assertReq400(uri("/tweets/search",
            "q.geo" to "[30,-800 TO 50,-60]"))
  }

  @Test fun testGeoDistOrder() {
    reqJson(uri("/tweets/search",
            "q.geo" to "[30,-80 TO 50,-60]", "d.docs.limit" to "1", "d.docs.sort" to "distance")).let {
      // middle point is 40,-70; box grabs all docs
      assertEquals(3, it["a.matchDocs"].asInt(), it.toString())
      assertEquals("Apple fruit", it["d.docs"][0]["text"].asText(), it.toString())
    }
    // bad spatial (-800 lon)
    assertReq400(uri("/tweets/search",
            "q.geo" to "[30,-800 TO 50,-60]", "d.docs.limit" to "1", "d.docs.sort" to "distance"))
  }

  @Test fun testGeoHeatmapFacets() {
    var firstGridLevel = -1
    var firstCells = -1
    // note q.geo is disjoint from a.geo.filter but we still get results
    val uri = uri("/tweets/search", "q.geo" to "[-50,-50 TO -49,-49]",
            "a.hm.limit" to "10",
            "a.hm.filter" to "[30,-80 TO 50,-60]")
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
      val numCells = it["rows"].asInt() * it["columns"].asInt()
      // it could be less than 4x the previous grid level since the edges are partial and thus
      // some sub-cells may get filtered out.
      assert(numCells >= 2*firstCells && numCells <= 4*firstCells, {"numCells: $numCells"})
    }

    val jsonRsp = reqJson(uri.queryParam("a.hm.posSent", "true"))
    fun sum2dJsonArray(counts2d: ArrayNode): Int =
      counts2d.sumBy { row -> (row as? ArrayNode)?.sumBy { it.asInt() } ?: 0 } // note: sometimes NullNode (not ArrayNode)
    assertEquals(3, sum2dJsonArray(jsonRsp["a.hm"]["counts_ints2D"] as ArrayNode))
    assertEquals(2, sum2dJsonArray(jsonRsp["a.hm.posSent"]["counts_ints2D"] as ArrayNode))
  }

  // MISC

  @Test fun testTextFacet() {
    reqJson(uri("/tweets/search", "a.text.limit" to "1")).let {
      assertEquals("fruit", it["a.text"][0]["value"].textValue())
      assertEquals(3,       it["a.text"][0]["count"].numberValue())
    }
  }

  @Test fun testExport() {
    // this is a cheasy test, granted
    val uriBuilder = uri("/tweets/export", "q.time" to "[2015-04-01 TO 2015-04-03]", "d.docs.limit" to "2")
    val rsp: Response = resources.client().target(uriBuilder).request().get() // HTTP GET
    try {
      assert(200 == rsp.status, {"Bad Status: ${rsp.status}, entity: ${rsp.readEntity(Any::class.java)}"})
      val resultCsv: String = rsp.readEntity(String::class.java)!!;
      assertEquals("""id,created_at,coord,user_name,text
1427976000000,2015-04-02T12:00:00Z,"42.5,-70",Otto,Orange fruit fruit fruit
1427889600000,2015-04-01T12:00:00Z,"41.5,-70",Alex,Apple fruit
""", resultCsv)
    } finally {
      rsp.close() //note: doesn't implement Closeable but has close method
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
    assertEquals(queryParams["a.hm.limit"] != null, json.hasNonNull("a.hm"))
    assertEquals(queryParams["a.hm.posSent"] != null, json.hasNonNull("a.hm.posSent"))
    if (queryParams["a.hm.posSent"] == "true") {
      // same metadata except counts
      val aHmNoCounts = json["a.hm"].deepCopy<JsonNode>() as ObjectNode
      aHmNoCounts.remove("counts_ints2D")
      val aHmPosNoCounts = json["a.hm.posSent"].deepCopy<JsonNode>() as ObjectNode
      aHmPosNoCounts.remove("counts_ints2D")
      assertEquals(aHmNoCounts, aHmPosNoCounts) // metadata should be the same
    }
    assertEquals(queryParams["a.text.limit"] != null, json.hasNonNull("a.text"))
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