package org.folio.okapi.managers;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.EnvStore;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.OkapiError;


public class EnvManager {

  private final Logger logger = OkapiLogger.get();
  private final LockedTypedMap1<EnvEntry> envMap = new LockedTypedMap1<>(EnvEntry.class);
  private final EnvStore envStore;
  private final Messages messages = Messages.getInstance();

  /**
   * Construct environment manager.
   * @param s storage
   */
  public EnvManager(EnvStore s) {
    envStore = s;
  }

  /**
   * Initialize environment manager.
   * @param vertx Vert.x handle
   * @return fut async result
   */
  public Future<Void> init(Vertx vertx) {
    logger.debug("starting EnvManager");
    return envMap.init(vertx, "env", false)
        .compose(x -> envStore.getAll())
        .compose(x -> {
          List<Future> futures = new LinkedList<>();
          for (EnvEntry e : x) {
            futures.add(add1(e));
          }
          return CompositeFuture.all(futures).mapEmpty();
        });
  }

  private Future<Void> add1(EnvEntry env) {
    if (env.getName() == null) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, messages.getMessage("10900")));
    }
    if (env.getValue() == null) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, messages.getMessage("10901")));
    }
    return envMap.add(env.getName(), env);
  }

  Future<Void> add(EnvEntry env) {
    return add1(env).compose(res -> envStore.add(env));
  }

  Future<EnvEntry> get(String name) {
    return envMap.get(name).compose(x -> {
      if (x == null) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, name));
      }
      return Future.succeededFuture(x);
    });
  }

  Future<List<EnvEntry>> get() {
    return envMap.getKeys().compose(keys -> {
      List<EnvEntry> list = new LinkedList<>();
      Future<Void> future = Future.succeededFuture();
      for (String key : keys) {
        future = future.compose(a -> envMap.get(key).compose(x -> {
          list.add(x);
          return Future.succeededFuture();
        }));
      }
      return future.map(list);
    });
  }

  Future<Void> remove(String name) {
    return envMap.removeNotFound(name).compose(res -> envStore.delete(name).mapEmpty());
  }
}
