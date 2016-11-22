package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.SQLConnection;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

public class PostgresHandle {

  private AsyncSQLClient cli;
  private final Logger logger = LoggerFactory.getLogger("okapi");

  public PostgresHandle(Vertx vertx, JsonObject conf) {
    // JsonObject postgreSQLClientConfig = new JsonObject();
    cli = PostgreSQLClient.createNonShared(vertx, conf);
    logger.info("PostgresHandle created");
  }

  public void getConnection(Handler<ExtendedAsyncResult<SQLConnection>> fut) {
    cli.getConnection(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        SQLConnection con = res.result();
        fut.handle(new Success<>(con));
      }
    });
  }
}
