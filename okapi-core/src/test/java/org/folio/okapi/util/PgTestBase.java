package org.folio.okapi.util;

import io.vertx.pgclient.PgConnectOptions;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton PostgreSQL instance from testcontainers.org.
 */
public abstract class PgTestBase extends TestBase {
  private static final String DEFAULT_POSTGRESQL_IMAGE_NAME = "postgres:16-alpine";
  private static final String POSTGRESQL_IMAGE_NAME =
      System.getenv().getOrDefault("TESTCONTAINERS_POSTGRES_IMAGE", DEFAULT_POSTGRESQL_IMAGE_NAME);
  /**
   * Testcontainers startup config workaround for podman:
   * https://github.com/testcontainers/testcontainers-java/issues/6640#issuecomment-1431636203
   */
  private static final int STARTUP_ATTEMPTS = 3;
  public static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = createPostgreSQLContainer();

  public static PostgreSQLContainer<?> createPostgreSQLContainer() {
    var container = new PostgreSQLContainer<>(POSTGRESQL_IMAGE_NAME)
        .withStartupAttempts(STARTUP_ATTEMPTS);
    container.start();
    return container;
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
