package org.folio.okapi.util;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

public class CompList<T> {

  List<Future> futures = new LinkedList<>();
  ErrorType errorType;

  public CompList(ErrorType type) {
    errorType = type;
  }

  public void add(Future f) {
    futures.add(f);
  }

  public void all(T l, Handler<ExtendedAsyncResult<T>> fut) {
    CompositeFuture.all(futures).setHandler(res2 -> {
      if (res2.failed()) {
        fut.handle(new Failure<>(errorType, res2.cause()));
      } else {
        fut.handle(new Success<>(l));
      }
    });
  }

  public void all(Handler<ExtendedAsyncResult<Void>> fut) {
    CompositeFuture.all(futures).setHandler(res2 -> {
      if (res2.failed()) {
        fut.handle(new Failure<>(errorType, res2.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

}
