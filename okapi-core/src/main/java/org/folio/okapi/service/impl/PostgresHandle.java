package org.folio.okapi.service.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnection;
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

  private final PgConnectOptions connectOptions;
  private final PoolOptions poolOptions;
  private final Vertx vertx;

  protected PostgresHandle(Vertx vertx, JsonObject conf) {
    this.vertx = vertx;
    String val;

    connectOptions = new PgConnectOptions();
    val = Config.getSysConf("postgres_host", "", conf);
    if (!val.isEmpty()) {
      connectOptions.setHost(val);
    }
    val = Config.getSysConf("postgres_port", "", conf);
    Logger logger = OkapiLogger.get();
    if (!val.isEmpty()) {
      try {
        connectOptions.setPort(Integer.parseInt(val));
      } catch (NumberFormatException e) {
        logger.warn("Bad postgres_port value: {}: {}", val, e.getMessage());
      }
    }
    connectOptions.setUser(Config.getSysConf("postgres_username",
      Config.getSysConf("postgres_user", "okapi", new JsonObject()), conf));
    connectOptions.setPassword(Config.getSysConf("postgres_password", "okapi25", conf));
    connectOptions.setDatabase(Config.getSysConf("postgres_database", "okapi", conf));

    poolOptions = new PoolOptions();
    poolOptions.setMaxSize(5);

    logger.debug("created");
  }

  PgConnectOptions getOptions() {
    return connectOptions;
  }

  public void getConnection(Handler<AsyncResult<SqlConnection>> con) {
    PgPool.pool(vertx, connectOptions, poolOptions).getConnection(con);
  }

  public PostgresQuery getQuery() {
    return new PostgresQuery(this);
  }

}
