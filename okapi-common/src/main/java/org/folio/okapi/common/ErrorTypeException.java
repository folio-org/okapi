package org.folio.okapi.common;

import io.vertx.core.AsyncResult;

/**
 * An Exception that wraps an {@link ErrorType} and a {@link Throwable}.
 */
public class ErrorTypeException extends RuntimeException {
  private final ErrorType errorType;

  public ErrorTypeException(ErrorType errorType, Throwable throwable) {
    super(throwable);
    this.errorType = errorType;
  }

  public ErrorTypeException(ErrorType errorType, String message) {
    super(message);
    this.errorType = errorType;
  }

  public ErrorType getErrorType() {
    return errorType;
  }

  /**
   * Get error type from ErrorTypeException or ErrorType.ANY for other.
   * @param t the exception
   * @return error type
   */
  public static ErrorType getType(Throwable t) {
    if (t instanceof ErrorTypeException) {
      return ((ErrorTypeException) t).errorType;
    }
    return ErrorType.ANY;
  }

  /**
   * Get error type from AsyncResult cause or ErrorType.ANY for other.
   * @param asyncResult async result
   * @return error type
   */
  public static <T> ErrorType getType(AsyncResult<T> asyncResult) {
    if (asyncResult.succeeded()) {
      throw new IllegalArgumentException("asyncResult must be failed");
    }
    return getType(asyncResult.cause());
  }
}
