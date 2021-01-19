package org.folio.okapi.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.shareddata.AsyncMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;

public class LockedStringMap {

  static class StringMap {

    @JsonProperty
    final Map<String, String> strings = new LinkedHashMap<>();
  }

  private AsyncMap<String, String> list = null;
  private Vertx vertx = null;
  private static final int DELAY = 10; // ms in recursing for retry of map
  protected final Logger logger = OkapiLogger.get();
  private final Messages messages = Messages.getInstance();

  /**
   * Initialize a shared map.
   * @param vertx Vert.x handle
   * @param mapName name of shared map
   * @param local true to force local map even if clustered
   * @return Future
   */
  public Future<Void> init(Vertx vertx, String mapName, boolean local) {
    this.vertx = vertx;
    return AsyncMapFactory.<String, String>create(vertx, mapName, local).compose(res -> {
      this.list = res;
      logger.info("initialized map {} ok", mapName);
      return Future.succeededFuture();
    });
  }

  public Future<Integer> size() {
    return list.size();
  }

  /**
   * Clear map.
   * @return async result.
   */
  public Future<Void> clear() {
    if (list == null) {
      return Future.succeededFuture();
    }
    return list.clear();
  }

  /**
   * Get value from shared map - primary and secondary level keys.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @return future with value (null if not found)
   */
  public Future<String> getString(String k, String k2) {
    return list.get(k).compose(val -> {
      if (k2 == null || val == null) {
        return Future.succeededFuture(val);
      }
      StringMap stringMap = new StringMap();
      Timer.Sample sample = MetricsHelper.getTimerSample();
      StringMap oldList = Json.decodeValue(val, StringMap.class);
      MetricsHelper.recordCodeExecutionTime(sample, "LockedStringMap.getString.decodeValue");
      stringMap.strings.putAll(oldList.strings);
      return Future.succeededFuture(stringMap.strings.get(k2));
    });
  }

  /**
   * Get values from shared map with primary key.
   * @param k primary-level key
   * @return future with values (null if not found)
   */
  public Future<Collection<String>> getPrefix(String k) {
    return list.get(k).compose(val -> {
      if (val == null) {
        return Future.succeededFuture(null);
      }
      StringMap map = Json.decodeValue(val, StringMap.class);
      return Future.succeededFuture(map.strings.values());
    });
  }

  /**
   * Get all keys from shared map (sorted).
   * @return Future with sorted keys
   */
  public Future<Collection<String>> getKeys() {
    return list.keys().compose(res -> {
      List<String> s = new ArrayList<>(res);
      java.util.Collections.sort(s);
      return Future.succeededFuture(s);
    });
  }

  /**
   * Update value in shared map.
   * @param allowReplace true: both insert and replace; false: insert only
   * @param k primary-level key
   * @param k2 secondary-level key
   * @param value new value
   * @return fut async result
   */
  public Future<Void> addOrReplace(boolean allowReplace, String k, String k2, String value) {
    return list.get(k).compose(oldVal -> {
      String newVal;
      if (k2 == null) {
        newVal = value;
      } else {
        StringMap smap = new StringMap();
        if (oldVal != null) {
          StringMap oldList = Json.decodeValue(oldVal, StringMap.class);
          smap.strings.putAll(oldList.strings);
        }
        if (!allowReplace && smap.strings.containsKey(k2)) {
          return Future.failedFuture(messages.getMessage("11400", k2));
        }
        smap.strings.put(k2, value);
        newVal = Json.encodePrettily(smap);
      }
      if (oldVal == null) { // new entry
        return list.putIfAbsent(k, newVal).compose(resPut -> {
          if (resPut == null) {
            return Future.succeededFuture();
          }
          // Someone messed with it, try again
          return addOrReplace2(allowReplace, k, k2, value);
        });
      } else { // existing entry, put and retry if someone else messed with it
        return list.replaceIfPresent(k, oldVal, newVal).compose(resRepl -> {
          if (Boolean.TRUE.equals(resRepl)) {
            return Future.succeededFuture();
          }
          return addOrReplace2(allowReplace, k, k2, value);
        });
      }
    });
  }

  private Future<Void> addOrReplace2(boolean allowReplace, String k, String k2, String value) {
    Promise<Void> promise = Promise.promise();
    vertx.setTimer(DELAY, x -> addOrReplace(allowReplace, k, k2, value).onComplete(promise));
    return promise.future();
  }

  public Future<Void> removeNotFound(String k) {
    return removeNotFound(k, null);
  }

  /**
   * Remove entry from shared map.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @return fut async result (Returns NotFound if k/k2 is not removed)
   */
  public Future<Void> removeNotFound(String k, String k2) {
    return remove(k, k2).compose(res -> {
      if (Boolean.TRUE.equals(res)) {
        return Future.succeededFuture();
      } else {
        return Future.failedFuture(
            new OkapiError(ErrorType.NOT_FOUND, k + (k2 == null ? "" : "/" +  k2)));
      }
    });
  }

  public Future<Boolean> remove(String k) {
    return remove(k, null);
  }

  /**
   * Remove entry from shared map.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @return future with result TRUE if deleted; FALSE if not found
   */
  public Future<Boolean> remove(String k, String k2) {
    return list.get(k).compose(val -> {
      if (val == null) {
        return Future.succeededFuture(false);
      }
      StringMap stringMap = new StringMap();
      if (k2 != null) {
        stringMap = Json.decodeValue(val, StringMap.class);
        if (!stringMap.strings.containsKey(k2)) {
          return Future.succeededFuture(false);
        }
        stringMap.strings.remove(k2);
      }
      if (stringMap.strings.isEmpty()) {
        return list.removeIfPresent(k, val).compose(result -> remove2(result, k, k2));
      } else { // list was not empty, remove value
        String newVal = Json.encodePrettily(stringMap);
        return list.replaceIfPresent(k, val, newVal).compose(result -> remove2(result, k, k2));
      }
    });
  }

  private Future<Boolean> remove2(Boolean result, String k, String k2) {
    if (Boolean.TRUE.equals(result)) {
      return Future.succeededFuture(true);
    } else {
      Promise<Boolean> promise = Promise.promise();
      vertx.setTimer(DELAY, res -> remove(k, k2).onComplete(promise));
      return promise.future();
    }
  }

}
