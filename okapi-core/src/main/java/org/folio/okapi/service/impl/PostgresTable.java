package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;

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

  private Future<Void> create(boolean reset, PostgresQuery q) {
    String notExists = reset ? "" : "IF NOT EXISTS ";
    String createSql = "CREATE TABLE " + notExists + table
        + " ( " + jsonColumn + " JSONB NOT NULL )";
    return q.query(createSql).compose(x -> {
      String createSql1 = "CREATE UNIQUE INDEX " + notExists + indexName + " ON "
          + table + " USING btree((" + idIndex + "))";
      return q.query(createSql1).onSuccess(y -> q.close()).mapEmpty();
    });
  }

  Future<Void> init(boolean reset) {
    PostgresQuery q = pg.getQuery();
    if (!reset) {
      return create(false, q);
    }
    String dropSql = "DROP TABLE IF EXISTS " + table;
    return q.query(dropSql).compose(x -> create(true, q));
  }

  Future<Void> insert(T dd) {
    PostgresQuery q = pg.getQuery();
    final String sql = "INSERT INTO " + table + "(" + jsonColumn + ") VALUES ($1::JSONB)";
    return q.query(sql, Tuple.of(JsonObject.mapFrom(dd))).<Void>mapEmpty()
        .onSuccess(res -> q.close());
  }

  Future<Void> insertBatch(List<T> dds) {
    if (dds.isEmpty()) {
      return Future.succeededFuture();
    }
    PostgresQuery q = pg.getQuery();
    final String sql = "INSERT INTO " + table + "(" + jsonColumn + ") VALUES ($1::JSONB)";
    final List<Tuple> tuples = new ArrayList<>(dds.size());
    dds.forEach(dd -> tuples.add(Tuple.of(JsonObject.mapFrom(dd))));
    return q.query(sql, tuples).<Void>mapEmpty()
        .onSuccess(res -> q.close());
  }

  Future<Void> update(T md) {
    PostgresQuery q = pg.getQuery();
    String sql = "INSERT INTO " + table + "(" + jsonColumn + ") VALUES ($1::JSONB)"
        + " ON CONFLICT ((" + idIndex + ")) DO UPDATE SET " + jsonColumn + "= $1::JSONB";
    String s = Json.encode(md);
    JsonObject doc = new JsonObject(s);
    return q.query(sql, Tuple.of(doc)).<Void>mapEmpty()
        .onSuccess(res -> q.close());
  }

  Future<Boolean> delete(String id) {
    PostgresQuery q = pg.getQuery();
    String sql = "DELETE FROM " + table + " WHERE " + idSelect;
    return q.query(sql, Tuple.of(id)).compose(res -> {
      q.close();
      if (res.rowCount() == 0) {
        return Future.succeededFuture(Boolean.FALSE);
      }
      return Future.succeededFuture(Boolean.TRUE);
    });
  }

  Future<List<T>> getAll(Class<T> clazz) {
    PostgresQuery q = pg.getQuery();
    String sql = "SELECT " + jsonColumn + " FROM " + table;
    return q.query(sql).compose(res -> {
      List<T> ml = new ArrayList<>();
      for (Row r : res) {
        JsonObject o = (JsonObject) r.getValue(0);
        T md = o.mapTo(clazz);
        ml.add(md);
      }
      q.close();
      return Future.succeededFuture(ml);
    });
  }

}
