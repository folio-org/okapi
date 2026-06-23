package org.folio.okapi;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledOnOs(architectures = {"amd64", "x86_64"},
    disabledReason = "Dockerfile base image folioci/alpine-jre-openjdk is available for amd64 and x86_64 only")
class OkapiIT {
  private static final Logger LOG = LoggerFactory.getLogger(OkapiIT.class);

  @Container
  @SuppressWarnings("resource")  // closed by @Container annotation
  public static GenericContainer<?> okapi = new GenericContainer<>(
      new ImageFromDockerfile()
      .withFileFromPath("Dockerfile", Path.of("../Dockerfile"))
      .withFileFromPath("okapi-core/target/okapi-core-fat.jar", Path.of("target/okapi-core-fat.jar")))
      .withExposedPorts(9130)
      .withCommand("dev");

  @BeforeAll
  static void beforeAll() {
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + okapi.getHost() + ":" + okapi.getFirstMappedPort();
    okapi.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams());
  }

  @Test
  void health() {
    RestAssured.when()
    .get("/_/proxy/health")
    .then()
    .statusCode(200)
    .body("$", is(empty()));
  }
}
