package org.folio.okapi.service;

import io.vertx.core.Handler;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;

public interface DeploymentStore {
  void insert(DeploymentDescriptor dd, Handler<ExtendedAsyncResult<Void>> fut);

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut);

  void getAll(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut);
}
