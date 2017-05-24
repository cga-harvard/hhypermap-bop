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

package edu.harvard.gis.hhypermap.bop.ingest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals


class TestIngest {

  @Test
  fun testTransform() {
    val input = """{
  "contributors": null,
  "truncated": false,
  "text": "Can you recommend anyone for this #job in ? https://t.co/LYiQrBd7i4 #Healthcare #Hiring #CareerArc",
  "is_quote_status": false,
  "in_reply_to_status_id": null,
  "id": 786254188132962300,
  "favorite_count": 0,
  "source": "<a href=\"http://www.tweetmyjobs.com\" rel=\"nofollow\">TweetMyJOBS</a>",
  "retweeted": false,
  "coordinates": {
    "type": "Point",
    "coordinates": [
      -121.7680088,
      37.6818745
    ]
  },
  "timestamp_ms": "1476292581004",
  "entities": {
    "user_mentions": [],
    "symbols": [],
    "hashtags": [
      {
        "indices": [
          34,
          38
        ],
        "text": "job"
      },
      {
        "indices": [
          68,
          79
        ],
        "text": "Healthcare"
      },
      {
        "indices": [
          80,
          87
        ],
        "text": "Hiring"
      },
      {
        "indices": [
          88,
          98
        ],
        "text": "CareerArc"
      }
    ],
    "urls": [
      {
        "url": "https://t.co/LYiQrBd7i4",
        "indices": [
          44,
          67
        ],
        "expanded_url": "http://bit.ly/2c2LZg4",
        "display_url": "bit.ly/2c2LZg4"
      }
    ]
  },
  "in_reply_to_screen_name": null,
  "id_str": "786254188132962304",
  "retweet_count": 0,
  "in_reply_to_user_id": null,
  "favorited": false,
  "user": {
    "follow_request_sent": null,
    "profile_use_background_image": true,
    "default_profile_image": false,
    "id": 21712764,
    "verified": false,
    "profile_image_url_https": "https://pbs.twimg.com/profile_images/668671014188945408/YCpTfOfI_normal.jpg",
    "profile_sidebar_fill_color": "407DB0",
    "profile_text_color": "000000",
    "followers_count": 464,
    "profile_sidebar_border_color": "000000",
    "id_str": "21712764",
    "profile_background_color": "253956",
    "listed_count": 117,
    "profile_background_image_url_https": "https://pbs.twimg.com/profile_background_images/315559631/Twitter-BG_2_bg-image.jpg",
    "utc_offset": -14400,
    "statuses_count": 505,
    "description": "Follow this account for geo-targeted Healthcare job tweets in San Jose, CA. Need help? Tweet us at @CareerArc!",
    "friends_count": 306,
    "location": "San Jose, CA",
    "profile_link_color": "4A913C",
    "profile_image_url": "http://pbs.twimg.com/profile_images/668671014188945408/YCpTfOfI_normal.jpg",
    "following": null,
    "geo_enabled": true,
    "profile_banner_url": "https://pbs.twimg.com/profile_banners/21712764/1448258569",
    "profile_background_image_url": "http://pbs.twimg.com/profile_background_images/315559631/Twitter-BG_2_bg-image.jpg",
    "name": "TMJ- SJC Health Jobs",
    "lang": "en",
    "profile_background_tile": false,
    "favourites_count": 0,
    "screen_name": "tmj_sjc_health",
    "notifications": null,
    "url": "http://www.careerarc.com/job-seeker",
    "created_at": "Tue Feb 24 00:35:43 +0000 2009",
    "contributors_enabled": false,
    "time_zone": "Eastern Time (US & Canada)",
    "protected": false,
    "default_profile": false,
    "is_translator": false
  },
  "geo": {
    "type": "Point",
    "coordinates": [
      37.6818745,
      -121.7680088
    ]
  },
  "in_reply_to_user_id_str": null,
  "possibly_sensitive": false,
  "lang": "en",
  "created_at": "Wed Oct 12 17:16:21 +0000 2016",
  "filter_level": "low",
  "in_reply_to_status_id_str": null,
  "place": {
    "full_name": "Livermore, CA",
    "url": "https://api.twitter.com/1.1/geo/id/159279f05be2ade4.json",
    "country": "United States",
    "place_type": "city",
    "bounding_box": {
      "type": "Polygon",
      "coordinates": [
        [
          [
            -121.823726,
            37.63653
          ],
          [
            -121.823726,
            37.730654
          ],
          [
            -121.696432,
            37.730654
          ],
          [
            -121.696432,
            37.63653
          ]
        ]
      ]
    },
    "country_code": "US",
    "attributes": {},
    "id": "159279f05be2ade4",
    "name": "Livermore"
  },
  "hcga_sentiment": "pos",
  "hcga_geoadmin_admin2": [
    {
      "id": "244-5-184",
      "txt": "United States_California_Alameda"
    }
  ],
  "hcga_geoadmin_us_census_tract": [
    {
      "tract": "06001451601"
    }
  ],
  "hcga_geoadmin_us_ma_census_block": []
}"""
    val jsonObj = ObjectMapper().readValue(input, ObjectNode::class.java)
    val solrDoc = edu.harvard.gis.hhypermap.bop.ingest.jsonToSolrInputDoc(jsonObj)
    // Replace Date with millis because the toString test we do next shouldn't be dependent on the
    //   current timezone.
    solrDoc.get("created_at")?.let { field -> field.setValue( (field.value as Date).time, 1f) }
    assertEquals("""SolrInputDocument(fields: [id=786254188132962304, created_at=1476292581004, minuteOfDayByUserTimeZone=796, coord=37.6818745,-121.7680088, text=Can you recommend anyone for this #job in ? https://t.co/LYiQrBd7i4 #Healthcare #Hiring #CareerArc, user_name=tmj_sjc_health, lang=en, sentiment_pos=true, geoadmin_admin2_count=1, geoadmin_admin2=/244/5/184, geoadmin_admin2_0_pathdv=/244, geoadmin_admin2_1_pathdv=/244/5, geoadmin_admin2_2_pathdv=/244/5/184, geoadmin_admin2_txt=/United States/California/Alameda, geoadmin_admin2_txt_0_pathdv=/United States, geoadmin_admin2_txt_1_pathdv=/United States/California, geoadmin_admin2_txt_2_pathdv=/United States/California/Alameda, geoadmin_us_census_tract_count=1, geoadmin_us_census_tract=06001451601, geoadmin_us_ma_census_block_count=0])""",
            solrDoc.toString())
  }
}