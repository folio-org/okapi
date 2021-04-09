package org.folio.okapi.common;

/**
 * An Exception that wraps an {@link ErrorType} and a {@link Throwable}.
 */
public class ErrorTypeException extends Exception {
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
}
