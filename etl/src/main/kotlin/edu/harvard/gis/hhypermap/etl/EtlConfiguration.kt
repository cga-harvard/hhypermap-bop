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

package edu.harvard.gis.hhypermap.etl

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.logging.DefaultLoggingFactory
import io.dropwizard.logging.LoggingFactory
import org.hibernate.validator.constraints.NotEmpty

class EtlConfiguration {

  @JsonProperty
  @NotEmpty
  var kafkaSourceTopic: String? = null

  @JsonProperty
  @NotEmpty
  var kafkaDestTopic: String? = null

  @JsonProperty("kafkaStreams")
  val kafkaStreamsConfig: MutableMap<String,Any> = mutableMapOf()

  @JsonProperty("logging")
  val loggingConfig: LoggingFactory = DefaultLoggingFactory()

}