package org.folio.okapi.service;

import io.vertx.core.Handler;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;

public interface DeploymentStore {
  void insert(DeploymentDescriptor md, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut);

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  void reset(Handler<ExtendedAsyncResult<Void>> fut);
}
