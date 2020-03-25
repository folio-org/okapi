package org.folio.okapi.util;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Factory to create either a vert.x ClusterWideMap or a AsyncLocalmap, if not
 * running in a clustered mode.
 */
class AsyncMapFactory {

  private AsyncMapFactory() {
    throw new IllegalAccessError("AsyncMapFactory");
  }

  /**
   * Creates an AsyncMap.
   *
   * @param <K> Key type
   * @param <V> Value type
   * @param vertx Vert.x handle
   * @param mapName name of the map. If null, will always create a local map
   * @param fut future
   */
  public static <K, V> void create(Vertx vertx, String mapName,
    Handler<ExtendedAsyncResult<AsyncMap<K, V>>> fut) {

    SharedData shared = vertx.sharedData();
    if (vertx.isClustered() && mapName != null) {
      shared.<K, V>getClusterWideMap(mapName, res -> {
        if (res.succeeded()) {
          fut.handle(new Success<>(res.result()));
        } else {
          fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        }
      });
    } else {
      // Dirty trickery to make sure we can run two verticles in our tests,
      // without them sharing the 'shared' memory. Only when running in non-
      // clustered mode, of course.
      // Also used in deploy-only nodes, where we want local-only tenant and
      // module lists with only the hard-coded supertenant and internalModule.
      String id = vertx.getOrCreateContext().deploymentID();
      if (mapName != null) {
        id = mapName + id;
      }
      shared.<K, V>getLocalAsyncMap(id, res -> {
        if (res.succeeded()) {
          fut.handle(new Success<>(res.result()));
        } else {
          fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        }
      });
    }
  }
}
