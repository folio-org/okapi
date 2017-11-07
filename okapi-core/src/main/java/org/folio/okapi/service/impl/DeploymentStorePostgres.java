package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;
import org.folio.okapi.bean.DeploymentDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.DeploymentStore;

@java.lang.SuppressWarnings({"squid:S1192"})
public class DeploymentStorePostgres implements DeploymentStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final PostgresHandle pg;
  private static final String TABLE = "deployments";
  private static final String JSON_COLUMN = "json";
  private static final String ID_SELECT = JSON_COLUMN + "->>'instId' = ?";
  private static final String ID_INDEX = JSON_COLUMN + "->'instId'";

  public DeploymentStorePostgres(PostgresHandle pg) {
    this.pg = pg;
  }

  @Override
  public void reset(Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    String dropSql = "DROP TABLE IF EXISTS " + TABLE;
    q.query(dropSql, res1 -> {
      if (res1.failed()) {
        logger.fatal(dropSql + ": " + res1.cause().getMessage());
        fut.handle(new Failure<>(res1.getType(), res1.cause()));
      } else {
        logger.debug("Dropped the " + TABLE + " table");
        String createSql = "CREATE TABLE " + TABLE + " ( "
          + JSON_COLUMN + " JSONB NOT NULL )";
        q.query(createSql, res2 -> {
          if (res2.failed()) {
            logger.fatal(createSql + ": " + res2.cause().getMessage());
            fut.handle(new Failure<>(res2.getType(), res2.cause()));
          } else {
            String createSql1 = "CREATE UNIQUE INDEX inst_id ON "
              + TABLE + " USING btree((" + ID_INDEX + "))";
            q.query(createSql1, res3 -> {
              if (res2.failed()) {
                logger.fatal(createSql1 + ": " + res3.cause().getMessage());
                fut.handle(new Failure<>(res3.getType(), res3.cause()));
              } else {
                fut.handle(new Success<>());
                q.close();
              }
            });
          }
        });
      }
    });
  }

  @Override
  public void insert(DeploymentDescriptor dd,
    Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {

    PostgresQuery q = pg.getQuery();
    String sql = "INSERT INTO " + TABLE + " (" + JSON_COLUMN + ") VALUES (?::JSONB)"
      + " ON CONFLICT ((" + ID_INDEX + ")) DO UPDATE SET " + JSON_COLUMN + "= ?::JSONB";
    String s = Json.encode(dd);
    JsonObject doc = new JsonObject(s);
    JsonArray jsa = new JsonArray();
    jsa.add(doc.encode());
    jsa.add(doc.encode());
    q.updateWithParams(sql, jsa, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        q.close();
        fut.handle(new Success<>(dd));
      }
    });
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    String sql = "DELETE FROM " + TABLE + " WHERE " + ID_SELECT;
    JsonArray jsa = new JsonArray();
    jsa.add(id);
    q.updateWithParams(sql, jsa, res -> {
      if (res.failed()) {
        logger.error("DeploymentStorePostgres.delete: " + res.cause());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        UpdateResult result = res.result();
        if (result.getUpdated() > 0) {
          fut.handle(new Success<>());
        } else {
          fut.handle(new Failure<>(NOT_FOUND, id));
        }
        q.close();
      }
    });
    fut.handle(new Success<>());
  }
}
