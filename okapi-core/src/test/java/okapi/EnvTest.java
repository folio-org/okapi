package okapi;

import org.folio.okapi.MainVerticle;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import static org.hamcrest.Matchers.*;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class EnvTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  Vertx vertx;
  private static final String LS = System.lineSeparator();

  public EnvTest() {
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    DeploymentOptions opt = new DeploymentOptions()
            .setConfig(new JsonObject().put("storage", "inmemory"));
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
  public void test1() {
    int port = Integer.parseInt(System.getProperty("port", "9130"));

    RestAssured.port = port;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;
    Response r;

    c = api.createRestAssured();
    c.given().get("/_/env").then().statusCode(200).body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/env/name1").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String badDoc = "{" + LS
            + "  \"name\" : \"mame1\"," + LS // the comma here makes it bad json!
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(badDoc).post("/_/env")
            .then().statusCode(400);

    final String doc = "{" + LS
            + "  \"name\" : \"name1\"," + LS
            + "  \"value\" : \"value1\"" + LS
            + "}";

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc).post("/_/env")
            .then()
            .statusCode(201)
            .body(equalTo(doc))
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    String locationName1 = r.getHeader("Location");
    c = api.createRestAssured();
    c.given().get(locationName1)
            .then()
            .statusCode(200)
            .body(equalTo(doc));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/env").then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(locationName1)
            .then()
            .statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get(locationName1).then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/env").then().statusCode(200).body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
  }
}
