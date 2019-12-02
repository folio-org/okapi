package org.folio.okapi.service.impl;

import org.folio.okapi.service.TenantStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import static org.folio.okapi.common.ErrorType.*;
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

  private final Logger logger = OkapiLogger.get();
  private final PostgresHandle pg;
  private static final String TABLE = "tenants";
  private static final String JSON_COLUMN = "tenantjson";
  private static final String ID_SELECT = JSON_COLUMN + "->'descriptor'->>'id' = ?";
  private static final String ID_INDEX = JSON_COLUMN + "->'descriptor'->'id'";
  private final PostgresTable<Tenant> pgTable;
  private Messages messages = Messages.getInstance();

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
    Iterator<JsonObject> it, Handler<ExtendedAsyncResult<Void>> fut) {

    if (it.hasNext()) {
      JsonObject r = it.next();
      String sql = "UPDATE " + TABLE + " SET " + JSON_COLUMN + " = ? WHERE " + ID_SELECT;
      String tj = r.getString(JSON_COLUMN);
      Tenant t = Json.decodeValue(tj, Tenant.class);
      t.setEnabled(enabled);
      String s = Json.encode(t);
      JsonObject doc = new JsonObject(s);
      JsonArray jsa = new JsonArray();
      jsa.add(doc.encode());
      jsa.add(id);
      q.queryWithParams(sql, jsa, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        } else {
          updateModuleR(q, id, enabled, it, fut);
        }
      });
    } else {
      fut.handle(new Success<>());
      q.close();
    }
  }

  @Override
  public void updateModules(String id, SortedMap<String, Boolean> enabled,
    Handler<ExtendedAsyncResult<Void>> fut) {

    if (logger.isDebugEnabled()) {
      logger.debug("updateModules {}", Json.encode(enabled.keySet()));
    }
    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + JSON_COLUMN + " FROM " + TABLE + " WHERE " + ID_SELECT;
    JsonArray jsa = new JsonArray();
    jsa.add(id);
    q.queryWithParams(sql, jsa, res -> {
      if (res.failed()) {
        logger.fatal("updateModule {} failed: {}", id, res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        ResultSet rs = res.result();
        if (rs.getNumRows() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, messages.getMessage("11200", id)));
          q.close();
        } else {
          updateModuleR(q, id, enabled, rs.getRows().iterator(), fut);
        }
      }
    });
  }
}
