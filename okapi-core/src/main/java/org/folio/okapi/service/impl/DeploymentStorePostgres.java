package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;
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
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    table.init(reset, fut);
  }

  @Override
  public void insert(DeploymentDescriptor dd, Handler<ExtendedAsyncResult<Void>> fut) {
    table.insert(dd, fut);
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    table.delete(id, fut);
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    table.getAll(DeploymentDescriptor.class, fut);
  }
}
