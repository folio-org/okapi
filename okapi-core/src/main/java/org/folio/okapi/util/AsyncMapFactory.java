package org.folio.okapi.util;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import java.util.Random;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import static org.folio.okapi.common.ErrorType.*;

/**
 * Factory to create either a vert.x ClusterWideMap or a
 * AsyncLocalmap, if not running in a clustered mode.
 */
class AsyncMapFactory {

  private AsyncMapFactory() {
    throw new IllegalAccessError("AsyncMapFactory");
  }

  /**
   * Create a AsyncMap
   *
   * @param <K> Key type
   * @param <V> Value type
   * @param vertx
   * @param mapName name of the map. If null, will always create a local map
   * @param fut
   */
  public static <K, V> void create(Vertx vertx, String mapName,          Handler<ExtendedAsyncResult<AsyncMap<K, V>>> fut) {
    if (vertx.isClustered() && mapName != null) {
      SharedData shared = vertx.sharedData();
      shared.<K, V>getClusterWideMap(mapName, res -> {
        if (res.succeeded()) {
          fut.handle(new Success<>(res.result()));
        } else {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        }
      });
    } else {
      // Dirty trickery to make sure we can run two verticles in our tests,
      // without them sharing the 'shared' memory. Only when running in non-
      // clustered mode, of course.
      // Also used in deploy-only nodes, where we want local-only tenant and
      // module lists with only the hard-coded supertenant and internalModule.
      Random r = new Random();
      String newid = String.format("%09d", r.nextInt(1000000000));
      if (mapName != null) {
        newid = mapName + newid;
      }
      AsyncLocalmap<K, V> l = new AsyncLocalmap<>(vertx, newid);
      fut.handle(new Success<>(l));
    }
  }
}
