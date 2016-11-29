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

package edu.harvard.gis.hhypermap.bop;

import com.google.common.base.Throwables;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Random;

public class SolrUpdateSlammer {
  private static volatile Throwable failedException;
  // sent to the /update? with params
  private static ModifiableSolrParams updateParams = new ModifiableSolrParams();
  static {
    //updateParams.set("_route_", "RT"); // ensures only one shard (that won't be deleted) manages other shards
    updateParams.set("shardMaxAge", "+10SECOND");
    updateParams.set("docMaxAge", "/SECOND-50SECOND");
  }

  private Random random = new Random(0L);
  private SolrClient client;
  private Instant startTime;
  private long nextShiftBack;
  private long nextRandomMillis;
  private String collection;
  Instant endInstant;


  public static void main(String[] args) throws Exception {

    int queueSize = 1;
    int threadCount = 1;
    try (SolrClient client = newClient("http://localhost:8983/solr", queueSize, threadCount)) {
      SolrUpdateSlammer me = new SolrUpdateSlammer();
      me.client = client;
      me.collection = "bop";
      me.startTime = Instant.now().minus(5, ChronoUnit.MINUTES);
      me.nextRandomMillis = 5000;
      me.nextShiftBack = 1000;
      me.endInstant = Instant.now().plusSeconds(30);
      me.slam();
    }
  }

  private void slam() throws IOException, SolrServerException {
    int docCounter = 0;
    long nanosStart = System.nanoTime();
    for (; Instant.now().isBefore(endInstant); docCounter++) {
      throwIfError();
      Instant time = Instant.now().plusMillis(Math.abs(random.nextLong() % nextRandomMillis) - nextShiftBack);
      SolrInputDocument doc = new SolrInputDocument();
      doc.setField("id", docCounter);
      doc.setField("created_at", Date.from(time));
      sendDoc(doc);

      // update time for next lap:

    }
    client.commit(collection);
    throwIfError();
    long nanosEnd = System.nanoTime();
    System.out.println("Added " + docCounter + " docs in " + Duration.ofNanos(nanosEnd - nanosStart));
    SolrQuery solrQuery = new SolrQuery();
    solrQuery.setQuery("*:*").setRows(0);
    QueryResponse queryResponse = client.query(collection, solrQuery);
    System.out.println("Found " + queryResponse.getResults().getNumFound());
  }

  private void sendDoc(SolrInputDocument doc) throws IOException, SolrServerException {
    UpdateRequest req = new UpdateRequest();
    req.add(doc);
    req.setParams(updateParams);
    req.setCommitWithin(1000);
    req.process(client, collection);
  }

  private void throwIfError() {
    if (failedException != null) {
      if (failedException instanceof SolrException) {
        SolrException exception = (SolrException) failedException;
        System.err.println(exception.getMetadata());
      }
      throw Throwables.propagate(failedException);
    }
  }

  private static SolrClient newClient(final String solrServerUrl, int queueSize, final int threadCount) {
    if (queueSize == 1 && threadCount == 1) {
      return new HttpSolrClient.Builder(solrServerUrl).build();
    }
    return new ConcurrentUpdateSolrClient(solrServerUrl, queueSize, threadCount) {
      @Override
      public void handleError(Throwable ex) {
        failedException = ex;
      }
    };
  }
}
