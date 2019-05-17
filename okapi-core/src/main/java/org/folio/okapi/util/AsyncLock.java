package org.folio.okapi.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;

public class AsyncLock {

  private final boolean isCluster;
  private final SharedData shared;

  public AsyncLock(Vertx vertx) {
    shared = vertx.sharedData();
    isCluster = vertx.isClustered();
  }

  public void getLock(String name, Handler<AsyncResult<Lock>> resultHandler) {
    long longTime = 2000000000;
    if (isCluster) {
      shared.getLockWithTimeout(name, longTime, resultHandler);
    } else {
      shared.getLocalLockWithTimeout(name, longTime, resultHandler);
    }
  }
}
