package com.indexdata.okapi.util;
import io.vertx.core.AsyncResult;

public class Success<T> implements AsyncResult<T> {
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
}

