package org.folio.okapi.service.impl;

import org.folio.okapi.service.TenantStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Stores Tenants in Postgres.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class TenantStorePostgres implements TenantStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final PostgresHandle pg;
  private static final String JSON_COLUMN = "tenantjson";
  private static final String ID_SELECT = JSON_COLUMN + "->'descriptor'->>'id' = ?";
  private static final String ID_INDEX = "btree((" + JSON_COLUMN + "->'descriptor'->'id'))";

  public TenantStorePostgres(PostgresHandle pg) {
    this.pg = pg;
  }

  public void resetDatabase(Storage.InitMode initMode, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    String dropSql = "DROP TABLE IF EXISTS tenants";
    q.query(dropSql, res1 -> {
      if (res1.failed()) {
        logger.fatal(dropSql + ": " + res1.cause().getMessage());
        fut.handle(new Failure<>(res1.getType(), res1.cause()));
      } else {
        logger.debug("Dropped the tenant table");
        if (initMode != Storage.InitMode.INIT) {
          fut.handle(new Success<>());
          return;
        }
        String createSql = "create table tenants ( "
          + JSON_COLUMN + " JSONB NOT NULL )";
        q.query(createSql, res2 -> {
          if (res2.failed()) {
            logger.fatal(createSql + ": " + res2.cause().getMessage());
            fut.handle(new Failure<>(res2.getType(), res2.cause()));
          } else {
            String createSql1 = "CREATE UNIQUE INDEX tenant_id ON "
              + "tenants USING " + ID_INDEX;
            q.query(createSql1, res3 -> {
              if (res2.failed()) {
                logger.fatal(createSql1 + ": " + res3.cause().getMessage());
                fut.handle(new Failure<>(res3.getType(), res3.cause()));
              } else {
                logger.debug("Initalized the tenant table");
                fut.handle(new Success<>());
                q.close();
              }
            });
          }
        });
      }
    });
  }

  private void insertTenant(PostgresQuery q, Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("insert");
    String sql = "INSERT INTO tenants ( " + JSON_COLUMN + " ) VALUES (?::JSONB)";
    String s = Json.encode(t);
    JsonObject doc = new JsonObject(s);
    JsonArray jsa = new JsonArray();
    jsa.add(doc.encode());
    q.queryWithParams(sql, jsa, res -> {
      if (res.failed()) {
        logger.fatal("insert failed: " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  @Override
  public void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut) {
    logger.debug("insert");
    PostgresQuery q = pg.getQuery();
    insertTenant(q, t, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>(t.getId()));
      }
      q.close();
    });
  }

  private void updateAll(PostgresQuery q, String id, TenantDescriptor td,
    Iterator<JsonObject> it, Handler<ExtendedAsyncResult<Void>> fut) {

    logger.debug("updateAll");
    if (it.hasNext()) {
      JsonObject r = it.next();
      String sql = "UPDATE tenants SET " + JSON_COLUMN + " = ? WHERE " + ID_SELECT;
      String tj = r.getString(JSON_COLUMN);
      Tenant t = Json.decodeValue(tj, Tenant.class);
      Tenant t2 = new Tenant(td, t.getEnabled());
      String s = Json.encode(t2);
      JsonObject doc = new JsonObject(s);
      JsonArray jsa = new JsonArray();
      jsa.add(doc.encode());
      jsa.add(id);
      q.queryWithParams(sql, jsa, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        updateAll(q, id, td, it, fut);
      });
    } else {
      q.close();
      fut.handle(new Success<>());
    }
  }

  @Override
  public void updateDescriptor(TenantDescriptor td,
    Handler<ExtendedAsyncResult<Void>> fut) {

    logger.debug("updateDescriptor");
    PostgresQuery q = pg.getQuery();
    final String id = td.getId();
    String sql = "SELECT " + JSON_COLUMN + " FROM tenants WHERE " + ID_SELECT;
    JsonArray jsa = new JsonArray();
    jsa.add(id);
    q.queryWithParams(sql, jsa, sres -> {
      if (sres.failed()) {
        fut.handle(new Failure<>(INTERNAL, sres.cause()));
      } else {
        ResultSet rs = sres.result();
        if (rs.getNumRows() == 0) {
          Tenant t = new Tenant(td);
          insertTenant(q, t, res -> {
            if (res.failed()) {
              fut.handle(new Failure<>(res.getType(), res.cause()));
            } else {
              fut.handle(new Success<>());
            }
            q.close();
          });
        } else {
          updateAll(q, id, td, rs.getRows().iterator(), fut);
        }
      }
    });

  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut) {
    logger.debug("listTenants");
    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + JSON_COLUMN + " FROM tenants";
    q.query(sql, sres -> {
      if (sres.failed()) {
        fut.handle(new Failure<>(INTERNAL, sres.cause()));
      } else {
        ResultSet rs = sres.result();
        List<Tenant> tl = new ArrayList<>();
        List<JsonObject> tempList = rs.getRows();
        for (JsonObject r : tempList) {
          String tj = r.getString(JSON_COLUMN);
          Tenant t = Json.decodeValue(tj, Tenant.class);
          tl.add(t);
        }
        fut.handle(new Success<>(tl));
      }
      q.close();
    });
  }

  @Override
  public void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut) {
    logger.debug("get");
    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + JSON_COLUMN + " FROM tenants WHERE " + ID_SELECT;
    JsonArray jsa = new JsonArray();
    jsa.add(id);
    q.queryWithParams(sql, jsa, sres -> {
      if (sres.failed()) {
        fut.handle(new Failure<>(INTERNAL, sres.cause()));
      } else {
        ResultSet rs = sres.result();
        if (rs.getNumRows() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
        } else {
          JsonObject r = rs.getRows().get(0);
          String tj = r.getString(JSON_COLUMN);
          Tenant t = Json.decodeValue(tj, Tenant.class);
          fut.handle(new Success<>(t));
        }
        q.close();
      }
    });
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("delete");
    PostgresQuery q = pg.getQuery();
    String sql = "DELETE FROM tenants WHERE " + ID_SELECT;

    JsonArray jsa = new JsonArray();
    jsa.add(id);
    q.updateWithParams(sql, jsa, sres -> {
      if (sres.failed()) {
        fut.handle(new Failure<>(INTERNAL, sres.cause()));
      } else {
        UpdateResult result = sres.result();
        if (result.getUpdated() > 0) {
          fut.handle(new Success<>());
        } else {
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
        }
      }
      q.close();
    });
  }

  private void updateModuleR(PostgresQuery q, String id, String module,
    Boolean enable, SortedMap<String, Boolean> enabled,
    Iterator<JsonObject> it, Handler<ExtendedAsyncResult<Void>> fut) {
    if (it.hasNext()) {
      JsonObject r = it.next();
      String sql = "UPDATE tenants SET " + JSON_COLUMN + " = ? WHERE " + ID_SELECT;
      String tj = r.getString(JSON_COLUMN);
      Tenant t = Json.decodeValue(tj, Tenant.class);
      if (enabled != null) {
        t.setEnabled(enabled);
      } else {
        if (enable) {
          t.enableModule(module);
        } else if (!t.isEnabled(module)) {
          fut.handle(new Failure<>(NOT_FOUND, "Module " + module + " for Tenant "
            + id + " not found, can not disable"));
          q.close();
          return;
        } else {
          t.disableModule(module);
        }
      }
      String s = Json.encode(t);
      JsonObject doc = new JsonObject(s);
      JsonArray jsa = new JsonArray();
      jsa.add(doc.encode());
      jsa.add(id);
      q.queryWithParams(sql, jsa, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        } else {
          updateModuleR(q, id, module, enable, enabled, it, fut);
        }
      });
    } else {
      fut.handle(new Success<>());
      q.close();
    }
  }

  private void updateModule(String id, String module,
    Boolean enable, SortedMap<String, Boolean> enabled,
    Handler<ExtendedAsyncResult<Void>> fut) {

    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + JSON_COLUMN + " FROM tenants WHERE " + ID_SELECT;
    JsonArray jsa = new JsonArray();
    jsa.add(id);
    q.queryWithParams(sql, jsa, sres -> {
      if (sres.failed()) {
        logger.fatal("updateModule failed: " + sres.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, sres.cause()));
      } else {
        ResultSet rs = sres.result();
        if (rs.getNumRows() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
          q.close();
        } else {
          logger.debug("update: replace");
          updateModuleR(q, id, module, enable, enabled,
            rs.getRows().iterator(), fut);
        }
      }
    });
  }

  @Override
  public void updateModules(String id, SortedMap<String, Boolean> enabled,
    Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("updateModules " + Json.encode(enabled.keySet()));
    updateModule(id, "", null, enabled, fut);

  }

  @Override
  public void enableModule(String id, String module,
          Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("enableModule");
    updateModule(id, module, true, null, fut);
  }

  @Override
  public void disableModule(String id, String module,
          Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("disableModule");
    updateModule(id, module, false, null, fut);
  }
}
