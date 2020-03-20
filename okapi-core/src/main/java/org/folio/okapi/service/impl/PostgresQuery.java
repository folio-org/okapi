package org.folio.okapi.service.impl;

import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;

import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

@java.lang.SuppressWarnings({"squid:S1192"})
public class PostgresQuery {

  private SqlConnection conn;
  private static Logger logger = OkapiLogger.get();
  private final PostgresHandle pg;

  public PostgresQuery(PostgresHandle pg) {
    this.pg = pg;
    this.conn = null;
  }

  private void getCon(Handler<ExtendedAsyncResult<Void>> fut) {
    if (conn != null) {
      fut.handle(new Success<>());
      return;
    }
    pg.getConnection(res -> {
      if (res.failed()) {
        logger.fatal("getCon failed {}", res.cause().getMessage());
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      conn = res.result();
      fut.handle(new Success<>());
    });
  }

  public void query(String sql, Tuple tuple,
    Handler<ExtendedAsyncResult<RowSet<Row>>> fut) {

    getCon(gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      logger.debug("preparedQuery sql {}", sql);
      conn.preparedQuery(sql, tuple, qres -> {
        if (qres.failed()) {
          logger.fatal("preparedQuery sql {} failed: {}",
            sql, qres.cause().getMessage());
          close();
          fut.handle(new Failure<>(ErrorType.INTERNAL, qres.cause()));
          return;
        }
        fut.handle(new Success<>(qres.result()));
      });
    });
  }

  public void query(String sql,
    Handler<ExtendedAsyncResult<RowSet<Row>>> fut) {

    getCon(gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      logger.debug("query sql {}", sql);
      conn.query(sql, qres -> {
        if (qres.failed()) {
          logger.fatal("query sql {} failed: {}",
            sql, qres.cause().getMessage());
          close();
          fut.handle(new Failure<>(ErrorType.INTERNAL, qres.cause()));
        } else {
          fut.handle(new Success<>(qres.result()));
        }
      });
    });
  }

  public void close() {
    if (conn != null) {
      conn.close();
      conn = null;
    }
  }
}
