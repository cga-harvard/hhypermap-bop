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

import com.fasterxml.jackson.annotation.JsonProperty
import edu.harvard.gis.hhypermap.bop.kafkastreamsbase.DwConfiguration
import edu.harvard.gis.hhypermap.bop.kafkastreamsbase.DwKafkaStreamsConfiguration
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient
import org.hibernate.validator.constraints.NotEmpty
import java.util.*
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern


class IngestDwConfiguration : DwConfiguration() {

  // Kafka stuff:

  @JsonProperty @NotEmpty
  var kafkaSourceTopic: String? = null

  @JsonProperty
  val kafkaOffsetCommitIntervalMs: Long = 5000

  private var _kafkaConsumerConfig: MutableMap<String,Any> = mutableMapOf()
  /** governed by KafkaConsumer */
  val kafkaConsumerConfig: MutableMap<String,Any>
    @JsonProperty("kafkaConsumer")
    @NotNull
    get() {
      // rewrite hyphens to periods. We do this so we can use sys & env prop overrides without
      // DropWizard interpreting the '.' as a sub-object

      // It's bad practice to update in a getter... but not sure what's better
      _kafkaConsumerConfig = _kafkaConsumerConfig.mapKeysTo(
              LinkedHashMap(_kafkaConsumerConfig.size),
              { it.key.replace('-', '.') } )
      return _kafkaConsumerConfig
    }

  // Solr stuff:

  @JsonProperty @NotEmpty @Pattern(regexp = "http://(.+)/")
  var solrConnectionString: String? = null

  @JsonProperty @NotEmpty
  var solrCollection: String? = null

  /** Concurrent threads to load Solr. Be careful! */
  @JsonProperty
  var solrThreadCount = 1

  /** Max size of pending queue before blocks. */
  @JsonProperty
  var solrQueueSize = 100

  fun newSolrClient() : ConcurrentUpdateSolrClient {
    return ConcurrentUpdateSolrClient.Builder(solrConnectionString)
            .withThreadCount(solrThreadCount)
            .withQueueSize(solrQueueSize)
            .build()
  }


}