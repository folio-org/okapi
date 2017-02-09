package org.folio.okapi.util;

import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;

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

  public void get(String k, Handler<ExtendedAsyncResult<T>> fut) {
    getString(k, null, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>(Json.decodeValue(res.result(), clazz)));
      }
    });
  }
}
