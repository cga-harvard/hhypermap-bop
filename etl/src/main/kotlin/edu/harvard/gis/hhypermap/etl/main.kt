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

import com.codahale.metrics.MetricRegistry
import io.dropwizard.configuration.ConfigurationFactory
import io.dropwizard.jackson.Jackson
import io.dropwizard.logging.BootstrapLogging
import io.dropwizard.validation.BaseValidator
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.slf4j.LoggerFactory
import java.io.File

val log = LoggerFactory.getLogger("edu.harvard.gis.hhypermap.etl")!!

fun main(args: Array<String>) {
  BootstrapLogging.bootstrap()
  val configFile: File = File(args[0])
  val etlConfig = ConfigurationFactory<EtlConfiguration>(
          EtlConfiguration::class.java,
          BaseValidator.newValidator(),
          Jackson.newObjectMapper(),
          "etl")//sysprop prefix
    .build(configFile)
  val metricRegistry = MetricRegistry()
  etlConfig.loggingConfig.configure(metricRegistry, "etl")
  etlConfig.kafkaStreamsConfig["key.serde"] = Serdes.LongSerde::class.java
  etlConfig.kafkaStreamsConfig["value.serde"] = GenericAvroSerde::class.java

  try {
    doStream(StreamsConfig(etlConfig.kafkaStreamsConfig),
            etlConfig.kafkaSourceTopic!!, etlConfig.kafkaDestTopic!!)
  } finally {
    etlConfig.loggingConfig.stop()
  }
}

fun doStream(streamsConfig: StreamsConfig, sourceTopic: String, destTopic: String) {
  val builder = KStreamBuilder()

  @Suppress("UNCHECKED_CAST")
  val keySerde: Serde<Long> = streamsConfig.keySerde() as Serde<Long>
  @Suppress("UNCHECKED_CAST")
  val valueSerde: Serde<GenericRecord> = streamsConfig.valueSerde() as Serde<GenericRecord>

  builder.stream<Long, GenericRecord>(keySerde, valueSerde, sourceTopic)
          .mapValues(::transform)
          .to(destTopic)

  val streams = KafkaStreams(builder, streamsConfig)
  streams.start()
}

fun transform(record: GenericRecord): GenericRecord = record// TODO transform the record
