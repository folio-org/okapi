package org.folio.okapi.util;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Collection;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;

public class LockedTypedMap1Faulty<T> extends LockedTypedMap1<T> {

  public LockedTypedMap1Faulty(Class<T> c) {
    super(c);
  }

  private String getError;

  public void setGetError(String getError) {
    this.getError = getError;
  }

  @Override
  public void get(String k, Handler<ExtendedAsyncResult<T>> fut) {
    if (getError != null) {
      fut.handle(new Failure<>(ErrorType.INTERNAL, getError));
      return;
    }
    super.get(k, fut);
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
  public void add(String k, T value, Handler<ExtendedAsyncResult<Void>> fut) {
    if (addError != null) {
      fut.handle(new Failure<>(ErrorType.INTERNAL, addError));
      return;
    }
    super.add(k, value, fut);
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
