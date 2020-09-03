package org.folio.okapi.util;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;


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
   * @param fut result with value if successful
   */
  public void getNotFound(String k, Handler<ExtendedAsyncResult<T>> fut) {
    get(k).onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      if (res.result() == null) {
        fut.handle(new Failure<>(ErrorType.NOT_FOUND, k));
        return;
      }
      fut.handle(new Success<>(res.result()));
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
      return Future.succeededFuture(Json.decodeValue(res, clazz));
    });
  }

  /**
   * Get all records in the map. Returns them in a LinkedHashMap, so they come
   * in well defined order.
   *
   * @param fut callback with the result, or some failure.
   */
  public void getAll(Handler<ExtendedAsyncResult<LinkedHashMap<String, T>>> fut) {
    getKeys().compose(keys -> {
      LinkedHashMap<String, T> results = new LinkedHashMap<>();
      List<Future> futures = new LinkedList<>();
      for (String key : keys) {
        futures.add(getString(key, (String) null).compose(res -> {
          T t = Json.decodeValue(res, clazz);
          results.put(key, t);
          return Future.succeededFuture();
        }));
      }
      return CompositeFuture.all(futures).compose(res -> Future.succeededFuture(results));
    }).onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
      }
      fut.handle(new Success<>(res.result()));
    });
  }

}
