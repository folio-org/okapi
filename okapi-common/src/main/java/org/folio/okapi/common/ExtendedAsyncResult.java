package org.folio.okapi.common;

import io.vertx.core.AsyncResult;

/**
 * Like vert.x' AsyncResult, but with our enum ErrorType, to distinguish between
 * internal and user errors, etc.
 */
public interface ExtendedAsyncResult<T> extends AsyncResult<T> {

  ErrorType getType();

  /**
   * Create an ExtendedAsyncResult from the AsyncResult.
   */
  public static <T> ExtendedAsyncResult<T> from(AsyncResult<T> asyncResult) {
    if (asyncResult instanceof ExtendedAsyncResult) {
      return (ExtendedAsyncResult<T>) asyncResult;
    }
    if (asyncResult.succeeded()) {
      return new Success<T>(asyncResult.result());
    }
    Throwable cause = asyncResult.cause();
    if (cause instanceof ErrorTypeException) {
      ErrorType errorType = ((ErrorTypeException) cause).getErrorType();
      Throwable causeCause = cause.getCause();
      return new Failure<T>(errorType, causeCause != null ? causeCause : cause);
    }
    return new Failure<T>(ErrorType.ANY, cause);
  }
}
