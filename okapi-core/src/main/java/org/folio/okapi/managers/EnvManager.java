package org.folio.okapi.managers;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.EnvStore;
import org.folio.okapi.util.CompList;
import org.folio.okapi.util.LockedTypedMap1;


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
    return envMap.init(vertx, "env").compose(res -> {
      Promise<Void> promise = Promise.promise();
      envStore.getAll(res2 -> {
        if (res2.failed()) {
          promise.fail(res2.cause());
          return;
        }
        List<Future> futures = new LinkedList<>();
        for (EnvEntry e : res2.result()) {
          Promise<Void> promise1 = Promise.promise();
          add1(e, promise1::handle);
          futures.add(promise1.future());
        }
        CompositeFuture.all(futures).onComplete(x -> promise.handle(x.mapEmpty()));
      });
      return promise.future();
    });
  }

  private void add1(EnvEntry env, Handler<ExtendedAsyncResult<Void>> fut) {
    if (env.getName() == null) {
      fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10900")));
    } else if (env.getValue() == null) {
      fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10901")));
    } else {
      envMap.add(env.getName(), env, fut);
    }
  }

  void add(EnvEntry env, Handler<ExtendedAsyncResult<Void>> fut) {
    add1(env, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        envStore.add(env, fut);
      }
    });
  }

  private void getR(Iterator<String> it, List<EnvEntry> all,
          Handler<ExtendedAsyncResult<List<EnvEntry>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(all));
    } else {
      String srvcId = it.next();
      get(srvcId, resGet -> {
        if (resGet.failed()) {
          fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
        } else {
          EnvEntry dpl = resGet.result();
          all.add(dpl);
          getR(it, all, fut);
        }
      });
    }
  }

  void get(String name, Handler<ExtendedAsyncResult<EnvEntry>> fut) {
    envMap.get(name, fut);
  }

  void get(Handler<ExtendedAsyncResult<List<EnvEntry>>> fut) {
    envMap.getKeys(resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
      } else {
        Collection<String> keys = resGet.result();
        List<EnvEntry> all = new LinkedList<>();
        if (keys == null || keys.isEmpty()) {
          fut.handle(new Success<>(all));
        } else {
          getR(keys.iterator(), all, fut);
        }
      }
    });
  }

  void remove(String name, Handler<ExtendedAsyncResult<Void>> fut) {
    envMap.remove(name, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        envStore.delete(name, fut);
      }
    });
  }
}
