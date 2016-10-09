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

package edu.harvard.gis.hhypermap.bop.kafkastreamsbase

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.logging.DefaultLoggingFactory
import io.dropwizard.logging.LoggingFactory
import java.util.*
import javax.validation.constraints.NotNull

/**
 * Configuration base class handling common stuff.
 */
abstract class DwStreamsConfiguration {

  private var _kafkaStreamsConfig: MutableMap<String,Any> = mutableMapOf()
  val kafkaStreamsConfig: MutableMap<String,Any>
    @JsonProperty("kafkaStreams")
    @NotNull
    get() {
      // rewrite hyphens to periods. We do this so we can use sys & env prop overrides without
      // DropWizard interpreting the '.' as a sub-object

      // It's bad practice to update in a getter... but not sure what's better
      _kafkaStreamsConfig = _kafkaStreamsConfig.mapKeysTo(
              LinkedHashMap(_kafkaStreamsConfig.size),
              { it.key.replace('-', '.') } )
      return _kafkaStreamsConfig
    }

  @JsonProperty("logging")
  @NotNull
  val loggingConfig: LoggingFactory = DefaultLoggingFactory()

}
