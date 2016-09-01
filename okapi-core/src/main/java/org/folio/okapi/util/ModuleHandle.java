package org.folio.okapi.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public interface ModuleHandle {

  void start(Handler<AsyncResult<Void>> startFuture);

  void stop(Handler<AsyncResult<Void>> stopFuture);
}
