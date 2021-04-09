package org.folio.okapi.common;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ExtendedAsyncResultTest implements WithAssertions {

  @Test
  void fromSuccess() {
    AsyncResult<String> success = new Success<>("foo");
    assertThat(ExtendedAsyncResult.from(success)).isSameAs(success);
  }

  @Test
  void fromFailure() {
    AsyncResult<String> failure = new Failure<>(ErrorType.USER, "foo");
    assertThat(ExtendedAsyncResult.from(failure)).isSameAs(failure);
  }

  @Test
  void fromSucceeded() {
    assertThat(ExtendedAsyncResult.from(Future.succeededFuture("done")))
        .isInstanceOf(Success.class)
        .returns(ErrorType.OK, from(ExtendedAsyncResult::getType))
        .returns("done", from(ExtendedAsyncResult::result));
  }

  @Test
  void fromErrorTypeException() {
    Exception cause = new ErrorTypeException(ErrorType.FORBIDDEN, "foo");
    assertThat(ExtendedAsyncResult.from(Future.failedFuture(cause)))
        .isInstanceOf(Failure.class)
        .returns(ErrorType.FORBIDDEN, from(ExtendedAsyncResult::getType))
        .returns(cause, from(ExtendedAsyncResult::cause));
  }

  @Test
  void fromErrorTypeExceptionWithCause() {
    Exception causeCause = new RuntimeException("causeCause");
    Exception cause = new ErrorTypeException(ErrorType.NOT_FOUND, causeCause);
    assertThat(ExtendedAsyncResult.from(Future.failedFuture(cause)))
        .isInstanceOf(Failure.class)
        .returns(ErrorType.NOT_FOUND, from(ExtendedAsyncResult::getType))
        .returns(causeCause, from(ExtendedAsyncResult::cause));
  }

  @Test
  void fromFailed() {
    Exception cause = new InterruptedException("break");
    assertThat(ExtendedAsyncResult.from(Future.failedFuture(cause)))
        .isInstanceOf(Failure.class)
        .returns(ErrorType.ANY, from(ExtendedAsyncResult::getType))
        .returns(cause, from(ExtendedAsyncResult::cause));
  }
}
