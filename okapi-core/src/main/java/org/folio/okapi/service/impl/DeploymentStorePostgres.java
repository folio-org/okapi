package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.service.DeploymentStore;


public class DeploymentStorePostgres implements DeploymentStore {

  private static final String JSON_COLUMN = "json";
  private static final String ID_SELECT = JSON_COLUMN + "->>'instId' = $1";
  private static final String ID_INDEX = JSON_COLUMN + "->'instId'";
  private final PostgresTable<DeploymentDescriptor> table;

  public DeploymentStorePostgres(PostgresHandle pg) {
    this.table = new PostgresTable<>(pg, "deployments", JSON_COLUMN,
      ID_INDEX, ID_SELECT, "inst_id");
  }

  @Override
  public Future<Void> init(boolean reset) {
    return table.init(reset);
  }

  @Override
  public Future<Void> insert(DeploymentDescriptor dd) {
    return table.insert(dd);
  }

  @Override
  public Future<Boolean> delete(String id) {
    return table.delete(id);
  }

  @Override
  public Future<List<DeploymentDescriptor>> getAll() {
    return table.getAll(DeploymentDescriptor.class);
  }
}
