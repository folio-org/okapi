package org.folio.okapi.service.impl;

import org.folio.okapi.service.TenantStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.sql.SQLConnection;
import java.util.ArrayList;
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
   * @param fut 
   */
  public void resetDatabase(Handler<ExtendedAsyncResult<Void>> fut) {
    if ( !pg.getDropDb()) {
      logger.info("Dropping all tenants and resetting the tables");
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
          if ( dres.failed()) {
            logger.fatal("TenantStorePg: resetDatabase: drop table failed: "
              + gres.cause().getMessage());
            fut.handle(new Failure<>(gres.getType(), gres.cause()));
          } else {
            String createSql = "create table tenants ( "
              + " id VARCHAR(32) PRIMARY KEY, "
              + "tenantjson JSONB NOT NULL )";
            logger.debug("TS: About to create tables: " + createSql);
            conn.query(createSql, cres -> {
              if ( cres.failed()) {
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


  @Override
  public void insert(Tenant t,
          Handler<ExtendedAsyncResult<String>> fut) {
    pg.getConnection(res -> {
      if (res.failed()) {
        logger.fatal("TenantStorePg: insert: getConnection() failed: "
          + res.cause().getMessage());
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        SQLConnection connection = res.result();
        String sql = "INSERT INTO tenants ( id, tenantjson ) VALUES (?, ?::JSONB)";
          // TODO Type of the id ??
        String id = t.getId();
        String s = Json.encode(t);
        JsonObject doc = new JsonObject(s);
        JsonArray jsa = new JsonArray();
        jsa.add(id);
        jsa.add(doc.encode());
        connection.queryWithParams(sql, jsa, query -> {
          if ( res.failed()) {
            logger.fatal("TenantStorePg: insert failed: "
              + res.cause().getMessage());
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            fut.handle(new Success<>("HOLY"));
          }
        });
      }
    });
  }

  @Override
  public void update(Tenant t,
          Handler<ExtendedAsyncResult<String>> fut) {
    logger.fatal("update");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void updateDescriptor(String id, TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.fatal("updateDescriptor");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    logger.fatal("listIds");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut) {
    logger.info("listTenants");
    List<Tenant> tl = new ArrayList<>();
    fut.handle(new Success<>(tl));
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
