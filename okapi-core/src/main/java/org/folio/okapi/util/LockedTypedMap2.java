package org.folio.okapi.util;

import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import java.util.LinkedList;
import java.util.List;

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

  public void get(String k, String k2, Handler<ExtendedAsyncResult<T>> fut) {
    getString(k, k2, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>(Json.decodeValue(res.result(), clazz)));
      }
    });
  }

  public void get(String k, Handler<ExtendedAsyncResult<List<T>>> fut) {
    getString(k, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        LinkedList<T> t = new LinkedList<>();
        for (String s : res.result()) {
          t.add((T) Json.decodeValue(s, clazz));
        }
        fut.handle(new Success<>(t));
      }
    });
  }
}
