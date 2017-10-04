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
  private static final String JSON_COLUMN = "modulejson";
  private static final String ID_SELECT = JSON_COLUMN + "->>'id' = ?";
  private static final String ID_INDEX = JSON_COLUMN + "->'id'";

  public ModuleStorePostgres(PostgresHandle pg) {
    this.pg = pg;
  }

  public void resetDatabase(Storage.InitMode initMode, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    q.query("DROP TABLE IF EXISTS modules", res1 -> {
      if (res1.failed()) {
        fut.handle(new Failure<>(INTERNAL, res1.cause()));
      } else {
        if (initMode != Storage.InitMode.INIT) {
          fut.handle(new Success<>());
          q.close();
          return;
        }
        final String createSql = "create table modules ( "
          + JSON_COLUMN + " JSONB NOT NULL )";
        q.query(createSql, res2 -> {
          if (res2.failed()) {
            fut.handle(new Failure<>(res2.getType(), res2.cause()));
          } else {
            final String createSql1 = "CREATE UNIQUE INDEX module_id ON " + ""
              + "modules USING btree((" + ID_INDEX + "))";
            q.query(createSql1, res3 -> {
              if (res2.failed()) {
                fut.handle(new Failure<>(res3.getType(), res3.cause()));
              } else {
                logger.debug("Intitialized the module table");
                q.close();
                fut.handle(new Success<>());
              }
            });
          }
        });
      }
    });
  } // resetDatabase

  @Override
  public void insert(ModuleDescriptor md,
    Handler<ExtendedAsyncResult<String>> fut) {

    PostgresQuery q = pg.getQuery();
    final String sql = "INSERT INTO modules ( " + JSON_COLUMN + " ) VALUES (?::JSONB)";
    String s = Json.encode(md);
    JsonObject doc = new JsonObject(s);
    JsonArray jsa = new JsonArray();
    jsa.add(doc.encode());
    q.queryWithParams(sql, jsa, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>(md.getId()));
      }
      q.close();
    });
  }

  @Override
  public void update(ModuleDescriptor md,
    Handler<ExtendedAsyncResult<String>> fut) {

    PostgresQuery q = pg.getQuery();
    final String id = md.getId();
    String sql = "INSERT INTO modules (" + JSON_COLUMN + ") VALUES (?::JSONB)"
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
        fut.handle(new Success<>(id));
      }
    });
  }

  @Override
  public void get(String id,
    Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {

    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + JSON_COLUMN + " FROM modules WHERE " + ID_SELECT;
    JsonArray jsa = new JsonArray();
    jsa.add(id);
    q.queryWithParams(sql, jsa, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        ResultSet rs = res.result();
        if (rs.getNumRows() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, "Module " + id + " not found"));
        } else {
          JsonObject r = rs.getRows().get(0);
          String tj = r.getString(JSON_COLUMN);
          ModuleDescriptor md = Json.decodeValue(tj, ModuleDescriptor.class);
          q.close();
          fut.handle(new Success<>(md));
        }
      }
    });
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + JSON_COLUMN + " FROM modules";
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
    String sql = "DELETE FROM modules WHERE " + ID_SELECT;
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
