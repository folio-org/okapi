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

/**
 * Postgres interface for Okapi.
 *
 * Before using postgres, you need to have it installed and running. You
 * also need to define a database, a database user, and its password.
 * For development on a Debian system, you can do the following:
 *   sudo -u postgres -i
 *   createuser -P okapi   # When it asks for a password, enter okapi25
 *   createdb -O okapi okapi
 * The values 'okapi', 'okapi25', and 'okapi' are defaults intended for
 * development use only. In real production, some DBA will have to set up
 * a proper database and its parameters. Pass these to Okapi with the
 * -D command line options, for example
 *   -D postgres_url=jdbc:postgresql://localhost:5432/okapi
 *   -D postgres_user=okapi
 *   -D postgres_password=okapi25
 *   -D postgres_database=okapi
 *
 * TODO - This is not the right place for these instructions!
 *
 * To exercise okapi using psql be sure to use the same kind
 * of connection.. If not, the server might use peer authentication (unix
 * passwords) rather than md5 auth.
 *   psql -U okapi postgresql://localhost:5432/okapi
 *
 * See /etc/postgresql/version/main/pg_hba.conf
 *
 */
public class PostgresHandle {

  private AsyncSQLClient cli;
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private boolean dropdb = false;

  /**
   *   Little helper to get a config value.
   * First from System (-D on command line),
   * then from config (from the way the verticle gets deployed, e.g. in tests)
   * finally a default value.
   * @param key   - Name of the config
   * @param def   - Default value
   * @param conf  - configs to take from
   * @return the value
   */
  private String getSysConf(String key, String def, JsonObject conf) {
    String v = System.getProperty(key, conf.getString(key, def));
    return v;
  }

  public PostgresHandle(Vertx vertx, JsonObject conf) {
    JsonObject pgconf = new JsonObject();
    String val = getSysConf("postgres_url", "", conf);
    if (!val.isEmpty()) {
      pgconf.put("url", val);
    }
    val = getSysConf("postgres_user", "okapi", conf);
    if (!val.isEmpty()) {
      pgconf.put("username", val);
    }
    val = getSysConf("postgres_password", "okapi25", conf);
    if (!val.isEmpty()) {
      pgconf.put("password", val);
    }
    val = getSysConf("postgres_database", "okapi", conf);
    if (!val.isEmpty()) {
      pgconf.put("database", val);
    }
    String db_init = getSysConf("postgres_db_init", "0", conf);
    if ("1".equals(db_init)) {
      logger.warn("Will initialize the whole database!");
      logger.warn("The postgres_db_init option is DEPRECATED!"
        + " use 'initdatabase' command (instead of 'dev' on the command line)");
      this.dropdb = true;
      // TODO - Drop the whole dropdb flag, when the time ready
    }

    logger.debug("Connecting to postgres with " + pgconf.encode());
    cli = PostgreSQLClient.createNonShared(vertx, pgconf);
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

  public boolean getDropDb() {
    return dropdb;
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
