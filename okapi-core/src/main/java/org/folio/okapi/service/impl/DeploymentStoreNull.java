package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.DeploymentStore;

public class DeploymentStoreNull implements DeploymentStore {

  @Override
  public void insert(DeploymentDescriptor md, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    fut.handle(new Success<>(md));
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }

  @Override
  public void reset(Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }

}
