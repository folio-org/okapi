package org.folio.okapi.service.impl;

import org.folio.okapi.service.TenantStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
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
  private static final String TABLE = "tenants";
  private static final String JSON_COLUMN = "tenantjson";
  private static final String ID_SELECT = JSON_COLUMN + "->'descriptor'->>'id' = ?";
  private static final String ID_INDEX = "btree((" + JSON_COLUMN + "->'descriptor'->'id'))";

  public TenantStorePostgres(PostgresHandle pg) {
    this.pg = pg;
  }

  private void create(boolean reset, PostgresQuery q, Handler<ExtendedAsyncResult<Void>> fut) {
    String notExists = reset ? "" : "IF NOT EXISTS ";
    String createSql = "CREATE TABLE " + notExists + TABLE
      + " ( " + JSON_COLUMN + " JSONB NOT NULL )";
    q.query(createSql, res1 -> {
      if (res1.failed()) {
        logger.fatal(createSql + ": " + res1.cause().getMessage());
        fut.handle(new Failure<>(res1.getType(), res1.cause()));
      } else {
        String createSql1 = "CREATE UNIQUE INDEX " + notExists + "tenant_id ON "
          + TABLE + " USING " + ID_INDEX;
        q.query(createSql1, res2 -> {
          if (res1.failed()) {
            logger.fatal(createSql1 + ": " + res2.cause().getMessage());
            fut.handle(new Failure<>(res2.getType(), res2.cause()));
          } else {
            fut.handle(new Success<>());
            q.close();
          }
        });
      }
    });
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    if (!reset) {
      create(reset, q, fut);
    } else {
      String dropSql = "DROP TABLE IF EXISTS " + TABLE;
      q.query(dropSql, res -> {
        if (res.failed()) {
          logger.fatal(dropSql + ": " + res.cause().getMessage());
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          create(reset, q, fut);
        }
      });
    }
  }

  private void insertTenant(PostgresQuery q, Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    String sql = "INSERT INTO " + TABLE + "(" + JSON_COLUMN + ") VALUES (?::JSONB)";
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
  public void insert(Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("insert");
    PostgresQuery q = pg.getQuery();
    insertTenant(q, t, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>());
      }
      q.close();
    });
  }

  private void updateAll(PostgresQuery q, String id, TenantDescriptor td,
    Iterator<JsonObject> it, Handler<ExtendedAsyncResult<Void>> fut) {

    logger.debug("updateAll");
    if (it.hasNext()) {
      JsonObject r = it.next();
      String sql = "UPDATE " + TABLE + " SET " + JSON_COLUMN + " = ? WHERE " + ID_SELECT;
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
        } else {
          updateAll(q, id, td, it, fut);
        }
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
    String sql = "SELECT " + JSON_COLUMN + " FROM " + TABLE + " WHERE " + ID_SELECT;
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
    String sql = "SELECT " + JSON_COLUMN + " FROM " + TABLE;
    q.query(sql, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        ResultSet rs = res.result();
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
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("delete");
    PostgresQuery q = pg.getQuery();
    String sql = "DELETE FROM " + TABLE + " WHERE " + ID_SELECT;

    JsonArray jsa = new JsonArray();
    jsa.add(id);
    q.updateWithParams(sql, jsa, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        UpdateResult result = res.result();
        if (result.getUpdated() > 0) {
          fut.handle(new Success<>());
        } else {
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
        }
      }
      q.close();
    });
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

    logger.debug("updateModules " + Json.encode(enabled.keySet()));
    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + JSON_COLUMN + " FROM " + TABLE + " WHERE " + ID_SELECT;
    JsonArray jsa = new JsonArray();
    jsa.add(id);
    q.queryWithParams(sql, jsa, res -> {
      if (res.failed()) {
        logger.fatal("updateModule failed: " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        ResultSet rs = res.result();
        if (rs.getNumRows() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
          q.close();
        } else {
          logger.debug("update: replace");
          updateModuleR(q, id, enabled, rs.getRows().iterator(), fut);
        }
      }
    });
  }
}
