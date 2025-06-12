package org.folio.okapi.util;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.common.ErrorType;

public class LockedTypedMap2<T> extends LockedStringMap {

  private final Class<T> clazz;

  public LockedTypedMap2(Class<T> c) {
    this.clazz = c;
  }

  public Future<Void> add(String k, String k2, T value) {
    String json = Json.encode(value);
    return addOrReplace(false, k, k2, json);
  }

  public Future<Void> put(String k, String k2, T value) {
    String json = Json.encode(value);
    return addOrReplace(true, k, k2, json);
  }

  /**
   * get and deserialize value from shared map.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @return fut async result with deserialized value on success
   */
  public Future<T> getNotFound(String k, String k2) {
    return get(k, k2).compose(res -> {
      if (res == null) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, k + "/" + k2));
      }
      return Future.succeededFuture(res);
    });
  }

  /**
   * get and deserialize value from shared map.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @return fut async result with deserialized value on success (null if not found)
   */
  public Future<T> get(String k, String k2) {
    return getString(k, k2).compose(res -> {
      if (res == null) {
        return Future.succeededFuture(null);
      }
      return Future.succeededFuture(JsonDecoder.decode(res, clazz));
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
        t.add(JsonDecoder.decode(s, clazz));
      }
      return Future.succeededFuture(t);
    });
  }
}
