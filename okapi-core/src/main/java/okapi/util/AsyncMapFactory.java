/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import static okapi.util.ErrorType.*;

public class AsyncMapFactory {

  public static <K, V> void create(Vertx vertx, String mapName,
          Handler<ExtendedAsyncResult<AsyncMap<K, V>>> fut) {
    if (vertx.isClustered()) {
      SharedData shared = vertx.sharedData();
      shared.<K, V>getClusterWideMap(mapName, res -> {
        if (res.succeeded()) {
          fut.handle(new Success<>(res.result()));
        } else {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        }
      });
    } else {
      AsyncLocalmap<K, V> l = new AsyncLocalmap<>(vertx, mapName);
      fut.handle(new Success(l));
    }
  }
}
