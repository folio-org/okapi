package org.folio.okapi.util;

import io.micrometer.core.instrument.Timer;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.GenericCompositeFuture;

public class LockedTypedMap1<T> extends LockedStringMap {

  private final Class<T> clazz;

  public LockedTypedMap1(Class<T> c) {
    this.clazz = c;
  }

  public Future<Void> add(String k, T value) {
    String json = Json.encode(value);
    return addOrReplace(false, k, null, json);
  }

  public Future<Void> put(String k, T value) {
    String json = Json.encode(value);
    return addOrReplace(true, k, null, json);
  }

  /**
   * Get and deserialize to type from shared map.
   * @param k key
   * @return result with value if successful
   */
  public Future<T> getNotFound(String k) {
    return get(k).compose(res -> {
      if (res == null) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, k));
      }
      return Future.succeededFuture(res);
    });
  }

  /**
   * Get and deserialize to type from shared map.
   * @param k key
   * @return future with value if found (null if not found)
   */
  public Future<T> get(String k) {
    return getString(k, null).compose(res -> {
      if (res == null) {
        return Future.succeededFuture(null);
      }
      Timer.Sample sample = MetricsHelper.getTimerSample();
      T t = JsonDecoder.decode(res, clazz);
      MetricsHelper.recordCodeExecutionTime(sample, "LockedTypedMap1.get.decodeValue");
      return Future.succeededFuture(t);
    });
  }

  /**
   * Get all records in the map. Returns them in a LinkedHashMap, so they come
   * in well defined order.
   *
   * @return fut callback with the result, or some failure.
   */
  public Future<LinkedHashMap<String, T>> getAll() {
    return getKeys().compose(keys -> {
      LinkedHashMap<String, T> results = new LinkedHashMap<>();
      List<Future<Void>> futures = new LinkedList<>();
      for (String key : keys) {
        futures.add(getString(key, null).compose(res -> {
          Timer.Sample sample = MetricsHelper.getTimerSample();
          T t = JsonDecoder.decode(res, clazz);
          MetricsHelper.recordCodeExecutionTime(sample, "LockedTypedMap1.getAll.decodeValue");
          results.put(key, t);
          return Future.succeededFuture();
        }));
      }
      return GenericCompositeFuture.all(futures).map(results);
    });
  }

}
