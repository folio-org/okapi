package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.service.EnvStore;

public class EnvStorePostgres implements EnvStore {

  private static final String JSON_COLUMN = "json";
  private static final String ID_SELECT = JSON_COLUMN + "->>'name' = ?";
  private static final String ID_INDEX = JSON_COLUMN + "->'name'";
  private final PostgresTable<EnvEntry> table;

  public EnvStorePostgres(PostgresHandle pg) {
    this.table = new PostgresTable(pg, "env", JSON_COLUMN, ID_INDEX, ID_SELECT, "name");
  }

  @Override
  public void add(EnvEntry env, Handler<ExtendedAsyncResult<Void>> fut) {
    table.update(env, fut);
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    table.delete(id, fut);
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    table.init(reset, fut);
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<EnvEntry>>> fut) {
    table.getAll(EnvEntry.class, fut);
  }

}
