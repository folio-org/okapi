package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.DeploymentStore;

public class DeploymentStoreNull implements DeploymentStore {

  @Override
  public void insert(DeploymentDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
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
