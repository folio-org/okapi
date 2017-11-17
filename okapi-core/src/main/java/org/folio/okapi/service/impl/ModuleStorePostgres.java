package org.folio.okapi.service.impl;

import org.folio.okapi.service.ModuleStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

public class ModuleStorePostgres implements ModuleStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final PostgresHandle pg;
  private static final String TABLE = "modules";
  private static final String JSON_COLUMN = "modulejson";
  private static final String ID_SELECT = JSON_COLUMN + "->>'id' = ?";
  private static final String ID_INDEX = JSON_COLUMN + "->'id'";

  public ModuleStorePostgres(PostgresHandle pg) {
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
        String createSql1 = "CREATE UNIQUE INDEX " + notExists + "module_id ON "
          + TABLE + " USING btree((" + ID_INDEX + "))";
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

  @Override
  public void insert(ModuleDescriptor md,
    Handler<ExtendedAsyncResult<Void>> fut) {

    PostgresQuery q = pg.getQuery();
    final String sql = "INSERT INTO " + TABLE + "(" + JSON_COLUMN + ") VALUES (?::JSONB)";
    String s = Json.encode(md);
    JsonObject doc = new JsonObject(s);
    JsonArray jsa = new JsonArray();
    jsa.add(doc.encode());
    q.queryWithParams(sql, jsa, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        q.close();
        fut.handle(new Success<>());
      }
    });
  }

  @Override
  public void update(ModuleDescriptor md,
    Handler<ExtendedAsyncResult<Void>> fut) {

    PostgresQuery q = pg.getQuery();
    String sql = "INSERT INTO " + TABLE + "(" + JSON_COLUMN + ") VALUES (?::JSONB)"
      + " ON CONFLICT ((" + ID_INDEX + ")) DO UPDATE SET " + JSON_COLUMN + "= ?::JSONB";
    String s = Json.encode(md);
    JsonObject doc = new JsonObject(s);
    JsonArray jsa = new JsonArray();
    jsa.add(doc.encode());
    jsa.add(doc.encode());
    q.updateWithParams(sql, jsa, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        q.close();
        fut.handle(new Success<>());
      }
    });
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + JSON_COLUMN + " FROM " + TABLE;
    q.query(sql, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        ResultSet rs = res.result();
        List<ModuleDescriptor> ml = new ArrayList<>();
        List<JsonObject> tempList = rs.getRows();
        for (JsonObject r : tempList) {
          String tj = r.getString(JSON_COLUMN);
          ModuleDescriptor md = Json.decodeValue(tj, ModuleDescriptor.class);
          ml.add(md);
        }
        q.close();
        fut.handle(new Success<>(ml));
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
        logger.fatal("delete failed: " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        UpdateResult result = res.result();
        if (result.getUpdated() > 0) {
          fut.handle(new Success<>());
        } else {
          fut.handle(new Failure<>(NOT_FOUND, "Module " + id + " not found"));
        }
        q.close();
      }
    });
  }
}
