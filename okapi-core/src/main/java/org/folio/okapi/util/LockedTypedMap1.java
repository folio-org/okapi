package org.folio.okapi.util;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import java.util.Collection;
import java.util.LinkedHashMap;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;


public class LockedTypedMap1<T> extends LockedStringMap {

  private final Class<T> clazz;

  public LockedTypedMap1(Class<T> c) {
    this.clazz = c;
  }

  public void add(String k, T value, Handler<ExtendedAsyncResult<Void>> fut) {
    String json = Json.encode(value);
    addOrReplace(false, k, null, json, fut);
  }

  public void put(String k, T value, Handler<ExtendedAsyncResult<Void>> fut) {
    String json = Json.encode(value);
    addOrReplace(true, k, null, json, fut);
  }

  /**
   * Get and deserialize to type from shared map.
   * @param k key
   * @param fut result with value if successful
   */
  public void get(String k, Handler<ExtendedAsyncResult<T>> fut) {
    getString(k, null, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>(Json.decodeValue(res.result(), clazz)));
      }
    });
  }

  /**
   * Get all records in the map. Returns them in a LinkedHashMap, so they come
   * in well defined order.
   *
   * @param fut callback with the result, or some failure.
   */
  public void getAll(Handler<ExtendedAsyncResult<LinkedHashMap<String, T>>> fut) {
    getKeys(keyRes -> {
      if (keyRes.failed()) {
        fut.handle(new Failure<>(keyRes.getType(), keyRes.cause()));
        return;
      }
      Collection<String> keys = keyRes.result();
      LinkedHashMap<String, T> results = new LinkedHashMap<>();
      CompList<LinkedHashMap<String,T>> futures = new CompList<>(ErrorType.INTERNAL);
      for (String key : keys) {
        Promise<String> promise = Promise.promise();
        getString(key, null, res -> {
          if (res.succeeded()) {
            T t = Json.decodeValue(res.result(), clazz);
            results.put(key, t);
          }
          promise.handle(res);
        });
        futures.add(promise);
      }
      futures.all(results, fut);
    });
  }

}
