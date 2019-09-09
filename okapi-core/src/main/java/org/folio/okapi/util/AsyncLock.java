package org.folio.okapi.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;

public class AsyncLock {

  private final boolean isCluster;
  private final SharedData shared;
  // A good margin below thread lock warnings (60 seconds)
  private static final long LONG_TIME = 5000; // in ms

  public AsyncLock(Vertx vertx) {
    shared = vertx.sharedData();
    isCluster = vertx.isClustered();
  }

  private void getLockR(String name, AsyncResult<Lock> x, Handler<AsyncResult<Lock>> resultHandler) {
    // when timeout is received we repeat again..
    if (x.failed() && x.cause().getMessage().startsWith("Timed out")) {
      getLock(name, resultHandler);
      return;
    }
    resultHandler.handle(x);
  }

  /**
   * Lock with no timeout. The built-in getLock has a timeout which is way
   * too short.
   * @param name of lock
   * @param resultHandler to be called when lock is obtained or error
   */
  public void getLock(String name, Handler<AsyncResult<Lock>> resultHandler) {
    if (isCluster) {
      shared.getLockWithTimeout(name, LONG_TIME, x -> getLockR(name, x, resultHandler));
    } else {
      shared.getLocalLockWithTimeout(name, LONG_TIME, x -> getLockR(name, x, resultHandler));
    }
  }
}
