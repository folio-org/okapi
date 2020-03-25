package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;


@java.lang.SuppressWarnings({"squid:S1192"})
class PostgresTable<T> {

  private final String table;
  private final String jsonColumn;
  private final String idIndex;
  private final String idSelect;
  private final String indexName;
  private final PostgresHandle pg;

  PostgresTable(PostgresHandle pg, String table, String jsonColumn,
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
        fut.handle(new Failure<>(res1.getType(), res1.cause()));
        return;
      }
      String createSql1 = "CREATE UNIQUE INDEX " + notExists + indexName + " ON "
        + table + " USING btree((" + idIndex + "))";
      q.query(createSql1, res2 -> {
        if (res1.failed()) {
          fut.handle(new Failure<>(res2.getType(), res2.cause()));
        } else {
          fut.handle(new Success<>());
          q.close();
        }
      });
    });
  }

  void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    if (!reset) {
      create(false, q, fut);
      return;
    }
    String dropSql = "DROP TABLE IF EXISTS " + table;
    q.query(dropSql, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      create(true, q, fut);
    });
  }

  void insert(T dd, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    final String sql = "INSERT INTO " + table + "(" + jsonColumn + ") VALUES ($1::JSONB)";
    String s = Json.encode(dd);
    JsonObject doc = new JsonObject(s);
    q.query(sql, Tuple.of(doc), res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      q.close();
      fut.handle(new Success<>());
    });
  }

  void update(T md, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    String sql = "INSERT INTO " + table + "(" + jsonColumn + ") VALUES ($1::JSONB)"
      + " ON CONFLICT ((" + idIndex + ")) DO UPDATE SET " + jsonColumn + "= $1::JSONB";
    String s = Json.encode(md);
    JsonObject doc = new JsonObject(s);
    q.query(sql, Tuple.of(doc), res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      q.close();
      fut.handle(new Success<>());
    });
  }

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    PostgresQuery q = pg.getQuery();
    String sql = "DELETE FROM " + table + " WHERE " + idSelect;
    q.query(sql, Tuple.of(id), res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      q.close();
      RowSet result = res.result();
      if (result.rowCount() == 0) {
        fut.handle(new Failure<>(ErrorType.NOT_FOUND, id));
        return;
      }
      fut.handle(new Success<>());
    });
  }

  void getAll(Class<T> clazz, Handler<ExtendedAsyncResult<List<T>>> fut) {
    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + jsonColumn + " FROM " + table;
    q.query(sql, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      List<T> ml = new ArrayList<>();
      for (Row r : res.result()) {
        JsonObject o = (JsonObject) r.getValue(0);
        T md = o.mapTo(clazz);
        ml.add(md);
      }
      q.close();
      fut.handle(new Success<>(ml));
    });
  }
}
