package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

@java.lang.SuppressWarnings({"squid:S1192"})
public class PostgresQuery {

  private SQLConnection conn;
  private static Logger logger = LoggerFactory.getLogger("okapi");
  private PostgresHandle pg;

  public PostgresQuery(PostgresHandle pg) {
    this.pg = pg;
    this.conn = null;
  }

  private void getCon(Handler<ExtendedAsyncResult<Void>> fut) {
    if (conn != null) {
      fut.handle(new Success<>());
    } else {
      pg.getConnection(res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          conn = res.result();
          fut.handle(new Success<>());
        }
      });
    }
  }

  public void queryWithParams(String sql, JsonArray jsa,
    Handler<ExtendedAsyncResult<ResultSet>> fut) {

    getCon(gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        logger.debug("queryWithParams sql: " + sql);
        conn.queryWithParams(sql, jsa, qres -> {
          if (qres.failed()) {
            logger.fatal("queryWithParams failed: "
              + qres.cause().getMessage() + " sql: " + sql);
            fut.handle(new Failure<>(INTERNAL, qres.cause()));
            close();
          } else {
            fut.handle(new Success<>(qres.result()));
          }
        });
      }
    });
  }

  public void updateWithParams(String sql, JsonArray jsa,
    Handler<ExtendedAsyncResult<UpdateResult>> fut) {

    getCon(gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        logger.debug("updateWithParams sql: " + sql);
        conn.updateWithParams(sql, jsa, qres -> {
          if (qres.failed()) {
            logger.fatal("updateWithParams failed: "
              + qres.cause().getMessage() + " sql: " + sql);
            fut.handle(new Failure<>(INTERNAL, qres.cause()));
            close();
          } else {
            fut.handle(new Success<>(qres.result()));
          }
        });
      }
    });
  }

  public void query(String sql,
    Handler<ExtendedAsyncResult<ResultSet>> fut) {

    getCon(gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        logger.debug("query sql: " + sql);
        conn.query(sql, qres -> {
          if (qres.failed()) {
            logger.fatal("query failed: "
              + qres.cause().getMessage() + " sql: " + sql);
            fut.handle(new Failure<>(INTERNAL, qres.cause()));
            close();
          } else {
            fut.handle(new Success<>(qres.result()));
          }
        });
      }
    });
  }

  public void close() {
    if (conn != null) {
      conn.close();
      conn = null;
    }
  }
}
