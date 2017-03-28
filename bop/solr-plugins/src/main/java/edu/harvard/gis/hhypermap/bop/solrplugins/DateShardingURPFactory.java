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

package edu.harvard.gis.hhypermap.bop.solrplugins;

import com.google.common.base.Throwables;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.DistributedUpdateProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;
import org.apache.solr.util.DateMathParser;
import org.apache.solr.util.TimeZoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.apache.solr.common.params.ShardParams._ROUTE_;

/**
 * Routes docs to a date based shard, possibly creating new shards and removing old ones on the
 * fly.  "Route" means to set the special field "_route_" to the name of the shard, after which
 * {@link DistributedUpdateProcessor} will know what to do.  This special field should be
 * removed later, as it's not intended to be stored.  The collection needs to be created with the
 * "implicit" router instead of the hash based router.
 * A variety of parameters configure the date duration of each shard, and the maximum age of docs
 * to keep overall. Shards are always created adjacent to an existing shard, and the start date of
 * the shard is incorporated into the shard's name.
 *
 * Limitations: (and things that might be improved)
 * <ul>
 *   <li>A collection must first be created with either a special "RT" shard or knowledge of
 *   what a correct shard name should be. This limitation is because this functionality
 *   here is an URP which implies that you can already get the document to a shard
 *   (chicken and egg problem).</li>
 *
 *   <li>Ideally pass the param {@code _route_=RT} to the /update url from a client sending updates.
 *   And furthermore only send updates to the shard with the RT leader.  If not done, there might
 *   be noisy log messages if multiple instances of this URP try and do shard management tasks.
 *   Worse if a shard about to be deleted is still receiving updates, it's deletion might be
 *   delayed, possibly causing problems if there aren't enough nodes with ample room for new
 *   shards (maxShardsPerNode).</li>
 *
 *   <li>Changing the params of an existing collection should be done when no indexing is occurring.
 *   TZ should not be changed.  shardMaxAge can be increased, but only decreased at a time when new
 *   docs should still go to that lead shard.</li>
 *
 *   <li>Ideally, shards should be created asynchronously in advance to avoid delaying the first
 *   docs of a shard.  This may act as a canary if it fails, giving time to fix it.</li>
 * </ul>
 *
 */
// TODO It would be nice to create new shards by size (e.g. cross a doc count threshold).
//     The design here tries to allow for that as a future possibility, since there is no shard END
//     date time in a shard's name.  But how do we do that given many URP instances / replicas?
//     Perhaps the shard leader's URP can singularly make this determination, locking, committing,
//     examining the last date/time that made it, then creating a new shard with the next possible
//     time, then setting the route to it for the current doc.  But do in a way that can be
//     asynchronous.
//TODO some of the config ought to go into the cluster state router.* metadata
//TODO consider shard index in shard name to avoid races, troubleshoot problems
//TODO decouple this logic so that people could use in a client as an alternative to an URP.
public class DateShardingURPFactory extends UpdateRequestProcessorFactory {

  public static final String PARAM_DOC_MAX_AGE = "docMaxAge";
  public static final String PARAM_SHARD_MAX_AGE = "shardMaxAge";
  public static final String PARAM_MAX_FUTURE_MILLIS = "docMaxFutureMillis";
  public static final String PARAM_ROUTER_FIELD = "router.field";

  private static final String SHARD_NAME_PREFIX = "shard-";
  private static final String SHARD_RT_NAME = "RT";

  // typical formatter; precision a day or more course
  static final DateTimeFormatter DAY_SHARD_NAME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ROOT);
  // if precision is sub-day, use this one (rarely used).  Note '_' not '-'.
  static final DateTimeFormatter SECOND_SHARD_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH_mm_ss'Z'", Locale.ROOT);

  @Override
  public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp, UpdateRequestProcessor next) {
    return new DateShardingURP(req, next);
  }

  private static class DateShardingURP extends UpdateRequestProcessor {

    static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    final ZkController zkController;
    final String collection;
    final CoreDescriptor coreDescriptor;

    final DateMathParser dateMathParser;
    final DateTimeFormatter shardDateTimeFormatter;
    final String routeField;
    final String docMaxAgeDateMath;
    final String shardMaxAgeDateMath; // must match: "NOW", "/", someUnit, "+", digits, someUnit
    final long maxFutureMillis;
    

    DateShardingURP(SolrQueryRequest req, UpdateRequestProcessor next) {
      super(next);
      // Configure from params:
      SolrParams params = req.getParams();

      TimeZone timeZone = TimeZoneUtils.getTimeZone(params.get(CommonParams.TZ));
      if (timeZone == null) {
        timeZone = TimeZone.getTimeZone("UTC");
      }
      dateMathParser = new DateMathParser(timeZone);

      docMaxAgeDateMath = params.get(PARAM_DOC_MAX_AGE);
      if (docMaxAgeDateMath == null) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Required param: " + PARAM_DOC_MAX_AGE);
      }
      checkDateMath(docMaxAgeDateMath);

      shardMaxAgeDateMath = params.get(PARAM_SHARD_MAX_AGE);
      if (shardMaxAgeDateMath == null) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Required param: " + PARAM_SHARD_MAX_AGE);
      }
      checkDateMath(shardMaxAgeDateMath);

      //TODO add param for this?
      if (shardMaxAgeDateMath.endsWith("DAY") || shardMaxAgeDateMath.endsWith("DAYS")) {
        shardDateTimeFormatter = DAY_SHARD_NAME_FORMATTER.withZone(timeZone.toZoneId()); // more succinct
      } else {
        shardDateTimeFormatter = SECOND_SHARD_NAME_FORMATTER.withZone(ZoneId.of("UTC")); // to the second
      }

      maxFutureMillis = params.getLong(PARAM_MAX_FUTURE_MILLIS, 10*60*1000L);//10min default

      routeField = params.get(PARAM_ROUTER_FIELD);
      if (routeField == null) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Required param: " + PARAM_ROUTER_FIELD);
      }

      coreDescriptor = req.getCore().getCoreDescriptor();
      zkController = coreDescriptor.getCoreContainer().getZkController();
      collection = coreDescriptor.getCollectionName();
    }

    private void checkDateMath(String dateMath) {
      DateMathParser.parseMath(new Date(), "NOW" + dateMath);
    }

    @Override
    public void processAdd(AddUpdateCommand cmd) throws IOException {
      SolrInputDocument sdoc = cmd.getSolrInputDocument();
      if (sdoc.getFieldValue(_ROUTE_) != null) {
        log.warn("Unexpected; this doc already has " + _ROUTE_ + "; ignoring it. doc=" + sdoc);
      }
      // Determine the key (e.g. date of the document)
      Object keyObj = sdoc.getFieldValue(routeField);
      if (keyObj == null) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Required field (the route field): " + routeField);
      }
      Instant key = parseKey(keyObj);

      if (precedesMaxAge(key)) {// IGNORE DOCUMENT BECAUSE IT'S TOO OLD
        if (log.isDebugEnabled()) {
          log.debug("Dropping old doc id=" + cmd.getPrintableId() + " key=" + key);
        }
        return;
      } else if (exceedsTimeTravelAge(key)) {// THROW EXCEPTION IF IT'S FROM THE FUTURE
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                "Doc date too far in future?" +
                        " id=" + cmd.getPrintableId() +
                        " key=" + key +
                        " now=" + Instant.now());
      }

      String shard = findShardForKeyAndCreateShardsIfNeeded(key);

      sdoc.setField(_ROUTE_, shard, 1.0f);
      // DistributedURP (technically ImplicitDocRouter) will see it and know what to do

      super.processAdd(cmd);
    }

    Instant parseKey(Object keyObj) {
      if (keyObj instanceof Instant) {
        return (Instant) keyObj;
      } else if (keyObj instanceof Date) {
        return ((Date)keyObj).toInstant();
      } else if (keyObj instanceof String) {
        return Instant.parse((String) keyObj);
      } else {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Don't know how to parse " + keyObj.getClass() +" as Date/Time");
      }
    }

    /** Calculates the shard this key belongs in, while creating it and necessary adjacent shards
     * as needed. Old shards are removed.  Returns the target shard name. */
    String findShardForKeyAndCreateShardsIfNeeded(Instant key) {
      //      return coll.getRouter().getTargetSlice(id, doc, route, req.getParams(), coll);
      List<Slice> slices = getOrderedSlices();

      slices = deleteOldShards(slices);

      slices = createFirstShardIfNeeded(slices);

      assert !slices.isEmpty(); // thus if the loop finishes, guaranteed to have prevShardName
      String prevShardName = null;
      Instant prevShardStartKey = null;
      //TODO loop front to back instead
      for (Slice slice : slices) {
        String name = slice.getName();
        Instant shardStartKey = parseStartKeyFromShardName(name);
        if (prevShardStartKey != null && prevShardStartKey.isAfter(shardStartKey)) {
          throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "shards not in order? " + slice);
        }
        if (key.isBefore(shardStartKey)) { // before this shard
          if (prevShardName != null) {
            return prevShardName;
          } else {
            log.warn("Highly unusual: we're creating new date based shards from the back." +
                    " Perhaps the max doc age was increased?");
            //TODO should we even do this?  If not then error or drop the doc?
            String backwardsDateMath = shardMaxAgeDateMath.replaceFirst("\\+", "-");
            return createShards(calculateShardStartSeries(shardStartKey, backwardsDateMath, key));
          }
        }
        prevShardName = name;
        prevShardStartKey = shardStartKey;
      }

      // Are we in the most recent shard?
      if (key.isBefore(parseDateMath(prevShardStartKey, shardMaxAgeDateMath))) {
        //TODO save off the range of this shard to optimize selection of next doc
        return prevShardName;
      }

      return createShards(calculateShardStartSeries(prevShardStartKey, shardMaxAgeDateMath, key));
    }

    List<Slice> createFirstShardIfNeeded(List<Slice> slices) {
      if (slices.isEmpty()) {
        // If there are no slices, create one.

        // To avoid distributed race conditions, this must be done exclusively.
        //   Solution: We insist that this shard be the RT leader.
        if (!isLeader()) {
          // TODO route to the leader and ensure this URP intercepts there.  How?
          //  Alternatively could use a Zk lock but don't want to add Curator dependency or code by hand.
          throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                  "Cannot create first time based shard unless this doc is sent to the " + SHARD_RT_NAME + " leader");
        }
        //   and synchronize since concurrent updates could be coming in
        synchronized (coreDescriptor) {
          slices = getOrderedSlices();
          if (slices.isEmpty()) { // double-check lock idiom; prevent races
            // Create shard anchored on the max age (relative to 'now').
            // TODO add option to have the initial shard be based on this doc's time instead
            Instant shardStartKey = parseDateMath(Instant.now(), docMaxAgeDateMath);
            createShards(Collections.singletonList(shardStartKey));
            slices = getOrderedSlices();
          }
        }
      }
      return slices;
    }

    boolean isLeader() {
      Replica leaderReplica = zkController.getZkStateReader().getLeader(collection, SHARD_RT_NAME);
      return leaderReplica.getName().equals(
              coreDescriptor.getCloudDescriptor().getCoreNodeName());
    }

    List<Slice> getOrderedSlices() {
      DocCollection coll = zkController.getClusterState().getCollection(collection);
      //TODO cache sorted for same coll ?
      List<Slice> slices = new ArrayList<>(coll.getSlices());
      slices.removeIf((s) -> s.getName().equals(SHARD_RT_NAME));
      // assumption: shard names sort in date order
      slices.sort(Comparator.comparing(Slice::getName));
      return slices;
    }

    List<Instant> calculateShardStartSeries(Instant shardStartKey, String dateMath, Instant targetKey) {
      boolean forward = !shardStartKey.isAfter(targetKey); // note: equal will be considered forward
      List<Instant> results = new ArrayList<>();
      Instant prevShardStartKey = shardStartKey;
      while (true) {
        Instant nextStartKey = parseDateMath(prevShardStartKey, dateMath);
        if (forward ^ nextStartKey.isAfter(prevShardStartKey)) { // XOR
          throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Date math ordering error?: " + dateMath);
        }
        if (!forward ^ targetKey.isBefore(nextStartKey)) { // XOR
          if (!forward) { // iff backwards, we need to create this shard at the end condition.
            results.add(nextStartKey);
          }
          return results;
        }

        results.add(nextStartKey);
        prevShardStartKey = nextStartKey;
      }
    }

    /** Creates a series of shards with the specified shardStartKeys. Returns the last shard name. */
    String createShards(List<Instant> shardStartKeys) {
      if (shardStartKeys.isEmpty()) {
        throw new IllegalArgumentException("No shards to create");
      }
      // lock to minimize concurrent races / logged errors
      synchronized (coreDescriptor) {
        String shardName = null;
        for (Instant shardStartKey : shardStartKeys) {
          shardName = formatShardNameFromStartKey(shardStartKey);
          createShardIfDoesNotExist(shardName);
        }
        return shardName;// not null
      }
    }

    void createShardIfDoesNotExist(String shardName) {
      final int MAX_ATTEMPTS = 3;
      Exception throwMe = null;
      for (int attempt = 1; true; attempt++) {
        if (zkController.getClusterState().getCollection(collection).getSlice(shardName) != null) {
          log.info("Was about to create shard {} but it already exists.", shardName);
          return; // SUCCESS
        } else if (throwMe != null) {
          break;
        } else {
          try (SolrClient solrClient = new HttpSolrClient.Builder(zkController.getBaseUrl()).build()) {
            CollectionAdminRequest.createShard(collection, shardName).process(solrClient);
            return; // SUCCESS!
          } catch (Exception e) {
            if (attempt >= MAX_ATTEMPTS || e.getMessage().contains("maxShardsPerNode")) {
              throwMe = e;
            } else {
              log.debug("Failed to create shard " + shardName + "; we'll try again...", e);
            }
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e1) {
              Thread.currentThread().interrupt();
              throwMe = e;
              break;
            }
            // note: perhaps it was created concurrently? If so; we'll return success next loop.
          }
        }
      }
      Throwables.propagateIfPossible(throwMe);
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
              "Error creating shard " + shardName + ": " + throwMe.toString(), throwMe);
    }

    List<Slice> deleteOldShards(List<Slice> slices) {
      // assume ordered old to new.
      boolean unchanged = true;
      for (int sliceIdx = 0; sliceIdx < slices.size(); sliceIdx++) {
        String shardName = slices.get(sliceIdx).getName();
        // next slice's start time, minus 1 millis, is this shard's end time.
        Instant shardEndTime;
        if (sliceIdx == slices.size() - 1) { // the leading shard
          Instant shardStartKey = parseStartKeyFromShardName(shardName);
          shardEndTime = parseDateMath(shardStartKey, shardMaxAgeDateMath);
        } else {
          shardEndTime = parseStartKeyFromShardName(slices.get(sliceIdx + 1).getName()).minusMillis(1L);
        }
        if (!precedesMaxAge(shardEndTime)) {
          break; // STOP; this shard has not expired, and thus no shards following it have either
        }
        // DELETE SHARD
        if (shardName.equals(getThisShardName())) {
          log.warn("Don't send docs to old shards (" + shardName + "). It can delay their timely deletion.");
          //break; // although we could abort; we'll asynchronously request deletion below
        }
        log.info("Deleting expired shard {}", shardName);
        Exception e = null;
        try (SolrClient solrClient = new HttpSolrClient.Builder(zkController.getBaseUrl()).build()) {
          unchanged = false;
          // Do this but don't block forever; such as in case there are some long running update
          //  streams, including maybe this very request (delete same shard?)
          String requestId = CollectionAdminRequest.deleteShard(collection, shardName).processAsync(solrClient);
          CollectionAdminRequest.waitForAsyncRequest(requestId, solrClient, 10);//wait up to 10 secs
        } catch (Exception _e) {
          e = _e;
        }
        if (zkController.getClusterState().getCollection(collection).getSlice(shardName) != null) {
          if (e != null) {
            log.warn(e.toString(), e);
          } else {
            log.warn("deleteShard failed or timed out");
          }
          break;// abort; don't re-throw
        } else { // successfully removed
          if (e != null) {
            log.info("Ignoring concurrent delete shard failure; it's been removed: " + e.toString());
            log.debug(e.toString(), e);
          }
        }
      }
      return unchanged ? slices : getOrderedSlices();
    }

    String getThisShardName() {
      return coreDescriptor.getCloudDescriptor().getShardId();
    }

    //         Date stuff:

    boolean precedesMaxAge(Instant key) {
      return key.isBefore(parseDateMath(Instant.now(), docMaxAgeDateMath));
    }

    boolean exceedsTimeTravelAge(Instant key) {
      return key.isAfter(Instant.now().plusMillis(maxFutureMillis));
    }

    Instant parseDateMath(Instant from, String dateMath) {
      dateMathParser.setNow(Date.from(from));
      if (dateMath.startsWith("NOW")) {
        dateMath = dateMath.substring(3);
      }
      try {
        return dateMathParser.parseMath(dateMath).toInstant();
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    }

    String formatShardNameFromStartKey(Instant shardStartKey) {
      return SHARD_NAME_PREFIX + shardDateTimeFormatter.format(shardStartKey);
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
}
