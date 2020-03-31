package org.folio.okapi.common;

public class Success<T> implements ExtendedAsyncResult<T> {

  private T item;

  public Success() {
    this.item = null;
  }

  public Success(T item) {
    this.item = item;
  }

  @Override
  public T result() {
    return item;
  }

  @Override
  public Throwable cause() {
    return null;
  }

  @Override
  public boolean succeeded() {
    return true;
  }

  @Override
  public boolean failed() {
    return false;
  }

  @Override
  public ErrorType getType() {
    return ErrorType.OK;
  }
}
