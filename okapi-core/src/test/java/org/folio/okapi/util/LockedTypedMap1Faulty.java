package org.folio.okapi.util;

import io.vertx.core.Future;
import java.util.Collection;

public class LockedTypedMap1Faulty<T> extends LockedTypedMap1<T> {

  public LockedTypedMap1Faulty(Class<T> c) {
    super(c);
  }

  private String getError;

  public void setGetError(String getError) {
    this.getError = getError;
  }

  @Override
  public Future<T> getNotFound(String k) {
    return get(k);
  }

  @Override
  public Future<T> get(String k) {
    if (getError != null) {
      return Future.failedFuture(getError);
    }
    return super.get(k);
  }

  private String addError;

  public void setAddError(String addError) {
    this.addError = addError;
  }

  @Override
  public Future<Void> add(String k, T value) {
    if (addError != null) {
      return Future.failedFuture(addError);
    }
    return super.add(k, value);
  }

  private String getKeysError;

  public void setGetKeysError(String getKeysError) {
    this.getKeysError = getKeysError;
  }


  @Override
  public Future<Collection<String>> getKeys() {
    if (getKeysError != null) {
      return Future.failedFuture(getKeysError);
    }
    return super.getKeys();
  }

}
