package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

@java.lang.SuppressWarnings({"squid:S1192"})
public class PostgresQuery {

  private SqlConnection conn;
  private final PostgresHandle pg;

  PostgresQuery(PostgresHandle pg) {
    this.pg = pg;
    this.conn = null;
  }

  private Future<SqlConnection> getCon() {
    if (conn != null) {
      return Future.succeededFuture(conn);
    }
    return pg.getConnection().compose(res -> {
      conn = res;
      return Future.succeededFuture(res);
    });
  }

  Future<RowSet<Row>> query(String sql, Tuple tuple) {
    return getCon().compose(x -> x.preparedQuery(sql).execute(tuple)).onFailure(x -> close());
  }

  Future<RowSet<Row>> query(String sql) {
    return getCon().compose(x -> x.query(sql).execute()).onFailure(x -> close());
  }

  void close() {
    if (conn != null) {
      conn.close();
      conn = null;
    }
  }
}
