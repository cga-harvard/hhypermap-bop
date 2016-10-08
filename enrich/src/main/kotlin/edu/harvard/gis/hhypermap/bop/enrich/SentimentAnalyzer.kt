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

package edu.harvard.gis.hhypermap.bop.enrich

import edu.harvard.gis.hhypermap.bop.kafkastreamsbase.StreamBase
import org.slf4j.LoggerFactory
import java.io.*
import java.net.Socket

/**
 * A client to a sentiment server.  Thread-safe but not re-entrant.
 * Server is this REPL: https://github.com/dkakkar/Twitter-Sentiment-Classifier
 */
class SentimentAnalyzer (hostColonPort: String) : Closeable {

  private val log = LoggerFactory.getLogger(this.javaClass)

  // todo use a pool of these things so we can be re-entrant, assuming the Kafka Streams
  //   can process us in more than one thread

  private val socket: Socket
  private val writer: PrintWriter
  private val reader: BufferedReader

  private val timer = StreamBase.METRIC_REGISTRY.timer("enrich.calcSentiment")!!

  init {
    // Connect to the sentiment server and wait until READY is read
    val (host, port) = hostColonPort.split(':', ignoreCase=false, limit=2)
    socket = Socket(host, port.toInt())
    reader = socket.inputStream.bufferedReader()

    var isReady = false
    val firstLines = mutableListOf<String>()
    for (line in reader.lineSequence()) {
      log.debug("From sentiment server $socket: $line")
      if (line == "READY") {
        isReady = true
        break
      }
      firstLines += line
      if (firstLines.size > 100) {
        break
      }
    }

    if (!isReady) {
      try {
        socket.close()
      } finally {
        throw RuntimeException("sentiment server failed. Response: " + firstLines.joinToString("\\r"))
      }
    }

    writer = PrintWriter(socket.outputStream)
  }

  fun calcSentiment(text: String) : Sentiment {
    synchronized(this) { // just in case
      return timer.time {
        writer.println(text.replace('\r',' ').replace('\n',' '))
        writer.flush()
        val resultStr = reader.readLine()
        when (resultStr) {
          "1" -> Sentiment.pos
          "0" -> Sentiment.neg
          null -> throw IOException("Unexpected closure of socket from server $socket")
          else -> throw RuntimeException("Unexpected response from server $socket: $resultStr")
        }
      }
    }
  }

  override fun close() {
    socket.close() // note: will in so doing close the reader & writer
  }

  enum class Sentiment { pos, neg }

}