package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.service.EnvStore;

public class EnvStorePostgres implements EnvStore {

  private static final String JSON_COLUMN = "json";
  private static final String ID_SELECT = JSON_COLUMN + "->>'name' = $1";
  private static final String ID_INDEX = JSON_COLUMN + "->'name'";
  private final PostgresTable<EnvEntry> table;

  public EnvStorePostgres(PostgresHandle pg) {
    this.table = new PostgresTable<>(pg, "env", JSON_COLUMN, ID_INDEX, ID_SELECT, "name");
  }

  @Override
  public Future<Void> add(EnvEntry env) {
    return table.update(env);
  }

  @Override
  public Future<Boolean> delete(String id) {
    return table.delete(id);
  }

  @Override
  public Future<Void> init(boolean reset) {
    return table.init(reset);
  }

  @Override
  public Future<List<EnvEntry>> getAll() {
    return table.getAll(EnvEntry.class);
  }

}
