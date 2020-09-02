package org.folio.okapi.util;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.common.ExtendedAsyncResult;

public class LockedTypedMap2<T> extends LockedStringMap {

  private final Class<T> clazz;

  public LockedTypedMap2(Class<T> c) {
    this.clazz = c;
  }

  public Future<Void> add(String k, String k2, T value) {
    String json = Json.encode(value);
    return addOrReplace(false, k, k2, json);
  }

  public void put(String k, String k2, T value, Handler<ExtendedAsyncResult<Void>> fut) {
    String json = Json.encode(value);
    addOrReplace(true, k, k2, json, fut);
  }

  /**
   * get and deserialize value from shared map.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @return fut async result with deserialized value on success
   */
  public Future<T> get(String k, String k2) {
    return getString(k, k2).compose(res -> {
      if (res == null) {
        return Future.failedFuture(new NotFound(k + "/" + k2));
      }
      return Future.succeededFuture(Json.decodeValue(res, clazz));
    });
  }

  /**
   * get and deserialize values from shared map.
   * @param k primary-level key
   * @return fut async result with deserialized values on success
   */
  public Future<List<T>> get(String k) {
    return getPrefix(k).compose(res -> {
      if (res == null) {
        return Future.succeededFuture(null);
      }
      LinkedList<T> t = new LinkedList<>();
      for (String s : res) {
        t.add(Json.decodeValue(s, clazz));
      }
      return Future.succeededFuture(t);
    });
  }
}
