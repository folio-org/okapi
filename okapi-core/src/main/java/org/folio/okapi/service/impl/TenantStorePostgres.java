package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import java.util.SortedMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.service.TenantStore;

/**
 * Stores Tenants in Postgres.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class TenantStorePostgres implements TenantStore {

  private final PostgresHandle pg;
  private static final String TABLE = "tenants";
  private static final String JSON_COLUMN = "tenantjson";
  private static final String ID_SELECT = JSON_COLUMN + "->'descriptor'->>'id' = $1";
  private static final String ID_INDEX = JSON_COLUMN + "->'descriptor'->'id'";
  private final PostgresTable<Tenant> pgTable;

  public TenantStorePostgres(PostgresHandle pg) {
    this.pg = pg;
    this.pgTable = new PostgresTable<>(pg, TABLE, JSON_COLUMN, ID_INDEX, ID_SELECT, "tenant_id");
  }

  @Override
  public Future<Void> init(boolean reset) {
    return pgTable.init(reset);
  }

  @Override
  public Future<Void> insert(Tenant t) {
    return pgTable.insert(t);
  }

  @Override
  public Future<Void> updateDescriptor(TenantDescriptor td) {
    Tenant t = new Tenant(td);
    return pgTable.update(t);
  }

  @Override
  public Future<List<Tenant>> listTenants() {
    return pgTable.getAll(Tenant.class);
  }

  @Override
  public Future<Boolean> delete(String id) {
    return pgTable.delete(id);
  }


  private Future<Boolean> updateModule(PostgresQuery q, String id,
                                      SortedMap<String, Boolean> enabled,
                                      RowSet<Row> set) {
    Future<Boolean> future = Future.succeededFuture(Boolean.FALSE);
    for (Row r : set) {
      String sql = "UPDATE " + TABLE + " SET " + JSON_COLUMN + " = $2 WHERE " + ID_SELECT;
      JsonObject o = (JsonObject) r.getValue(0);
      Tenant t = o.mapTo(Tenant.class);
      t.setEnabled(enabled);
      JsonObject doc = JsonObject.mapFrom(t);
      future = future.compose(a -> q.query(sql, Tuple.of(id, doc)).map(Boolean.TRUE));
    }
    return future;
  }

  @Override
  public Future<Boolean> updateModules(String id, SortedMap<String, Boolean> enabled) {

    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + JSON_COLUMN + " FROM " + TABLE + " WHERE " + ID_SELECT;
    return q.query(sql, Tuple.of(id))
        .compose(res -> updateModule(q, id, enabled, res))
        .onComplete(x -> q.close());
  }
}
