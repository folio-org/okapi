package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.service.DeploymentStore;

public class DeploymentStoreNull implements DeploymentStore {

  @Override
  public Future<Void> insert(DeploymentDescriptor md) {
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
  public Future<List<DeploymentDescriptor>> getAll() {
    return Future.succeededFuture(new ArrayList<>());
  }

}
