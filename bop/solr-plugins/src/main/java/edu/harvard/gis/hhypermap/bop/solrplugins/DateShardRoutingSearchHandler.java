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

package edu.harvard.gis.hhypermap.bop.solrplugins;

import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.util.DateMathParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class DateShardRoutingSearchHandler extends SearchHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String SHARD_NAME_PREFIX = "shard-";
  private static final String SHARD_RT_NAME = "RT";
  public static final String START_PARAM = "hcga.start";
  public static final String END_PARAM = "hcga.end";

  //nocommit TODO initialize from the URP's same logic. Ideally save in collection.
  final DateTimeFormatter shardDateTimeFormatter =
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH_mm_ss'Z'", Locale.ROOT)
          .withZone(ZoneId.of("UTC"));

  @Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
    addShardsParamIfWeCan(req);
    super.handleRequestBody(req, rsp);
  }

  private void addShardsParamIfWeCan(SolrQueryRequest req) {
    CoreDescriptor coreDescriptor = req.getCore().getCoreDescriptor();
    CoreContainer coreContainer = coreDescriptor.getCoreContainer();
    if (!coreContainer.isZooKeeperAware()) {
      return;
    }
    if (!req.getParams().getBool("distrib", true)) {
      return;
    }
    final String shards = req.getParams().get(ShardParams.SHARDS);
    if (shards != null) {
      return; // we already have the shards
    }

    String startStrParam = req.getParams().get(START_PARAM);
    String endStrParam = req.getParams().get(END_PARAM);

    Instant startInst =  // null means open-ended ('*')
            startStrParam == null ? null : DateMathParser.parseMath(null, startStrParam).toInstant();
    Instant endInst =
            endStrParam == null ? null : DateMathParser.parseMath(null, endStrParam).toInstant();

    if (startInst == null && endInst == null) {
      return;
    }

    ZkController zkController = coreContainer.getZkController();
    String collection = req.getParams().get("collection", coreDescriptor.getCollectionName());
    List<Slice> slices = getOrderedSlices(zkController, collection);
    if (slices.size() <= 1) {
      return;
    }

    List<String> routeShardNames = new ArrayList<>(); // the result
    boolean findingStart = (startInst != null);
    String prevShardName = null;
    Instant prevShardStartKey = null;

    for (Slice slice : slices) {
      String name = slice.getName();
      Instant shardStartKey = parseStartKeyFromShardName(name);
      if (prevShardStartKey != null && prevShardStartKey.isAfter(shardStartKey)) {
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "shards not in order? " + slice);
      }
      if (findingStart) {
        // As we advance shards, is this one finally > the 'start'? If so, accept it.
        if (shardStartKey.isAfter(startInst)) {
          if (prevShardName != null) {
            routeShardNames.add(prevShardName);
          }
          findingStart = false; // thus findingEnd
        }
      }
      if (!findingStart && endInst == null) { // take all the remainder since 'end' is null
        routeShardNames.add(name);
      } else if (!findingStart) { // findingEnd
        if (shardStartKey.isAfter(endInst)) {
          break;
        }
        routeShardNames.add(name);
      }

      prevShardName = name;
      prevShardStartKey = shardStartKey;
    }
    if (findingStart) {
      routeShardNames.add(prevShardName);
    }

    if (routeShardNames.size() == slices.size()) {
      return;
    }

    ModifiableSolrParams params = new ModifiableSolrParams(req.getParams());
    String shardsValue = StrUtils.join(routeShardNames, ',');
    params.set(ShardParams.SHARDS, shardsValue);
    req.setParams(params);
    log.debug("Set shards: {}", shardsValue);
  }

  List<Slice> getOrderedSlices(ZkController zkController, String collection) {
    DocCollection coll = zkController.getClusterState().getCollection(collection);
    //TODO cache sorted for same coll ?
    List<Slice> slices = new ArrayList<>(coll.getSlices());
    slices.removeIf((s) -> s.getName().equals(SHARD_RT_NAME));
    // assumption: shard names sort in date order
    slices.sort(Comparator.comparing(Slice::getName));
    return slices;
  }

  Instant parseStartKeyFromShardName(String shard) {
    if (!shard.startsWith(SHARD_NAME_PREFIX)) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
              "shard name expected to start with " + SHARD_NAME_PREFIX + " but got " + shard);
    }
    // chop off prefix
    shard = shard.substring(SHARD_NAME_PREFIX.length());
    try {
      return shardDateTimeFormatter.parse(shard, Instant::from);
    } catch (DateTimeParseException e) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "couldn't parse date from shard " + shard, e);
    }
  }
}
