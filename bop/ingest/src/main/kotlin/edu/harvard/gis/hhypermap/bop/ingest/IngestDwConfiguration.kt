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
import org.apache.solr.client.solrj.SolrRequest
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient
import org.apache.solr.client.solrj.request.RequestWriter
import org.apache.solr.common.util.NamedList
import org.hibernate.validator.constraints.NotEmpty
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern


class IngestDwConfiguration : DwConfiguration() {

  // Kafka stuff:

  @JsonProperty @NotEmpty
  var kafkaSourceTopic: String? = null

  @JsonProperty
  val kafkaOffsetCommitIntervalMs: Long = 30000

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
  var solrQueueSize = 1000

  /** If this many milliseconds pass, the streamed connection to Solr will end. */
  @JsonProperty
  var solrPollQueueTimeMs = 1000 // SolrJ ConcurrentUpdateSolrClient defaults to 250

  fun newSolrClient() : ConcurrentUpdateSolrClient {

    return object : ConcurrentUpdateSolrClient(solrConnectionString, solrQueueSize, solrThreadCount) {
      init {
        setPollQueueTime(solrPollQueueTimeMs)
//        setRequestWriter(RequestWriter()) // XML
      }

      //TODO CUSC ought to do this error handling by itself
      //   https://issues.apache.org/jira/browse/SOLR-3284

      val errorRef = AtomicReference<Throwable>()

      override fun handleError(ex: Throwable) {
        if (! errorRef.compareAndSet(null, ex)) {
          // there is an existing error yet to be reported. Just log this one.
          super.handleError(ex) // default impl logs
        }
      }

      override fun close() {
        try {
          throwIfError()
        } finally {
          super.close()
        }
      }

      override fun request(request: SolrRequest<*>?, collection: String?): NamedList<Any> {
        throwIfError()
        return super.request(request, collection)
      }

      override fun blockUntilFinished() {
        super.blockUntilFinished()
        throwIfError()
      }

      private fun throwIfError() {
        val ex = errorRef.get()
        if (ex != null) {
          errorRef.compareAndSet(ex, null) // set to null, only if it's the same exception
          throw ex
        }
      }

    }
  }

}