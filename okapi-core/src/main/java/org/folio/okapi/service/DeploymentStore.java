package org.folio.okapi.service;

import io.vertx.core.Future;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;

public interface DeploymentStore {
  Future<Void> insert(DeploymentDescriptor dd);

  Future<Boolean> delete(String id);

  Future<Void> init(boolean reset);

  Future<List<DeploymentDescriptor>> getAll();
}
