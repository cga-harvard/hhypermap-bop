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

import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import java.util.concurrent.CountDownLatch

/**
 * A DropWizard oriented Application base class that uses Kafka Streams.  It's not a typical
 * DropWizard app since there is no web interface.
 */
abstract class DwKafkaStreamsApplication<C : DwKafkaStreamsConfiguration>
    (mainArgs: Array<String>, cClazz: Class<C>)
    : DwApplication<C>(mainArgs, cClazz) {

  val kafkaStreams: KafkaStreams
  
  init {
    dwConfig.kafkaStreamsConfig["key.serde"] = Serdes.LongSerde::class.java
    dwConfig.kafkaStreamsConfig["value.serde"] = JsonSerde::class.java
    val streamsConfig = StreamsConfig(dwConfig.kafkaStreamsConfig)
    @Suppress("UNCHECKED_CAST")
    val keySerde = streamsConfig.keySerde() as Serde<Long>
    @Suppress("UNCHECKED_CAST")
    val valueSerde = streamsConfig.valueSerde() as Serde<TreeNode>
    kafkaStreams = buildStreams(streamsConfig, keySerde, valueSerde)
  }

  abstract fun buildStreams(streamsConfig: StreamsConfig,
                            keySerde: Serde<Long>, valueSerde: Serde<TreeNode>): KafkaStreams

  fun run() {
    addCloseHook {kafkaStreams.close()}

    // when starting all over again (reprocess data), may need to reset local state
    if (System.getProperty("kafkaStreamsReset", "false").toBoolean()) {
      log.warn("Asked to run local Kafka Streams 'cleanUp' -- not normal!  " +
              "note: auto.offset.reset=" + dwConfig.kafkaStreamsConfig["auto.offset.reset"])
      kafkaStreams.cleanUp()
    } else if (dwConfig.kafkaStreamsConfig["auto.offset.reset"] == "earliest") {
      log.warn("detected 'auto.offset.reset'='earliest'; you should probably set kafkaStreamsReset=true sys prop 1st time")
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