package org.folio.okapi.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.IOException;
import org.assertj.core.api.WithAssertions;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.util.PgTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

@Timeout(5000)
@ExtendWith(VertxExtension.class)
class PostgresHandleTest extends PgTestBase implements WithAssertions {

  static final String KEY_PATH = "/var/lib/postgresql/data/server.key";
  static final String CRT_PATH = "/var/lib/postgresql/data/server.crt";
  static final String CONF_PATH = "/var/lib/postgresql/data/postgresql.conf";
  static final String CONF_BAK_PATH = "/var/lib/postgresql/data/postgresql.conf.bak";
  static String serverCrt;

  static void exec(String... command) {
    try {
      ExecResult execResult = POSTGRESQL_CONTAINER.execInContainer(command);
      OkapiLogger.get().debug(() -> String.join(" ", command) + " " + execResult);
    } catch (InterruptedException | IOException | UnsupportedOperationException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Append each entry to postgresql.conf and reload it into postgres.
   * Appending a key=value entry has precedence over any previous entries of the same key.
   */
  static void configure(String... configEntries) {
    exec("cp", "-p", CONF_BAK_PATH, CONF_PATH);  // start with unaltered config
    for (String configEntry : configEntries) {
      exec("sh", "-c", "echo '" + configEntry + "' >> " + CONF_PATH);
    }
    exec("su-exec", "postgres", "pg_ctl", "reload");
  }

  @BeforeAll
  static void beforeAll() {
    MountableFile serverKeyFile = MountableFile.forClasspathResource("server.key");
    MountableFile serverCrtFile = MountableFile.forClasspathResource("server.crt");
    POSTGRESQL_CONTAINER.copyFileToContainer(serverKeyFile, KEY_PATH);
    POSTGRESQL_CONTAINER.copyFileToContainer(serverCrtFile, CRT_PATH);
    exec("chown", "postgres.postgres", KEY_PATH, CRT_PATH);
    exec("chmod", "400", KEY_PATH, CRT_PATH);
    exec("cp", "-p", CONF_PATH, CONF_BAK_PATH);

    serverCrt = getResource("server.crt");
    OkapiLogger.get().debug(() -> config().encodePrettily());
  }

  @AfterAll
  static void afterAll() {
    configure();  // restore and reload original config
  }

  @Test
  void hostAndPort(Vertx vertx) {
    PostgresHandle postgresHandle = new PostgresHandle(vertx,
        new JsonObject().put("postgres_host", "example.com").put("postgres_port", "9876"));
    assertThat(postgresHandle.getOptions().getHost()).isEqualTo("example.com");
    assertThat(postgresHandle.getOptions().getPort()).isEqualTo(9876);
  }

  @Test
  void ignoreInvalidPortNumber(Vertx vertx) {
    PostgresHandle postgresHandle = new PostgresHandle(vertx,
        new JsonObject().put("postgres_port", "q"));
    assertThat(postgresHandle.getOptions().getHost()).isEqualTo("localhost");
    assertThat(postgresHandle.getOptions().getPort()).isEqualTo(5432);
  }

  static private JsonObject config() {
    return new JsonObject()
        .put("postgres_host", POSTGRESQL_CONTAINER.getHost())
        .put("postgres_port", POSTGRESQL_CONTAINER.getFirstMappedPort() + "")
        .put("postgres_database", POSTGRESQL_CONTAINER.getDatabaseName())
        .put("postgres_username", POSTGRESQL_CONTAINER.getUsername())
        .put("postgres_password", POSTGRESQL_CONTAINER.getPassword())
        .put("postgres_server_pem", serverCrt);
  }

  @Test
  @DisplayName("Basic connectivity test without encryption")
  @SuppressWarnings("java:S2699")  // suppress "Tests should include assertions"
  void connectWithoutSsl(Vertx vertx, VertxTestContext vtc) {
    configure("ssl = off");
    JsonObject config = config();
    config.remove("postgres_server_pem");
    new PostgresHandle(vertx, config).getConnection().onComplete(vtc.succeedingThenComplete());
  }

  @Test
  void rejectWithoutSsl(Vertx vertx, VertxTestContext vtc) {
    configure("ssl = off");
    new PostgresHandle(vertx, config()).getConnection().onComplete(vtc.failing(fail -> vtc.completeNow()));
  }

  @Test
  void tlsv1_3(Vertx vertx, VertxTestContext vtc) {
    configure("ssl = on");
    new PostgresHandle(vertx, config()).getConnection().onComplete(vtc.succeeding(connection -> vtc.verify(() -> {
      assertThat(connection.isSSL()).isTrue();
      String sql = "SELECT version FROM pg_stat_ssl WHERE pid = pg_backend_pid()";
      connection.query(sql).execute(vtc.succeeding(rowset -> vtc.verify(() -> {
        assertThat(rowset.iterator().next().getString(0)).isEqualTo("TLSv1.3");
        vtc.completeNow();
      })));
    })));
  }

  @Test
  void rejectTlsv1_2(Vertx vertx, VertxTestContext vtc) {
    configure("ssl = on", "ssl_min_protocol_version = TLSv1.2", "ssl_max_protocol_version = TLSv1.2");
    new PostgresHandle(vertx, config()).getConnection().onComplete(vtc.failing(fail -> vtc.completeNow()));
  }

  @Test
  void scram256(Vertx vertx, VertxTestContext vtc) {
    configure("ssl = on", "password_encryption = scram-sha-256");
    new PostgresHandle(vertx, config()).getConnection()
        .compose(connection -> {
          String sql = "ALTER USER " + POSTGRESQL_CONTAINER.getUsername()
              + " WITH PASSWORD '" + POSTGRESQL_CONTAINER.getPassword() + "';";
          return connection.query(sql).execute().mapEmpty();
        })
        .compose(x -> new PostgresHandle(vertx, config()).getConnection())
        .onComplete(vtc.succeedingThenComplete());
  }
}
