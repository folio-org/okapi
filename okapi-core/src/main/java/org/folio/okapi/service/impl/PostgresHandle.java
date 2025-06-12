package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.SslMode;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnection;
import java.util.Collections;
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
  private final Pool pool;

  PostgresHandle(Vertx vertx, JsonObject conf) {
    String val;

    connectOptions = new PgConnectOptions();
    val = Config.getSysConf("postgres_host", null, conf);
    if (val != null) {
      connectOptions.setHost(val);
    }
    val = Config.getSysConf("postgres_port", null, conf);
    Logger logger = OkapiLogger.get();
    if (val != null) {
      try {
        connectOptions.setPort(Integer.parseInt(val));
      } catch (NumberFormatException e) {
        logger.warn("Bad postgres_port value: {}: {}", val, e.getMessage());
      }
    }

    // postgres_user is supported for system configuration (-D option) only and is deprecated
    connectOptions.setUser(Config.getSysConf("postgres_username",
        Config.getSysConf("postgres_user", "okapi", new JsonObject()), conf));
    connectOptions.setPassword(Config.getSysConf("postgres_password", "okapi25", conf));
    connectOptions.setDatabase(Config.getSysConf("postgres_database", "okapi", conf));
    String serverPem = Config.getSysConf("postgres_server_pem", null, conf);
    if (serverPem != null) {
      logger.debug("Enforcing SSL encryption for PostgreSQL connections, "
          + "requiring TLSv1.3 with server name certificate");
      connectOptions.setSslMode(SslMode.VERIFY_FULL);
      ClientSSLOptions cso = new ClientSSLOptions();
      cso.setHostnameVerificationAlgorithm("HTTPS");
      cso.setTrustOptions(
          new PemTrustOptions().addCertValue(Buffer.buffer(serverPem)));
      cso.setEnabledSecureTransportProtocols(Collections.singleton("TLSv1.3"));
      connectOptions.setSslOptions(cso);
    }

    PoolOptions poolOptions = new PoolOptions();
    poolOptions.setMaxSize(5);

    pool = PgBuilder.pool().using(vertx).connectingTo(connectOptions).with(poolOptions).build();
    logger.debug("created");
  }

  PgConnectOptions getOptions() {
    return connectOptions;
  }

  public Future<SqlConnection> getConnection() {
    return pool.getConnection();
  }

  public PostgresQuery getQuery() {
    return new PostgresQuery(this);
  }

}
