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

import com.codahale.metrics.JmxReporter
import com.codahale.metrics.MetricRegistry
import io.dropwizard.configuration.ConfigurationSourceProvider
import io.dropwizard.configuration.FileConfigurationSourceProvider
import io.dropwizard.configuration.ResourceConfigurationSourceProvider
import io.dropwizard.configuration.YamlConfigurationFactory
import io.dropwizard.jackson.Jackson
import io.dropwizard.logging.BootstrapLogging
import io.dropwizard.validation.BaseValidator
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.util.*
import javax.management.ObjectName
import javax.management.StandardMBean

/**
 * A DropWizard oriented Application base class.
 * It's not a typical DropWizard app since there is no web interface.
 */
abstract class DwApplication<C : DwConfiguration>(mainArgs: Array<String>, cClazz: Class<C>) {
  companion object {
    val METRIC_REGISTRY = MetricRegistry()
  }

  val log = LoggerFactory.getLogger(this.javaClass.`package`.name)!!
  val dwConfig: C

  private val closeHooks = ArrayDeque<() -> Unit>()

  init {
    Runtime.getRuntime().addShutdownHook(Thread(null, {
      runCloseHooks()
    }, "closeHookRunner"))
  }

  fun addCloseHook(hook: AutoCloseable) = addCloseHook({hook.close()})

  fun addCloseHook(hook: ()-> Unit) {
    synchronized(closeHooks) {
      closeHooks.addLast(hook)
    }
  }

  fun runCloseHooks() {
    log.debug("Running close hooks")
    synchronized(closeHooks) { // only run close hooks in serial
      while (true) {
        val hook = closeHooks.pollLast() ?: break
        try {
          hook.invoke()
        } catch (e: Exception) {
          log.error(e.toString(), e)
        }
      }
      println("Shut down cleanly.")
    }
  }

  init {
    check(mainArgs.size <= 1, {"Accepts optionally the yaml config file"})
    val configFile: File? = if (mainArgs.size == 1) File(mainArgs[0]) else null

    BootstrapLogging.bootstrap()

    dwConfig = buildConfig(configFile, cClazz)

    //configure logging as soon as we can
    dwConfig.loggingConfig.configure(METRIC_REGISTRY, "dw")
    addCloseHook { dwConfig.loggingConfig.stop()}

    exposeRsrcFileAsJmx(log.name+".GIT", "/git.properties").let { textMBean ->
      log.info("git.properites: " + textMBean.text)
    }


    val jmxReporter = JmxReporter.forRegistry(METRIC_REGISTRY).build()
    jmxReporter.start()
    addCloseHook {jmxReporter.stop()}
  }

  open fun buildConfig(configFile: File? = null, cClazz: Class<C>): C {
    val configProvider: ConfigurationSourceProvider
    val configPath: String
    if (configFile != null) {
      configProvider = FileConfigurationSourceProvider()
      configPath = configFile.toString()
    } else {
      // TODO take optional String arg and see if it's a URL?
      configProvider = ResourceConfigurationSourceProvider()
      configPath = "dw.yml"
    }
    // propagate env to sys props so DropWizard will see it
    val propertyPrefix = "dw." //sysprop & env prefix
    for ((prefName, value) in System.getenv()) {
      if (prefName.startsWith(propertyPrefix) && System.getProperty(prefName) == null) {
        System.setProperty(prefName, value)
      }
    }
    return YamlConfigurationFactory<C>(
            cClazz,
            BaseValidator.newValidator(),
            Jackson.newObjectMapper(),
            propertyPrefix)
            .build(configProvider, configPath).apply { postBuild() }
  }

  fun exposeRsrcFileAsJmx(jmxName: String, rsrcPath: String): TextMBean {
    val mbean = object : StandardMBean(TextMBean::class.java, false), TextMBean {
      override val text: String
        get() =
        this.javaClass.getResourceAsStream(rsrcPath)
                .reader(StandardCharsets.ISO_8859_1).readText()
    }
    ManagementFactory.getPlatformMBeanServer()
            .registerMBean(mbean, ObjectName(jmxName+":type=Text"))
    return mbean
  }

  interface TextMBean {
    val text: String
  }

}