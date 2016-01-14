package com.indexdata.okapi.util;

import io.vertx.core.AsyncResult;

public class Failure<T> implements AsyncResult<T> {
  private Throwable failure;

  public Failure(Throwable failure) {
    this.failure = failure;
  }
  public Failure(String s) {
    this.failure = new Throwable(s);
  }
  
  @Override
  public T result() {
    return null;
  }

  @Override
  public Throwable cause() {
    return failure;
  }

  @Override
  public boolean succeeded() {
    return false;
  }

  @Override
  public boolean failed() {
    return true;
  }
}
