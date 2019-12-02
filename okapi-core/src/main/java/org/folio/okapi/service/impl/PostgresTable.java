package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;

@java.lang.SuppressWarnings({"squid:S1192"})
class PostgresTable<T> {

  private final Logger logger = OkapiLogger.get();

  private final String table;
  private final String jsonColumn;
  private final String idIndex;
  private final String idSelect;
  private final String indexName;
  private final PostgresHandle pg;

  public PostgresTable(PostgresHandle pg, String table, String jsonColumn,
    String idIndex, String idSelect, String indexName) {
    this.pg = pg;
    this.table = table;
    this.jsonColumn = jsonColumn;
    this.idIndex = idIndex;
    this.idSelect = idSelect;
    this.indexName = indexName;
  }

  private void create(boolean reset, PostgresQuery q, Handler<ExtendedAsyncResult<Void>> fut) {
    String notExists = reset ? "" : "IF NOT EXISTS ";
    String createSql = "CREATE TABLE " + notExists + table
      + " ( " + jsonColumn + " JSONB NOT NULL )";
    q.query(createSql, res1 -> {
      if (res1.failed()) {
        logger.fatal("{}: {}", createSql, res1.cause().getMessage());
        fut.handle(new Failure<>(res1.getType(), res1.cause()));
      } else {
        String createSql1 = "CREATE UNIQUE INDEX " + notExists + indexName + " ON "
          + table + " USING btree((" + idIndex + "))";
        q.query(createSql1, res2 -> {
          if (res1.failed()) {
            logger.fatal("{}: {}", createSql1, res2.cause().getMessage());
            fut.handle(new Failure<>(res2.getType(), res2.cause()));
          } else {
            fut.handle(new Success<>());
            q.close();
          }
        });
      }
    });
  }

  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    if (!reset) {
      create(reset, q, fut);
    } else {
      String dropSql = "DROP TABLE IF EXISTS " + table;
      q.query(dropSql, (ExtendedAsyncResult<ResultSet> res) -> {
        if (res.failed()) {
          logger.fatal("{}: {}", dropSql, res.cause().getMessage());
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          create(reset, q, fut);
        }
      });
    }
  }

  public void insert(T dd, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    final String sql = "INSERT INTO " + table + "(" + jsonColumn + ") VALUES (?::JSONB)";
    String s = Json.encode(dd);
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

  public void update(T md, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    String sql = "INSERT INTO " + table + "(" + jsonColumn + ") VALUES (?::JSONB)"
      + " ON CONFLICT ((" + idIndex + ")) DO UPDATE SET " + jsonColumn + "= ?::JSONB";
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

  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    String sql = "DELETE FROM " + table + " WHERE " + idSelect;
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
          fut.handle(new Failure<>(NOT_FOUND, id));
        }
        q.close();
      }
    });
  }

  public void getAll(Class<T> clazz, Handler<ExtendedAsyncResult<List<T>>> fut) {
    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + jsonColumn + " FROM " + table;
    q.query(sql, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        ResultSet rs = res.result();
        List<T> ml = new ArrayList<>();
        List<JsonObject> tempList = rs.getRows();
        for (JsonObject r : tempList) {
          String tj = r.getString(jsonColumn);
          T md = Json.decodeValue(tj, clazz);
          ml.add(md);
        }
        q.close();
        fut.handle(new Success<>(ml));
      }
    });
  }
}
