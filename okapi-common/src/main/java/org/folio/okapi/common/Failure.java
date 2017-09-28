package org.folio.okapi.common;

public class Failure<T> implements ExtendedAsyncResult<T> {

  private final Throwable failureThrown;
  private final ErrorType errorType;

  public Failure(ErrorType errorType, Throwable failure) {
    this.failureThrown = failure;
    this.errorType = errorType;
  }

  public Failure(ErrorType errorType, String s) {
    this.failureThrown = new Throwable(s);
    this.errorType = errorType;
  }

  @Override
  public T result() {
    return null;
  }

  @Override
  public Throwable cause() {
    return failureThrown;
  }

  @Override
  public boolean succeeded() {
    return false;
  }

  @Override
  public boolean failed() {
    return true;
  }

  public ErrorType getType() {
    return errorType;
  }
}
