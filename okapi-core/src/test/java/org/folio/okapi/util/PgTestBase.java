package org.folio.okapi.util;

import io.vertx.pgclient.PgConnectOptions;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton PostgreSQL instance from testcontainers.org.
 */
public abstract class PgTestBase extends TestBase {
  public static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER;

  static {
    POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("postgres:12-alpine");
    POSTGRESQL_CONTAINER.start();
  }

  public static PgConnectOptions getPgConnectOptions() {
    return new PgConnectOptions()
        .setPort(POSTGRESQL_CONTAINER.getFirstMappedPort())
        .setHost(POSTGRESQL_CONTAINER.getHost())
        .setDatabase(POSTGRESQL_CONTAINER.getDatabaseName())
        .setUser(POSTGRESQL_CONTAINER.getUsername())
        .setPassword(POSTGRESQL_CONTAINER.getPassword());
  }
}
