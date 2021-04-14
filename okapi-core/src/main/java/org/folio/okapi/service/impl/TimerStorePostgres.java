package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import java.util.List;
import org.folio.okapi.bean.TimerDescriptor;
import org.folio.okapi.service.TimerStore;

public class TimerStorePostgres implements TimerStore {
  private static final String TABLE = "timers";
  private static final String JSON_COLUMN = "json";
  private static final String ID_SELECT = JSON_COLUMN + "->>'id' = $1";
  private static final String ID_INDEX = JSON_COLUMN + "->'id'";
  private final PostgresTable<TimerDescriptor> pgTable;

  /**
   * TimerStore Postgres constructor.
   * @param pg handle for postgres
   */
  public TimerStorePostgres(PostgresHandle pg) {
    this.pgTable = new PostgresTable<>(pg, TABLE, JSON_COLUMN, ID_INDEX, ID_SELECT,
        "timers_tenant_timer_id");
  }

  @Override
  public Future<Void> init(boolean reset) {
    return pgTable.init(reset);
  }

  @Override
  public Future<List<TimerDescriptor>> getAll() {
    return pgTable.getAll(TimerDescriptor.class);
  }

  @Override
  public Future<Void> put(TimerDescriptor timerDescriptor) {
    return pgTable.update(timerDescriptor);
  }

  @Override
  public Future<Boolean> delete(String id) {
    return pgTable.delete(id).mapEmpty();
  }
}
