package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.service.ModuleStore;

public class ModuleStorePostgres implements ModuleStore {

  private static final String TABLE = "modules";
  private static final String JSON_COLUMN = "modulejson";
  private static final String ID_SELECT = JSON_COLUMN + "->>'id' = $1";
  private static final String ID_INDEX = JSON_COLUMN + "->'id'";
  private final PostgresTable<ModuleDescriptor> pgTable;

  public ModuleStorePostgres(PostgresHandle pg) {
    this.pgTable = new PostgresTable<>(pg, TABLE, JSON_COLUMN, ID_INDEX, ID_SELECT, "module_id");
  }

  @Override
  public Future<Void> init(boolean reset) {
    return pgTable.init(reset);
  }

  @Override
  public void insert(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    pgTable.insert(md, fut);
  }

  @Override
  public void update(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    pgTable.update(md, fut);
  }

  @Override
  public Future<List<ModuleDescriptor>> getAll() {
    return pgTable.getAll(ModuleDescriptor.class);
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    pgTable.delete(id, fut);
  }
}
