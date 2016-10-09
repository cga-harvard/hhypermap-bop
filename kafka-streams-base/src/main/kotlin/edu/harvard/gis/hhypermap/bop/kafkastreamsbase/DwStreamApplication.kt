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
import com.fasterxml.jackson.databind.node.ObjectNode
import io.dropwizard.configuration.ConfigurationSourceProvider
import io.dropwizard.configuration.FileConfigurationSourceProvider
import io.dropwizard.configuration.ResourceConfigurationSourceProvider
import io.dropwizard.configuration.YamlConfigurationFactory
import io.dropwizard.jackson.Jackson
import io.dropwizard.logging.BootstrapLogging
import io.dropwizard.validation.BaseValidator
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.management.ObjectName
import javax.management.StandardMBean

/**
 * A DropWizard oriented Application base class that uses Kafka Streams.  It's not a typical
 * DropWizard app since there is no web interface.
 */
abstract class DwStreamApplication<C : DwStreamsConfiguration>(mainArgs: Array<String>, cClazz: Class<C>) {
  companion object {
    val METRIC_REGISTRY = MetricRegistry()
  }
  
  val log = LoggerFactory.getLogger(this.javaClass.`package`.name)!!
  val dwConfig: C
  val kafkaStreams: KafkaStreams

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

    exposeRsrcFileAsJmx(log.name+".GIT", "/git.properties")

    val jmxReporter = JmxReporter.forRegistry(METRIC_REGISTRY).build()
    jmxReporter.start()
    addCloseHook {jmxReporter.stop()}

    this.kafkaStreams = buildStreams(dwConfig)
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
            .build(configProvider, configPath)
  }

  fun exposeRsrcFileAsJmx(jmxName: String, rsrcPath: String) {
    val mbean = object : StandardMBean(TextMBean::class.java, false), TextMBean {
      override val text: String
        get() =
        this.javaClass.getResourceAsStream(rsrcPath)
                .reader(StandardCharsets.ISO_8859_1).readText()
    }
    ManagementFactory.getPlatformMBeanServer()
            .registerMBean(mbean, ObjectName(jmxName+":type=Text"))
  }

  interface TextMBean {
    val text: String
  }

  fun buildStreams(dwConfig: DwStreamsConfiguration): KafkaStreams {
    dwConfig.kafkaStreamsConfig["key.serde"] = Serdes.LongSerde::class.java
    dwConfig.kafkaStreamsConfig["value.serde"] = JsonSerde::class.java
    val streamsConfig = StreamsConfig(dwConfig.kafkaStreamsConfig)
    @Suppress("UNCHECKED_CAST")
    val keySerde: Serde<Long> = streamsConfig.keySerde() as Serde<Long>
    @Suppress("UNCHECKED_CAST")
    val valueSerde: Serde<ObjectNode> = streamsConfig.valueSerde() as Serde<ObjectNode>
    return buildStreams(streamsConfig, keySerde, valueSerde)
  }

  abstract fun buildStreams(streamsConfig: StreamsConfig,
                            keySerde: Serde<Long>, valueSerde: Serde<ObjectNode>): KafkaStreams

  fun run() {
    addCloseHook {kafkaStreams.close()}

    // when starting all over again (reprocess data), may need to reset local state
    if (System.getProperty("kafkaStreamsReset", "false").toBoolean()) {
      log.warn("Asked to run local Kafka Streams 'cleanUp' -- not normal!  " +
              "note: auto.offset.reset=" + dwConfig.kafkaStreamsConfig["auto.offset.reset"])
      kafkaStreams.cleanUp()
    } else if (dwConfig.kafkaStreamsConfig["auto.offset.reset"] == "earliest") {
      log.warn("detected 'auto.offset.reset'; you should probably set kafkaStreamsReset=true sys prop")
    }

    val latch = CountDownLatch(1)
    kafkaStreams.setUncaughtExceptionHandler { thread, throwable ->
      log.error(throwable.toString(), throwable)
      thread.interrupt()
      latch.countDown()
    }

    kafkaStreams.start()

    try {
      latch.await() // either interruption will finish off, or error triggered latch
    } finally {
      runCloseHooks()
    }
  }
}