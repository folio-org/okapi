package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;

@java.lang.SuppressWarnings({"squid:S1192"})
public class PostgresQuery {

  private static final Logger LOG = OkapiLogger.get();
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

  /**
   * Run sql with tuple.
   *
   * <p>The connection is kept open on success and is closed on failure.
   */
  Future<RowSet<Row>> query(String sql, Tuple tuple) {
    return getCon().compose(x -> x.preparedQuery(sql).execute(tuple))
        .onFailure(e -> {
          LOG.error(e.getMessage(), e);
          close();
        });
  }

  /**
   * Run sql with tuples.
   *
   * <p>The connection is kept open on success and is closed on failure.
   */
  Future<RowSet<Row>> query(String sql, List<Tuple> tuples) {
    return getCon().compose(x -> x.preparedQuery(sql).executeBatch(tuples))
        .onFailure(e -> {
          LOG.error(e.getMessage(), e);
          close();
        });
  }

  /**
   * Run sql.
   *
   * <p>The connection is kept open on success and is closed on failure.
   */
  Future<RowSet<Row>> query(String sql) {
    return getCon().compose(x -> x.query(sql).execute())
        .onFailure(e -> {
          LOG.error(e.getMessage(), e);
          close();
        });
  }

  void close() {
    if (conn != null) {
      conn.close();
      conn = null;
    }
  }
}
