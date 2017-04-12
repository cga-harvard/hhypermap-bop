/*
 * Copyright 2017 President and Fellows of Harvard College
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

import ch.qos.logback.classic.Level
import io.dropwizard.logging.BootstrapLogging
import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.impl.ZkClientClusterStateProvider
import org.apache.solr.client.solrj.request.AbstractUpdateRequest
import org.apache.solr.client.solrj.request.LukeRequest
import org.apache.solr.client.solrj.request.UpdateRequest
import org.apache.solr.common.cloud.Replica
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.thread

val log = LoggerFactory.getLogger("edu.harvard.gis.hhypermap.bop.ingest")!!

fun main(args: Array<String>) {
  val zkHost = args[0]
  val collectionName = args[1]

  BootstrapLogging.bootstrap(Level.INFO) // Dropwizard util for Logback

  val docCollection = CloudSolrClient.Builder().withZkHost(zkHost).build().use { solrClient ->
    solrClient.connect()
    (solrClient.clusterStateProvider as ZkClientClusterStateProvider).getState(collectionName)?.get()
  } ?: throw Exception("$collectionName collection doesn't exist")

  val replicasByNode = docCollection.replicas.groupBy { it.nodeName }
  val threads = mutableListOf<Thread>()
  for ((node, replicasNotSorted) in replicasByNode) {
    val replicas = replicasNotSorted.sortedByDescending { it.name }
    threads.add(thread(name="Merge for node $node") {
      val replicaNames = replicas.map { it.name }.joinToString(",")
      log.info("Will optimize these replicas in sequence: $replicaNames}")
      replicas.forEach(::optimizeReplica)
    })
  }
  for (thread in threads) {
    thread.join()
  }
}

private fun optimizeReplica(replica: Replica) {
  val logPrefix = "${replica.nodeName}: Replica ${replica.name}:"
  val targetSegments = if (replica.coreUrl.contains("2014")) 16 else 1//nocommit hack

  HttpSolrClient.Builder(replica.coreUrl).build().use { replicaClient ->
    val segCount = getSegmentCount(replicaClient)
    if (segCount <= targetSegments) {
      log.info("$logPrefix has $segCount segment(s). Continuing to next.")
      return
    }
    log.info("$logPrefix About to optimize $segCount segment(s) to $targetSegments at ${replica.coreUrl}")
    try {
      val elapsedDuration = UpdateRequest()
              .setAction(AbstractUpdateRequest.ACTION.OPTIMIZE, false, false, targetSegments)
              // Make local (this shard only)
              .apply { setParam("commit_end_point", "true") } //hack; see Solr DistributedUpdateProcessor
              .process(replicaClient)
              .elapsedTime.let { Duration.ofMillis(it) }
      val finalSegmentCount = getSegmentCount(replicaClient)
      log.info("$logPrefix Optimize finished in $elapsedDuration final segment count $finalSegmentCount")
    } catch(e: Exception) {
      log.error("$logPrefix $e", e)
      return// TODO catch timeout and loop till until see 1 segment? How do we know a merge is in progress?
    }
  }
}

private fun getSegmentCount(replicaClient: HttpSolrClient?) = LukeRequest().process(replicaClient).indexInfo["segmentCount"] as Int