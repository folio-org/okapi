package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import java.util.Collections;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.service.ModuleStore;

public class ModuleStoreNull implements ModuleStore {
  @Override
  public Future<Boolean> delete(String id) {
    return Future.succeededFuture(Boolean.TRUE);
  }

  @Override
  public Future<List<ModuleDescriptor>> getAll() {
    return Future.succeededFuture(Collections.emptyList());
  }

  @Override
  public Future<Void> insert(List<ModuleDescriptor> mds) {
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> init(boolean reset) {
    return Future.succeededFuture();
  }
}
