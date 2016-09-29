/**
 * Copyright 2016 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.confluent.examples.streams

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsConfig
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Utility functions to make integration testing more convenient.
 */
object IntegrationTestUtils {

  private val UNLIMITED_MESSAGES = -1
  val DEFAULT_TIMEOUT = 30 * 1000L

  /**
   * Returns up to `maxMessages` message-values from the topic.
   * @param topic          Kafka topic to read messages from
   * @param consumerConfig Kafka consumer configuration
   * @param maxMessages    Maximum number of messages to read via the consumer.
   * @return The values retrieved via the consumer.
   */
  fun <K, V> readValues(topic: String, consumerConfig: Properties, maxMessages: Int): List<V> {
    val kvs = readKeyValues<K, V>(topic, consumerConfig, maxMessages)
    return kvs.asSequence().map({ kv -> kv.value }).toList()
  }

  /**
   * Returns as many messages as possible from the topic until a (currently hardcoded) timeout is
   * reached.
   * @param topic          Kafka topic to read messages from
   * @param consumerConfig Kafka consumer configuration
   * @return The KeyValue elements retrieved via the consumer.
   */
  fun <K, V> readKeyValues(topic: String, consumerConfig: Properties): List<KeyValue<K, V>> {
    return readKeyValues(topic, consumerConfig, UNLIMITED_MESSAGES)
  }

  /**
   * Returns up to `maxMessages` by reading via the provided consumer (the topic(s) to read from are
   * already configured in the consumer).
   * @param topic          Kafka topic to read messages from
   * @param consumerConfig Kafka consumer configuration
   * @param maxMessages    Maximum number of messages to read via the consumer
   * @return The KeyValue elements retrieved via the consumer
   */
  fun <K, V> readKeyValues(topic: String, consumerConfig: Properties, maxMessages: Int): List<KeyValue<K, V>> {
    val consumer = KafkaConsumer<K, V>(consumerConfig)
    consumer.subscribe(listOf(topic))
    val pollIntervalMs = 100
    val maxTotalPollTimeMs = 2000
    var totalPollTimeMs = 0
    val consumedValues = ArrayList<KeyValue<K, V>>()
    while (totalPollTimeMs < maxTotalPollTimeMs && continueConsuming(consumedValues.size, maxMessages)) {
      totalPollTimeMs += pollIntervalMs
      val records = consumer.poll(pollIntervalMs.toLong())
      for (record in records) {
        consumedValues.add(KeyValue(record.key(), record.value()))
      }
    }
    consumer.close()
    return consumedValues
  }

  private fun continueConsuming(messagesConsumed: Int, maxMessages: Int): Boolean {
    return maxMessages <= 0 || messagesConsumed < maxMessages
  }

  /**
   * Removes local state stores.  Useful to reset state in-between integration test runs.
   * @param streamsConfiguration Streams configuration settings
   */
  @Throws(IOException::class)
  fun purgeLocalStreamsState(streamsConfiguration: Properties) {
    val path = streamsConfiguration.getProperty(StreamsConfig.STATE_DIR_CONFIG)
    if (path != null) {
      val node = Paths.get(path)
      // Only purge state when it's under /tmp.  This is a safety net to prevent accidentally
      // deleting important local directory trees.
      if (node.toAbsolutePath().startsWith("/tmp")) {
        deleteFileRecursive(node)
      }
    }
  }

  private fun deleteFileRecursive(path: Path) {
    Files.walkFileTree(path, object : FileVisitor<Path> {
      override fun postVisitDirectory(dir: Path, exc: IOException): FileVisitResult {
        Files.delete(dir)
        return FileVisitResult.CONTINUE
      }

      override fun preVisitDirectory(dir: Path,
                            attrs: BasicFileAttributes): FileVisitResult {
        return FileVisitResult.CONTINUE
      }

      override fun visitFile(file: Path,
                    attrs: BasicFileAttributes): FileVisitResult {
        Files.delete(file)
        return FileVisitResult.CONTINUE
      }

      override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
        return FileVisitResult.CONTINUE
      }
    })
  }

  /**
   * @param topic          Kafka topic to write the data records to
   * @param records        Data records to write to Kafka
   * @param producerConfig Kafka producer configuration
   * @param             Key type of the data records
   * @param             Value type of the data records
   */
  @Throws(ExecutionException::class, InterruptedException::class)
  fun <K, V> produceKeyValuesSynchronously(
          topic: String, records: Collection<KeyValue<K, V>>, producerConfig: Properties) {
    val producer = KafkaProducer<K, V>(producerConfig)
    for (record in records) {
      val f = producer.send(
              ProducerRecord(topic, record.key, record.value))
      f.get()
    }
    producer.flush()
    producer.close()
  }

  @Throws(ExecutionException::class, InterruptedException::class)
  fun <V> produceValuesSynchronously(
          topic: String, records: Collection<V>, producerConfig: Properties) {
    val keyedRecords = records.asSequence().map({ record -> KeyValue<Any, V>(null, record) }).toList()
    produceKeyValuesSynchronously(topic, keyedRecords, producerConfig)
  }

  @Throws(InterruptedException::class)
  fun <K, V> waitUntilMinKeyValueRecordsReceived(consumerConfig: Properties,
                                                 topic: String,
                                                 expectedNumRecords: Int): List<KeyValue<K, V>> {

    return waitUntilMinKeyValueRecordsReceived(consumerConfig, topic, expectedNumRecords, DEFAULT_TIMEOUT)
  }

  /**
   * Wait until enough data (key-value records) has been consumed.
   * @param consumerConfig Kafka Consumer configuration
   * @param topic          Topic to consume from
   * @param expectedNumRecords Minimum number of expected records
   * @param waitTime       Upper bound in waiting time in milliseconds
   * @return All the records consumed, or null if no records are consumed
   * @throws InterruptedException
   * @throws AssertionError if the given wait time elapses
   */
  @Throws(InterruptedException::class)
  fun <K, V> waitUntilMinKeyValueRecordsReceived(consumerConfig: Properties,
                                                 topic: String,
                                                 expectedNumRecords: Int,
                                                 waitTime: Long): List<KeyValue<K, V>> {
    val accumData = ArrayList<KeyValue<K, V>>()
    val startTime = System.currentTimeMillis()
    while (true) {
      val readData = readKeyValues<K, V>(topic, consumerConfig)
      accumData.addAll(readData)
      if (accumData.size >= expectedNumRecords)
        return accumData
      if (System.currentTimeMillis() > startTime + waitTime)
        throw AssertionError("Expected " + expectedNumRecords +
                " but received only " + accumData.size +
                " records before timeout " + waitTime + " ms")
      Thread.sleep(Math.min(waitTime, 100L))
    }
  }

  @Throws(InterruptedException::class)
  fun <V> waitUntilMinValuesRecordsReceived(consumerConfig: Properties,
                                            topic: String,
                                            expectedNumRecords: Int): List<V> {

    return waitUntilMinValuesRecordsReceived(consumerConfig, topic, expectedNumRecords, DEFAULT_TIMEOUT)
  }

  /**
   * Wait until enough data (value records) has been consumed.
   * @param consumerConfig Kafka Consumer configuration
   * @param topic          Topic to consume from
   * @param expectedNumRecords Minimum number of expected records
   * @param waitTime       Upper bound in waiting time in milliseconds
   * @return All the records consumed, or null if no records are consumed
   * @throws InterruptedException
   * @throws AssertionError if the given wait time elapses
   */
  @Throws(InterruptedException::class)
  fun <V> waitUntilMinValuesRecordsReceived(consumerConfig: Properties,
                                            topic: String,
                                            expectedNumRecords: Int,
                                            waitTime: Long): List<V> {
    val accumData = ArrayList<V>()
    val startTime = System.currentTimeMillis()
    while (true) {
      val readData = readValues<Any, V>(topic, consumerConfig, expectedNumRecords)
      accumData.addAll(readData)
      if (accumData.size >= expectedNumRecords)
        return accumData
      if (System.currentTimeMillis() > startTime + waitTime)
        throw AssertionError("Expected " + expectedNumRecords +
                " but received only " + accumData.size +
                " records before timeout " + waitTime + " ms")
      Thread.sleep(Math.min(waitTime, 100L))
    }
  }
}