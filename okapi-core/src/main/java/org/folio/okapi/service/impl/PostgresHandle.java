package org.folio.okapi.service.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.SQLConnection;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.OkapiLogger;

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
@java.lang.SuppressWarnings({"squid:S1192"})
class PostgresHandle {

  private AsyncSQLClient cli;

  protected PostgresHandle(Vertx vertx, JsonObject conf) {
    JsonObject pgconf = new JsonObject();
    String val;

    val = Config.getSysConf("postgres_host", "", conf);
    if (!val.isEmpty()) {
      pgconf.put("host", val);
    }
    val = Config.getSysConf("postgres_port", "", conf);
    Logger logger = OkapiLogger.get();
    if (!val.isEmpty()) {
      try {
        Integer x = Integer.parseInt(val);
        pgconf.put("port", x);
      } catch (NumberFormatException e) {
        logger.warn("Bad postgres_port value: {}: {}", val, e.getMessage());
      }
    }
    pgconf.put("username", Config.getSysConf("postgres_username",
      Config.getSysConf("postgres_user", "okapi", new JsonObject()), conf));
    pgconf.put("password", Config.getSysConf("postgres_password", "okapi25", conf));
    pgconf.put("database", Config.getSysConf("postgres_database", "okapi", conf));
    cli = createSQLClient(vertx, pgconf);
    logger.debug("created");
  }

  public void getConnection(Handler<AsyncResult<SQLConnection>> fut) {
    cli.getConnection(fut);
  }

  protected AsyncSQLClient createSQLClient(Vertx vertx, JsonObject pgconf) {
    return PostgreSQLClient.createNonShared(vertx, pgconf);
  }

  public PostgresQuery getQuery() {
    return new PostgresQuery(this);
  }

}
