package org.folio.okapi.util;

import io.vertx.core.Handler;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.folio.okapi.common.ExtendedAsyncResult;

public class TestBase {

  /**
   * Like context.asyncAssertSuccess() but for ExtendedAsyncResult;
   */
  protected Handler<ExtendedAsyncResult<Void>> asyncAssertSuccess(TestContext context) {
    Async async = context.async();
    return handler -> {
      if (handler.failed()) {
        context.fail(handler.cause());
      }
      async.complete();
    };
  }

  /**
   * Like context.asyncAssertSuccess(Handler) but for ExtendedAsyncResult;
   */
  protected <T> Handler<ExtendedAsyncResult<T>> asyncAssertSuccess(
      TestContext context, Handler<Void> block) {
    Async async = context.async();
    return handler -> {
      if (handler.failed()) {
        context.fail(handler.cause());
      }
      context.verify(block);
      async.complete();
    };
  }
}
