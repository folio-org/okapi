package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.EnvStore;


public class EnvStoreNull implements EnvStore {

  @Override
  public void add(EnvEntry env, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<EnvEntry>>> fut) {
    fut.handle(new Success<>(new ArrayList<>()));
  }

}
