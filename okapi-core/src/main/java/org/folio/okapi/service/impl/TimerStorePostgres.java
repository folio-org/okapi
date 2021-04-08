package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.bean.TimerDescriptor;
import org.folio.okapi.service.TimerStore;

public class TimerStorePostgres implements TimerStore {
  private final PostgresHandle pg;
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
    this.pg = pg;
    this.pgTable = new PostgresTable<>(pg, TABLE, JSON_COLUMN, ID_INDEX, ID_SELECT,
        "timers_tenant_timer_id");
  }

  @Override
  public Future<Void> init(boolean reset) {
    return pgTable.init(reset);
  }

  @Override
  public Future<List<TimerDescriptor>> getAll(String tenantId) {
    int prefixLen = tenantId.length() + 1;
    return pgTable.getAll(TimerDescriptor.class).compose(x -> {
      List<TimerDescriptor> res = new LinkedList<>();
      for (TimerDescriptor timerDescriptor : x) {
        String tenantTimerId = timerDescriptor.getId();
        if (tenantTimerId.startsWith(tenantId + ".")) {
          timerDescriptor.setId(tenantTimerId.substring(prefixLen));
          res.add(timerDescriptor);
        }
      }
      return Future.succeededFuture(res);
    });
  }

  @Override
  public Future<Void> put(String tenantId, TimerDescriptor timerDescriptor) {
    // TODO: there must be a better way
    String encoded = Json.encode(timerDescriptor);
    TimerDescriptor timerDescriptor1 = new JsonObject(encoded).mapTo(TimerDescriptor.class);
    timerDescriptor1.setId(tenantId + "." + timerDescriptor.getId());
    return pgTable.update(timerDescriptor1);
  }
}
