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
import org.folio.okapi.common.Config;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/*
 * PostgreSQL interface for Okapi.
 *
 * Before using PostgreSQL, you need to have it installed and running. You
 * also need to define a database, a database user, and its password.
 * For development on a Debian system, you can do the following:
 *   sudo -u postgres -i
 *   createuser -P okapi   # When it asks for a password, enter okapi25
 *   createdb -O okapi okapi
 * The values 'okapi', 'okapi25', and 'okapi' are defaults intended for
 * development use only. In real production, some DBA will have to set up
 * a proper database and its parameters.
 *
 * To exercise okapi using psql be sure to use the same kind of connection.
 * If not, the server might use peer authentication (unix passwords) rather
 * than md5 auth.
 *
 *   psql -U okapi postgresql://localhost:5432/okapi
 *
 * See /etc/postgresql/version/main/pg_hba.conf
 *
 */
public class PostgresHandle {

  private AsyncSQLClient cli;
  private final Logger logger = LoggerFactory.getLogger("okapi");

  public PostgresHandle(Vertx vertx, JsonObject conf) {
    JsonObject pgconf = new JsonObject();
    String val;

    val = Config.getSysConf("postgres_host", "", conf);
    if (!val.isEmpty()) {
      pgconf.put("host", val);
    }
    val = Config.getSysConf("postgres_port", "", conf);
    if (!val.isEmpty()) {
      try {
        Integer x = Integer.parseInt(val);
        pgconf.put("port", x);
      } catch (NumberFormatException e) {
        logger.warn("Bad postgres_port value: " + val + ": " + e.getMessage());
      }
    }
    val = Config.getSysConf("postgres_username", Config.getSysConf("postgres_user", "okapi", conf), conf);
    if (!val.isEmpty()) {
      pgconf.put("username", val);
    }
    val = Config.getSysConf("postgres_password", "okapi25", conf);
    if (!val.isEmpty()) {
      pgconf.put("password", val);
    }
    val = Config.getSysConf("postgres_database", "okapi", conf);
    if (!val.isEmpty()) {
      pgconf.put("database", val);
    }
    logger.debug("Connecting to postgres with " + pgconf.encode());
    cli = PostgreSQLClient.createNonShared(vertx, pgconf);
    logger.debug("created");
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

  public void closeConnection(SQLConnection conn) {
    conn.close(cres -> {
      if (cres.failed()) {
        logger.fatal("Closing handle failed: "
                + cres.cause().getMessage());
      }
    });
  }


}
