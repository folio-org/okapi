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
import java.util.TreeMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Stores Tenants in Postgres.
 */
public class TenantStorePostgres implements TenantStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final PostgresHandle pg;
  private final String jsonColumn = "tenantjson";
  private final String idSelect = jsonColumn + "->'descriptor'->>'id' = ?";
  private final String idIndex = "btree((" + jsonColumn + "->'descriptor'->'id'))";

  public TenantStorePostgres(PostgresHandle pg) {
    this.pg = pg;
  }

  public void resetDatabase(Storage.InitMode initMode, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("resetDatabase");
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("resetDatabase: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        final SQLConnection conn = gres.result();
        String dropSql = "DROP TABLE IF EXISTS tenants";
        conn.query(dropSql, dres -> {
          if (dres.failed()) {
            logger.fatal(dropSql + ": " + gres.cause().getMessage());
            fut.handle(new Failure<>(gres.getType(), gres.cause()));
            pg.closeConnection(conn);
          } else {
            logger.debug("Dropped the tenant table");
            if (initMode != Storage.InitMode.INIT) {
              fut.handle(new Success<>());
              return;
            }
            String createSql = "create table tenants ( "
                    + jsonColumn + " JSONB NOT NULL )";
            conn.query(createSql, cres -> {
              if (cres.failed()) {
                logger.fatal(createSql + ": " + gres.cause().getMessage());
                fut.handle(new Failure<>(gres.getType(), gres.cause()));
                pg.closeConnection(conn);
              } else {
                // create unique index id on tenants using btree(id);
                String createSql1 = "CREATE UNIQUE INDEX tenant_id ON "
                        + "tenants USING " + idIndex;
                conn.query(createSql1, res -> {
                  if (cres.failed()) {
                    logger.fatal(createSql1 + ": " + gres.cause().getMessage());
                    fut.handle(new Failure<>(gres.getType(), gres.cause()));
                  } else {
                    logger.debug("Initalized the tenant table");
                    fut.handle(new Success<>());
                  }
                  pg.closeConnection(conn);
                });
              }
            });
          }
        });
      }
    });
  } // resetDatabase

  private void insert(SQLConnection conn, Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("insert");
    String sql = "INSERT INTO tenants ( " + jsonColumn + " ) VALUES (?::JSONB)";
    String s = Json.encode(t);
    JsonObject doc = new JsonObject(s);
    JsonArray jsa = new JsonArray();
    jsa.add(doc.encode());
    conn.queryWithParams(sql, jsa, ires -> {
      if (ires.failed()) {
        logger.fatal("insert failed: " + ires.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, ires.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  @Override
  public void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut) {
    logger.debug("insert");
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("insert: getConnection() failed: " + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn= gres.result();
        insert(conn, t, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            fut.handle(new Success<>(t.getId()));
          }
          pg.closeConnection(conn);
        });
      }
    });
  }

  private void updateAll(SQLConnection conn, String id, TenantDescriptor td, Iterator<JsonObject> it, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("updateAll");
    if (it.hasNext()) {
      JsonObject r = it.next();
      String sql = "UPDATE tenants SET " + jsonColumn + " = ? WHERE " + idSelect;
      String tj = r.getString(jsonColumn);
      Tenant t = Json.decodeValue(tj, Tenant.class);
      Tenant t2 = new Tenant(td, t.getEnabled());
      String s = Json.encode(t2);
      JsonObject doc = new JsonObject(s);
      JsonArray jsa = new JsonArray();
      jsa.add(doc.encode());
      jsa.add(id);
      conn.queryWithParams(sql, jsa, res -> {
        if (res.failed()) {
          logger.fatal("update failed: " + res.cause().getMessage());
        }
        updateAll(conn, id, td, it, fut);
      });
    } else {
      pg.closeConnection(conn);
      fut.handle(new Success<>());
    }
  }

  // INSERT INTO tenants ( id, tenantjson) VALUES (2, '{"enabled": {}, "descriptor": {"id": "our", "name": "our library", "description": "Our Own Library"}}')
  // ON CONFLICT (id) DO UPDATE SET tenantjson = '{"enabled": {}, "descriptor": {"id": "our", "name": "our library", "description": "Our"}}';
  @Override
  public void updateDescriptor(TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("updateDescriptor");
    final String id = td.getId();
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("update: getConnection() failed: " + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "SELECT " + jsonColumn + " FROM tenants WHERE " + idSelect;
        JsonArray jsa = new JsonArray();
        jsa.add(id);
        conn.queryWithParams(sql, jsa, sres -> {
          if (sres.failed()) {
            logger.fatal("update failed: " + sres.cause().getMessage());
            pg.closeConnection(conn);
            fut.handle(new Failure<>(INTERNAL, sres.cause()));
          } else {
            ResultSet rs = sres.result();
            if (rs.getNumRows() == 0) {
              logger.info("update: insert");
              Tenant t = new Tenant(td);
              insert(conn, t, res -> {
                if (res.failed()) {
                  fut.handle(new Failure<>(res.getType(), res.cause()));
                } else {
                  fut.handle(new Success<>());
                }
                pg.closeConnection(conn);
              });
            } else {
              logger.info("update: replace");
              updateAll(conn, id, td, rs.getRows().iterator(), fut);
            }
          }
        });
      }
    });

  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut) {
    logger.debug("listTenants");
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("listTenants: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "SELECT " + jsonColumn + " FROM tenants";
        conn.query(sql, sres -> {
          if (sres.failed()) {
            logger.fatal("listTenants: select failed: "
                    + sres.cause().getMessage());
            fut.handle(new Failure<>(INTERNAL, sres.cause()));
          } else {
            ResultSet rs = sres.result();
            List<Tenant> tl = new ArrayList<>();
            List<JsonObject> tempList = rs.getRows();
            for (JsonObject r : tempList) {
              String tj = r.getString(jsonColumn);
              Tenant t = Json.decodeValue(tj, Tenant.class);
              tl.add(t);
            }
            fut.handle(new Success<>(tl));
          }
          pg.closeConnection(conn);
        });
      }
    });
  }

  @Override
  public void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut) {
    logger.debug("get");
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("get: getConnection() failed: " + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "SELECT " + jsonColumn + " FROM tenants WHERE " + idSelect;
        JsonArray jsa = new JsonArray();
        jsa.add(id);
        conn.queryWithParams(sql, jsa, sres -> {
          if (sres.failed()) {
            logger.fatal("get failed: " + sres.cause().getMessage());
            pg.closeConnection(conn);
            fut.handle(new Failure<>(INTERNAL, sres.cause()));
          } else {
            ResultSet rs = sres.result();
            if (rs.getNumRows() == 0) {
              fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
            } else {
              JsonObject r = rs.getRows().get(0);
              String tj = r.getString(jsonColumn);
              Tenant t = Json.decodeValue(tj, Tenant.class);
              fut.handle(new Success<>(t));
            }
            pg.closeConnection(conn);
          }
        });
      }
    });
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("delete");
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("delete: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "DELETE FROM tenants WHERE " + idSelect;
        JsonArray jsa = new JsonArray();
        jsa.add(id);
        conn.updateWithParams(sql, jsa, sres -> {
          if (sres.failed()) {
            logger.fatal("delete failed: " + sres.cause().getMessage());
            fut.handle(new Failure<>(INTERNAL, sres.cause()));
          } else {
            UpdateResult result = sres.result();
            if (result.getUpdated() > 0)
              fut.handle(new Success<>());
            else
              fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
          }
          pg.closeConnection(conn);
        });
      }
    });
  }

  private void updateModuleR(SQLConnection conn, String id, String module,
    Boolean enable, TreeMap<String, Boolean> enabled,
    Iterator<JsonObject> it,          Handler<ExtendedAsyncResult<Void>> fut) {
    if (it.hasNext()) {
      JsonObject r = it.next();
      String sql = "UPDATE tenants SET " + jsonColumn + " = ? WHERE " + idSelect;
      String tj = r.getString(jsonColumn);
      Tenant t = Json.decodeValue(tj, Tenant.class);
      if (enabled != null) {
        t.setEnabled(enabled);
      } else {
        if (enable) {
          t.enableModule(module);
        } else if (!t.isEnabled(module)) {
          fut.handle(new Failure<>(NOT_FOUND, "Module " + module + " for Tenant "
            + id + " not found, can not disable"));
          pg.closeConnection(conn);
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
      conn.queryWithParams(sql, jsa, res -> {
        if (res.failed()) {
          logger.fatal("update module failed: " + res.cause().getMessage());
          fut.handle(new Failure<>(INTERNAL, res.cause()));
          pg.closeConnection(conn);
        } else {
          updateModuleR(conn, id, module, enable, enabled, it, fut);
        }
      });
    } else {
      fut.handle(new Success<>());
      pg.closeConnection(conn);
    }
  }

  private void updateModule(String id, String module,
    Boolean enable, TreeMap<String, Boolean> enabled,
    Handler<ExtendedAsyncResult<Void>> fut) {
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("updateModule: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "SELECT " + jsonColumn + " FROM tenants WHERE " + idSelect;
        JsonArray jsa = new JsonArray();
        jsa.add(id);
        conn.queryWithParams(sql, jsa, sres -> {
          if (sres.failed()) {
            logger.fatal("updateModule failed: " + sres.cause().getMessage());
            pg.closeConnection(conn);
            fut.handle(new Failure<>(INTERNAL, sres.cause()));
          } else {
            ResultSet rs = sres.result();
            if (rs.getNumRows() == 0) {
              fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
              pg.closeConnection(conn);
            } else {
              logger.debug("update: replace");
              updateModuleR(conn, id, module, enable, enabled,
                rs.getRows().iterator(), fut);
            }
          }
        });
      }
    });
  }

  @Override
  public void updateModules(String id, TreeMap<String, Boolean> enabled,
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
