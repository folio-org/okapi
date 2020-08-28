package org.folio.okapi.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;

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
   * @return Future
   */
  public Future<Void> init(Vertx vertx, String mapName) {
    this.vertx = vertx;
    return AsyncMapFactory.<String, String>create(vertx, mapName).compose(res -> {
      this.list = res;
      logger.info("initialized map {} ok", mapName);
      return Future.succeededFuture();
    });
  }

  public Future<Integer> size() {
    return list.size();
  }

  /**
   * Get value from shared map - primary and secondary level keys.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @param fut async result with value if successful
   */
  public void getString(String k, String k2, Handler<ExtendedAsyncResult<String>> fut) {
    getString(k, k2).onComplete(resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, resGet.cause()));
        return;
      }
      String res = resGet.result();
      if (res == null) {
        fut.handle(new Failure<>(ErrorType.NOT_FOUND, k + (k2 == null ? "" : "/" +  k2)));
      } else {
        fut.handle(new Success<>(res));
      }
    });
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
      StringMap oldList = Json.decodeValue(val, StringMap.class);
      stringMap.strings.putAll(oldList.strings);
      return Future.succeededFuture(stringMap.strings.get(k2));
    });
  }

  /**
   * Get values from shared map with primary key.
   * @param k primary-level key
   * @param fut async result with values if successful
   */
  public void getString(String k, Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    getString(k).onComplete(resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, resGet.cause()));
        return;
      }
      Collection<String> res = resGet.result();
      if (res == null) {
        fut.handle(new Failure<>(ErrorType.NOT_FOUND, k));
      } else {
        fut.handle(new Success<>(res));
      }
    });
  }

  /**
   * Get values from shared map with primary key.
   * @param k primary-level key
   * @return future with values (null if not found)
   */
  public Future<Collection<String>> getString(String k) {
    return list.get(k).compose(val -> {
      if (val == null) {
        return Future.succeededFuture(null);
      }
      StringMap map = Json.decodeValue(val, StringMap.class);
      return Future.succeededFuture(map.strings.values());
    });
  }

  /**
   * Get all values from shared map.
   * @param fut async result with values if successful
   */
  public void getKeys(Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    getKeys().onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
      } else {
        fut.handle(new Success<>(res.result()));
      }
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
   * @param fut async result
   */
  public void addOrReplace(boolean allowReplace, String k, String k2, String value,
                           Handler<ExtendedAsyncResult<Void>> fut) {
    addOrReplace(allowReplace, k, k2, value).onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      fut.handle(new Success<>());
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
    return list.get(k).compose(resGet -> {
      String oldVal = resGet;
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
    vertx.setTimer(DELAY, x -> addOrReplace(allowReplace, k, k2, value)
        .onComplete(promise::handle));
    return promise.future();
  }

  public void remove(String k, Handler<ExtendedAsyncResult<Boolean>> fut) {
    remove(k, null, fut);
  }

  /**
   * Remove entry from shared map.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @param fut async result (true if removed)
   */
  public void remove(String k, String k2,
                     Handler<ExtendedAsyncResult<Boolean>> fut) {
    remove(k, k2).onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      if (Boolean.TRUE.equals(res.result())) {
        fut.handle(new Success<>(res.result()));
      } else {
        fut.handle(new Failure<>(ErrorType.NOT_FOUND, k + (k2 == null ? "" : "/" +  k2)));
      }
    });
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
      vertx.setTimer(DELAY, res -> remove(k, k2).onComplete(promise::handle));
      return promise.future();
    }
  }

}
