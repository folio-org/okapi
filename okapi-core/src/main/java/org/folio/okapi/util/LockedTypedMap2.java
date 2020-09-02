package org.folio.okapi.util;

import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

public class LockedTypedMap2<T> extends LockedStringMap {

  private final Class<T> clazz;

  public LockedTypedMap2(Class<T> c) {
    this.clazz = c;
  }

  public void add(String k, String k2, T value, Handler<ExtendedAsyncResult<Void>> fut) {
    String json = Json.encode(value);
    addOrReplace(false, k, k2, json, fut);
  }

  public void put(String k, String k2, T value, Handler<ExtendedAsyncResult<Void>> fut) {
    String json = Json.encode(value);
    addOrReplace(true, k, k2, json, fut);
  }

  /**
   * get and deserialize value from shared map.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @param fut async result with deserialized value on success
   */
  public void get(String k, String k2, Handler<ExtendedAsyncResult<T>> fut) {
    getString(k, k2).onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      if (res.result() == null) {
        fut.handle(new Failure<>(ErrorType.NOT_FOUND, k + "/" + k2));
        return;
      }
      fut.handle(new Success<>(Json.decodeValue(res.result(), clazz)));
    });
  }

  /**
   * get and deserialize values from shared map.
   * @param k primary-level key
   * @param fut async result with deserialized values on success
   */
  public void get(String k, Handler<ExtendedAsyncResult<List<T>>> fut) {
    getPrefix(k, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        LinkedList<T> t = new LinkedList<>();
        for (String s : res.result()) {
          t.add(Json.decodeValue(s, clazz));
        }
        fut.handle(new Success<>(t));
      }
    });
  }
}
