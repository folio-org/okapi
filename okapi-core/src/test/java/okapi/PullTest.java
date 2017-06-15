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
import io.vertx.core.http.HttpClient;
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
public class PullTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  Vertx vertx;
  private HttpClient httpClient;

  private static final String LS = System.lineSeparator();
  private String locationSampleDeployment1;
  private String locationSampleDeployment2;
  private String locationSampleModule;
  private String locationSampleModule2;
  private String vert1;
  private String vert2;
  private final int port = 9131;

  public PullTest() {
  }

  private void otherDeploy(TestContext context, Async async) {
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", "9131"));
    vertx.deployVerticle(MainVerticle.class.getName(), opt, res -> {
      if (res.failed()) {
        context.fail(res.cause());
      } else {
        vert2 = res.result();
        async.complete();
      }
    });
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    Async async = context.async();
    httpClient = vertx.createHttpClient();
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", "9130"));
    vertx.deployVerticle(MainVerticle.class.getName(), opt, res -> {
      if (res.failed()) {
        context.fail(res.cause());
      } else {
        vert1 = res.result();
        otherDeploy(context, async);
      }
    });
  }

  @After
  public void tearDown(TestContext context) {
    td(context, context.async());
  }

  private void td(TestContext context, Async async) {
    if (locationSampleDeployment1 != null) {
      httpClient.delete(port, "localhost", locationSampleDeployment1, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSampleDeployment1 = null;
          td(context, async);
        });
      }).end();
      return;
    }
    if (locationSampleDeployment2 != null) {
      httpClient.delete(port, "localhost", locationSampleDeployment2, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSampleDeployment2 = null;
          td(context, async);
        });
      }).end();
      return;
    }
    if (vert1 != null) {
      vertx.undeploy(vert1, res -> {
        logger.info("vert1 undeployed");
        vert1 = null;
        td(context, async);
      });
      return;
    }
    if (vert2 != null) {
      vertx.undeploy(vert2, res -> {
        logger.info("vert2 undeployed");
        vert2 = null;
        td(context, async);
      });
      return;
    }
    vertx.close(x -> {
      logger.info("vertx.close completed");
      async.complete();
    });
  }

  @Test
  public void test1() {
    RestAssured.port = port;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;
    Response r;

    c = api.createRestAssured();
    c.given().get("/_/env").then().statusCode(200).body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }

  @Test
  public void test2() {
    RestAssured.port = port;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");
    RestAssuredClient c;
    Response r;

    final String docModuleA = "{" + LS
      + "  \"id\" : \"module-a-1.0.0\"," + LS
      + "  \"name\" : \"A\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"int-a\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docModuleA).post("/_/proxy/modules").then().statusCode(201).extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docModuleB = "{" + LS
      + "  \"id\" : \"module-b-1.0.0\"," + LS
      + "  \"name\" : \"B\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"int-b\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ {" + LS
      + "    \"id\" : \"int-a\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docModuleB).post("/_/proxy/modules").then().statusCode(201).extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docModuleC = "{" + LS
      + "  \"id\" : \"module-c-1.0.0\"," + LS
      + "  \"name\" : \"C\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"int-c\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ {" + LS
      + "    \"id\" : \"int-b\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docModuleC).post("/_/proxy/modules").then().statusCode(201).extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }
}
