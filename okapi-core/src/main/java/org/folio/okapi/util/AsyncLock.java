package org.folio.okapi.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;

public class AsyncLock {

  boolean isCluster;
  final SharedData shared;
  // A good margin below thread lock warnings (60 seconds)
  static final long DEFAULT_POLL_TIME = 5000; // in ms
  long pollTime = DEFAULT_POLL_TIME;

  public AsyncLock(Vertx vertx) {
    shared = vertx.sharedData();
    isCluster = vertx.isClustered();
  }

  private void getLockR(String name, AsyncResult<Lock> x,
                        Handler<AsyncResult<Lock>> resultHandler) {

    // when timeout is received we repeat again..
    if (x.failed() && x.cause().getMessage().startsWith("Timed out")) {
      getLock(name, resultHandler);
      return;
    }
    resultHandler.handle(x);
  }

  /**
   * Lock with no timeout. The built-in getLock has a timeout which is way too short.
   * @param name of lock
   * @param resultHandler to be called when lock is obtained or error
   */
  public void getLock(String name, Handler<AsyncResult<Lock>> resultHandler) {
    if (isCluster) {
      shared.getLockWithTimeout(name, pollTime, x -> getLockR(name, x, resultHandler));
    } else {
      shared.getLocalLockWithTimeout(name, pollTime, x -> getLockR(name, x, resultHandler));
    }
  }
}
