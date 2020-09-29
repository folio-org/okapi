package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.service.EnvStore;

public class EnvStoreNull implements EnvStore {

  @Override
  public Future<Void> add(EnvEntry env) {
    return Future.succeededFuture();
  }

  @Override
  public Future<Boolean> delete(String id) {
    return Future.succeededFuture(Boolean.TRUE);
  }

  @Override
  public Future<Void> init(boolean reset) {
    return Future.succeededFuture();
  }

  @Override
  public Future<List<EnvEntry>> getAll() {
    return Future.succeededFuture(new ArrayList<>());
  }

}
