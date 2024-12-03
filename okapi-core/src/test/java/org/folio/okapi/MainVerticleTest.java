package org.folio.okapi;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class MainVerticleTest {

  @BeforeAll
  static void startMainVerticle(Vertx vertx, VertxTestContext vtc) {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    vertx.deployVerticle(new MainVerticle())
        .onComplete(vtc.succeedingThenComplete());
  }

  @Test
  void version200() {
    get("http://localhost:9130/_/version")
        .then()
        .statusCode(200);
  }

  @Test
  void uriTooLong414() {
    get("http://localhost:9130/_/version?" + "x".repeat(8200))
        .then()
        .statusCode(414)
        .body(is("Your request URI is too long."));
  }

  @Test
  void headerTooLarge431() {
    given()
        .header("X-Foo", "x".repeat(8200))
        .when()
        .get("http://localhost:9130/_/version")
        .then()
        .statusCode(431)
        .body(is("Your HTTP request header fields are too large."));
  }
}
