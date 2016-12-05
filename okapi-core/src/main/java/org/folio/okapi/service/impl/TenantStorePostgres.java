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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
  private long lastTimestamp = 0;
  private PostgresHandle pg;

  private final String tenantTable = "tenants";

  public TenantStorePostgres(PostgresHandle pg) {
    logger.info("TenantStoreProgres");
    this.pg = pg;
  }

  /**
   * Drop and create the table(s) we may need.
   *
   * @param fut
   */
  public void resetDatabase(Handler<ExtendedAsyncResult<Void>> fut) {
    if (!pg.getDropDb()) {
      fut.handle(new Success<>());
      return;
    }
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("TenantStorePg: resetDatabase: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        final SQLConnection conn = gres.result();
        String dropSql = "DROP TABLE IF EXISTS tenants";
        conn.query(dropSql, dres -> {
          if (dres.failed()) {
            logger.fatal("TenantStorePg: resetDatabase: drop table failed: "
                    + gres.cause().getMessage());
            fut.handle(new Failure<>(gres.getType(), gres.cause()));
          } else {
            String createSql = "create table tenants ( "
                    + " id VARCHAR(32) PRIMARY KEY, "
                    + "tenantjson JSONB NOT NULL )";
            logger.debug("TS: About to create tables: " + createSql);
            conn.query(createSql, cres -> {
              if (cres.failed()) {
                logger.fatal("TenantStorePg: resetDatabase: drop table failed: "
                        + gres.cause().getMessage());
                fut.handle(new Failure<>(gres.getType(), gres.cause()));
              } else {
                fut.handle(new Success<>());
              }
            });
          }
        });
      }
    });
  } // resetDatabase

  public void insert(SQLConnection conn, Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    String sql = "INSERT INTO tenants ( id, tenantjson ) VALUES (?, ?::JSONB)";
    // TODO Type of the id ??
    String id = t.getId();
    String s = Json.encode(t);
    JsonObject doc = new JsonObject(s);
    JsonArray jsa = new JsonArray();
    jsa.add(id);
    jsa.add(doc.encode());
    conn.queryWithParams(sql, jsa, ires -> {
      if (ires.failed()) {
        logger.fatal("TenantStorePg: insert failed: "
                + ires.cause().getMessage());
        conn.close(cres -> {
          if (cres.failed()) {
            logger.fatal("TenantStorePg: Insert: Closing handle failed as well: "
                    + cres.cause().getMessage());
          } // Do not handle failure here, we report the select failure below
        });
        fut.handle(new Failure<>(INTERNAL, ires.cause()));
      } else {
        conn.close(cres -> {
          if (cres.failed()) {
            logger.fatal("TenantStorePg: insert: Closing handle failed: "
                    + cres.cause().getMessage());
            fut.handle(new Failure<>(INTERNAL, cres.cause()));
          } else {
            fut.handle(new Success<>());
          }
        });
      }
    });
  }

  @Override
  public void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut) {
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("TenantStorePg: insert: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        insert(gres.result(), t, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            fut.handle(new Success<>(t.getId()));
          }
        });
      }
    });
  }

  private void updateAll(SQLConnection conn, String id, TenantDescriptor td, Iterator<JsonObject> it, Handler<ExtendedAsyncResult<Void>> fut) {
    if (it.hasNext()) {
      JsonObject r = it.next();
      String sql = "UPDATE tenants SET tenantjson = ? WHERE id=?";
      String tj = r.getString("tenantjson");
      Tenant t = Json.decodeValue(tj, Tenant.class);
      Tenant t2 = new Tenant(td, t.getEnabled());
      String s = Json.encode(t2);
      JsonObject doc = new JsonObject(s);
      JsonArray jsa = new JsonArray();
      jsa.add(doc.encode());
      jsa.add(id);
      conn.queryWithParams(sql, jsa, res -> {
        if (res.failed()) {
          logger.fatal("TenantStorePg: update failed: "
                  + res.cause().getMessage());
        }
        updateAll(conn, id, td, it, fut);
      });
    } else {
      conn.close(res -> {
        if (res.failed()) {
          logger.fatal("TenantStorePg: updateAll: Closing handle failed: "
                  + res.cause().getMessage());
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        } else {
          fut.handle(new Success<>());
        }
      });
    }
  }

  @Override
  public void updateDescriptor(String id, TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.info("updateDescriptor");
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("TenantStorePg: update: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "SELECT tenantjson FROM tenants WHERE id=?";
        JsonArray jsa = new JsonArray();
        jsa.add(id);
        conn.queryWithParams(sql, jsa, sres -> {
          if (sres.failed()) {
            logger.fatal("TenantStorePg: update failed: "
                    + sres.cause().getMessage());
            conn.close(cres -> {
              if (cres.failed()) {
                logger.fatal("TenantStorePg: update: Closing handle failed as well: "
                        + cres.cause().getMessage());
              } // Do not handle failure here, we report the select failure below
            });
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
  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    logger.fatal("listIds");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut) {
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("TenantStorePg: listTenants: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "SELECT tenantjson FROM tenants";
        conn.query(sql, sres -> {
          if (sres.failed()) {
            logger.fatal("TenantStorePg: listTenants: select failed: "
                    + sres.cause().getMessage());
            conn.close(cres -> {
              if (cres.failed()) {
                logger.fatal("TenantStorePg: ListTenants: Closing handle failed as well: "
                        + cres.cause().getMessage());
              } // Do not handle failure here, we report the select failure below
            });
            fut.handle(new Failure<>(INTERNAL, sres.cause()));
          } else {
            ResultSet rs = sres.result();
            List<Tenant> tl = new ArrayList<>();
            List<JsonObject> tempList = rs.getRows();
            for (JsonObject r : tempList) {
              logger.debug("listTenants: Looking at " + r);
              String tj = r.getString("tenantjson");
              Tenant t = Json.decodeValue(tj, Tenant.class);
              tl.add(t);
            }
            conn.close(cres -> {
              if (cres.failed()) {
                logger.fatal("TenantStorePg: ListTenants: Closing handle failed: "
                        + cres.cause().getMessage());
                fut.handle(new Failure<>(INTERNAL, cres.cause()));
              } else {
                fut.handle(new Success<>(tl));
              }
            });
          }
        });
      }
    });
  }

  @Override
  public void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut) {
    logger.fatal("get");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.fatal("delete");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void enableModule(String id, String module, long timestamp,
          Handler<ExtendedAsyncResult<Void>> fut) {
    logger.fatal("enableModule");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void disableModule(String id, String module, long timestamp,
          Handler<ExtendedAsyncResult<Void>> fut) {
    logger.fatal("disableModule");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }
}
