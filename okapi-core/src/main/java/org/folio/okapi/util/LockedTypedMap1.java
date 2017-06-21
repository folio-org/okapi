package org.folio.okapi.util;

import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;

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

  /**
   * Get all records in the map. Returns them in a LinkedHashMap, so they come
   * in well defined order.
   *
   * @param fut callback with the result, or some failure.
   */
  public void getAll(Handler<ExtendedAsyncResult<LinkedHashMap<String, T>>> fut) {
    getKeys(kres -> {
      if (kres.failed()) {
        fut.handle(new Failure<>(kres.getType(), kres.cause()));
        return;
      }
      LinkedHashMap<String, T> all = new LinkedHashMap<>();
      Collection<String> keys = kres.result();
      Iterator<String> it = keys.iterator();
      getAllR(it, all, fut);
    });
  }

  private void getAllR(Iterator<String> it,
    LinkedHashMap<String, T> all,
    Handler<ExtendedAsyncResult<LinkedHashMap<String, T>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(all));
      return;
    }
    String key = it.next();
    getString(key, null, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      String json = gres.result();
      T t = Json.decodeValue(gres.result(), clazz);
      all.put(key, t);
      getAllR(it, all, fut);
    });
  }
}
