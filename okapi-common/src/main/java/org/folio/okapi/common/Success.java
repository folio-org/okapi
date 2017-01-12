package org.folio.okapi.common;

import static org.folio.okapi.common.ErrorType.*;

public class Success<T> implements ExtendedAsyncResult<T> {

  private T item;

  public Success() {
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
    return OK;
  }
}
