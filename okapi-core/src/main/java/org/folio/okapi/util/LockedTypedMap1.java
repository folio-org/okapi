package org.folio.okapi.util;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.folio.okapi.common.ErrorType.INTERNAL;

public class LockedTypedMap1<T> extends LockedStringMap {

  private final Class<T> clazz;
  private AtomicInteger countDown;

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
      Collection<String> keys = kres.result();
      LinkedHashMap<String, T> results = new LinkedHashMap<>();
      List<Future> futures = new LinkedList<>();
      for (String key : keys) {
        Future f = Future.future();
        getString(key, null, res -> {
          if (res.succeeded()) {
            T t = Json.decodeValue(res.result(), clazz);
            results.put(key, t);
            f.handle(Future.succeededFuture());
          } else {
            f.handle(Future.failedFuture(res.cause()));
          }
        });
        futures.add(f);
      }
      CompositeFuture.all(futures).setHandler(res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        } else {
          fut.handle(new Success<>(results));
        }
      });
    });
  }

}
