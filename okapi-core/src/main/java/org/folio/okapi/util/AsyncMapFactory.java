package org.folio.okapi.util;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;

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
   * @param local   use local map even if Vert.x is in Clustered mode.
   * @param <K>     Key type
   * @param <V>     Value type
   * @return Future with AsyncMap result
   */
  public static <K, V> Future<AsyncMap<K, V>> create(Vertx vertx, String mapName, boolean local) {
    SharedData shared = vertx.sharedData();
    if (!local && vertx.isClustered()) {
      return  shared.getClusterWideMap(mapName);
    }
    // Dirty trickery to make sure we can run two verticles in our tests,
    // without them sharing the 'shared' memory. Only when running in non-
    // clustered mode, of course.
    // Also used in deploy-only nodes, where we want local-only tenant and
    // module lists with only the hard-coded supertenant and internalModule.
    String id = vertx.getOrCreateContext().deploymentID();
    return shared.getLocalAsyncMap(mapName + id);
  }
}
