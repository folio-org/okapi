package org.folio.okapi.service.impl;

import org.folio.okapi.service.ModuleStore;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;

public class ModuleStorePostgres implements ModuleStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private static final String TABLE = "modules";
  private static final String JSON_COLUMN = "modulejson";
  private static final String ID_SELECT = JSON_COLUMN + "->>'id' = ?";
  private static final String ID_INDEX = JSON_COLUMN + "->'id'";
  private final PostgresTable<ModuleDescriptor> table;

  public ModuleStorePostgres(PostgresHandle pg) {
    this.table = new PostgresTable(pg, TABLE, JSON_COLUMN, ID_INDEX, ID_SELECT, "module_id");
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    table.init(reset, fut);
  }

  @Override
  public void insert(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    table.insert(md, fut);
  }

  @Override
  public void update(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    table.update(md, fut);
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    table.getAll(ModuleDescriptor.class, fut);
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    table.delete(id, fut);
  }
}
