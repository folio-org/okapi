package org.folio.okapi.util;

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

  private String getKeysError;

  public void setGetKeysError(String getKeysError) {
    this.getKeysError = getKeysError;
  }

  @Override
  public void getKeys(Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    if (getKeysError != null) {
      fut.handle(new Failure<>(ErrorType.INTERNAL, getKeysError));
      return;
    }
    super.getKeys(fut);
  }

}
