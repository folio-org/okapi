package org.folio.okapi.util;

import java.util.LinkedList;
import java.util.List;

import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

public class CompList<T> {

  List<Future> futures = new LinkedList<>();
  ErrorType errorType;

  public CompList(ErrorType type) {
    errorType = type;
  }

  public void add(Promise p) {
    futures.add(p.future());
  }

  public void all(T l, Handler<ExtendedAsyncResult<T>> fut) {
    CompositeFuture.all(futures).setHandler(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(errorType, res.cause()));
      } else {
        fut.handle(new Success<>(l));
      }
    });
  }

  public void all(Handler<ExtendedAsyncResult<Void>> fut) {
    CompositeFuture.all(futures).setHandler(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(errorType, res.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  public void seq(Handler<ExtendedAsyncResult<Void>> fut) {
    Future<Void> future = Future.succeededFuture();
    for (Future f : futures) {
      future = future.compose(x -> {
        if (f.failed()) {
          return Future.failedFuture(f.cause());
        } else {
          return Future.succeededFuture();
        }
      });
    }
    future.setHandler(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(errorType, res.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

}
