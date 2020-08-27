package org.folio.okapi.util;

import io.vertx.core.Future;
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
   * @param vertx   Vert.x handle
   * @param mapName name of the map. If null, will always create a local map
   * @param <K>     Key type
   * @param <V>     Value type
   * @return Future with AsyncMap result
   */
  public static <K, V> Future<AsyncMap<K, V>> create(Vertx vertx, String mapName) {
    SharedData shared = vertx.sharedData();
    if (vertx.isClustered() && mapName != null) {
      return shared.<K, V>getClusterWideMap(mapName);
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
      return shared.<K, V>getLocalAsyncMap(id);
    }
  }
}
