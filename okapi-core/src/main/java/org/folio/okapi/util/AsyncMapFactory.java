package org.folio.okapi.util;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import static org.folio.okapi.common.ErrorType.*;

/**
 * Factory to create either a vert.x ClusterWideMap or a
 * AsyncLocalmap, if not running in a clustered mode.
 */
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
      fut.handle(new Success<>(l));
    }
  }
}
