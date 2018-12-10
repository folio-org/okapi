package org.folio.okapi;

import io.restassured.RestAssured;
import static io.restassured.RestAssured.given;
import org.folio.okapi.MainVerticle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class LogLevelTest {

  private Vertx vertx;

  private final int port = 9230;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", Integer.toString(port)));
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    Async async = context.async();
    vertx.close(x -> {
      async.complete();
    });
  }

  @Test
  public void testLogLevel() {
    RestAssured.port = port;

    String currentLevel = given().get("/_/test/loglevel").then()
      .assertThat().statusCode(200).extract().body().asString();

    String trace = "{\"level\":\"TRACE\"}";
    given().header("Content-Type", "application/json")
      .body(trace)
      .post("/_/test/loglevel").then()
      .assertThat().statusCode(200);

    given().get("/_/test/loglevel").then()
      .assertThat().statusCode(200);

    given() // Put the level back to what it was
      .header("Content-Type", "application/json")
      .body(currentLevel)
      .post("/_/test/loglevel").then()
      .assertThat().statusCode(200);

  }
}
