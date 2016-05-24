/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

public class Failure<T> implements ExtendedAsyncResult<T> {

  final private Throwable failure;
  final private ErrorType errorType;

  public Failure(ErrorType errorType, Throwable failure) {
    this.failure = failure;
    this.errorType = errorType;
  }

  public Failure(ErrorType errorType, String s) {
    this.failure = new Throwable(s);
    this.errorType = errorType;
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

  public ErrorType getType() {
    return errorType;
  }
}
