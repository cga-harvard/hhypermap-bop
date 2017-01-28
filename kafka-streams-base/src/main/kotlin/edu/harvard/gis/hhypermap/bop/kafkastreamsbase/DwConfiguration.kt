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
import java.util.*
import javax.validation.constraints.NotNull

/**
 * Configuration base class handling common stuff.
 */
abstract class DwConfiguration {

  @JsonProperty("logging")
  @NotNull
  val loggingConfig: DefaultLoggingFactory = DefaultLoggingFactory()

  open fun postBuild() {
    // HACK:   To easily configure logging, we'd like to be able set sys props or env vars
    //   of the form "dw.logging.loggers.edu-harvard-gis". We can't do period for the package
    //   because it confuses Dropwizard's object path walking stuff. So we post-process here.
    //   I'd like to have this code automatically occur via the setter of the loggers but
    //   Jackson/Dropwizard's factory polymorphism makes this too awkward IMO.
    loggingConfig.setLoggers(replaceHyphenatedKeys(loggingConfig.loggers))
  }

  protected fun <V> replaceHyphenatedKeys(map: Map<String, V>): MutableMap<String, V>
          = map.mapKeysTo(
            LinkedHashMap(map.size),
            { it.key.replace('-', '.') } )
}
