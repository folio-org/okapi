package org.folio.okapi.common;

import io.vertx.core.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ErrorTypeExceptionTest {
  @Test
  void testConstructMessage() {
    ErrorTypeException e = new ErrorTypeException(ErrorType.USER, "my user error");
    assertThat(e.getErrorType(), is(ErrorType.USER));
    assertThat(e.getMessage(), is("my user error"));
  }

  @Test
  void testConstructThrowable() {
    RuntimeException runtimeException = new RuntimeException("my user error");
    ErrorTypeException e = new ErrorTypeException(ErrorType.USER, runtimeException);
    assertThat(e.getErrorType(), is(ErrorType.USER));
    assertThat(e.getMessage(), is("java.lang.RuntimeException: my user error"));
    assertThat(ErrorTypeException.getType(runtimeException), is(ErrorType.ANY));
    assertThat(ErrorTypeException.getType(e), is(ErrorType.USER));
  }

  @Test
  void testAsyncResult() {
    Future f = Future.failedFuture("error");
    assertThat(ErrorTypeException.getType(f), is(ErrorType.ANY));
    assertThat(f.cause().getMessage(), is("error"));

    Future s = Future.succeededFuture();
    Assertions.assertThrows(IllegalArgumentException.class, () -> ErrorTypeException.getType(s));
  }


}
