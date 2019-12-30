package org.folio.okapi.service.impl;

import org.folio.okapi.service.TenantStore;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;

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
  private Messages messages = Messages.getInstance();
  private final Logger logger = OkapiLogger.get();

  public TenantStorePostgres(PostgresHandle pg) {
    this.pg = pg;
    this.pgTable = new PostgresTable<>(pg, TABLE, JSON_COLUMN, ID_INDEX, ID_SELECT, "tenant_id");
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    pgTable.init(reset, fut);
  }

  @Override
  public void insert(Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    pgTable.insert(t, fut);
  }

  @Override
  public void updateDescriptor(TenantDescriptor td,
    Handler<ExtendedAsyncResult<Void>> fut) {

    Tenant t = new Tenant(td);
    pgTable.update(t, fut);
  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut) {
    pgTable.getAll(Tenant.class, fut);
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    pgTable.delete(id, fut);
  }

  private void updateModuleR(PostgresQuery q, String id,
    SortedMap<String, Boolean> enabled,
    Iterator<Row> it, Handler<ExtendedAsyncResult<Void>> fut) {

    if (!it.hasNext()) {
      fut.handle(new Success<>());
      q.close();
      return;
    }
    Row r = it.next();
    String sql = "UPDATE " + TABLE + " SET " + JSON_COLUMN + " = $2 WHERE " + ID_SELECT;
    JsonObject o = (JsonObject) r.getValue(0);
    Tenant t = o.mapTo(Tenant.class);
    t.setEnabled(enabled);
    JsonObject doc = JsonObject.mapFrom(t);
    q.query(sql, Tuple.of(id, doc), res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
      } else {
        updateModuleR(q, id, enabled, it, fut);
      }
    });
  }

  @Override
  public void updateModules(String id, SortedMap<String, Boolean> enabled,
    Handler<ExtendedAsyncResult<Void>> fut) {

    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + JSON_COLUMN + " FROM " + TABLE + " WHERE " + ID_SELECT;
    q.query(sql, Tuple.of(id), res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      RowSet<Row> rs = res.result();
      updateModuleR(q, id, enabled, rs.iterator(), fut);
    });
  }
}
