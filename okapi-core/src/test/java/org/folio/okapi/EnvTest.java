package org.folio.okapi;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.common.OkapiLogger;
import static org.hamcrest.Matchers.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class EnvTest {

  private final Logger logger = OkapiLogger.get();
  private Vertx vertx;
  private HttpClient httpClient;
  private static final String LS = System.lineSeparator();
  private String locationSampleDeployment1;
  private final int port = 9230;
  private static RamlDefinition api;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
  }

  @Before
  public void setUp(TestContext context) {
    logger.debug("starting EnvTest");
    vertx = Vertx.vertx();
    httpClient = vertx.createHttpClient();

    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", Integer.toString(port)));

    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
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
    vertx.close(x -> {
      async.complete();
    });
  }

  @Test
  public void test1() {
    RestAssured.port = port;

    RestAssuredClient c;
    Response r;

    c = api.createRestAssured3();
    c.given().get("/_/env").then().statusCode(200).body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/env/name1").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String missingNameDoc = "{" + LS
      + "  \"value\" : \"value1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(missingNameDoc).post("/_/env")
      .then().statusCode(400);

    final String missingValueDoc = "{" + LS
      + "  \"name\" : \"name1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(missingValueDoc).post("/_/env")
      .then().statusCode(400);

    final String badDoc = "{" + LS
      + "  \"name\" : \"BADJSON\"," + LS // the comma here makes it bad json!
      + "}";
    c = api.createRestAssured3();
    c.given()
            .header("Content-Type", "application/json")
            .body(badDoc).post("/_/env")
            .then().statusCode(400);

    final String doc = "{" + LS
            + "  \"name\" : \"name1\"," + LS
            + "  \"value\" : \"value1\"" + LS
            + "}";

    c = api.createRestAssured3();
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

    c = api.createRestAssured3();
    c.given().get(locationName1)
            .then()
            .statusCode(200)
            .body(equalTo(doc));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/env").then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().delete(locationName1)
            .then()
            .statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get(locationName1).then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().delete(locationName1)
      .then()
      .statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/env").then().statusCode(200).body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

  }

  @Test
  public void test2() {
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RestAssuredClient c;
    Response r;
    // add Env entry
    final String doc = "{" + LS
            + "  \"name\" : \"helloGreeting\"," + LS
            + "  \"value\" : \"hejsa\"" + LS
            + "}";
    c = api.createRestAssured3();
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
    // deploy module
    final String docSampleDeployment = "{" + LS
      + "  \"srvcId\" : \"sample-module-1.0.0\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleDeployment).post("/_/deployment/modules")
            .then()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    locationSampleDeployment1 = r.getHeader("Location");
    // proxy module
    final String docSampleModule1 = "{" + LS
      + "  \"id\" : \"sample-module-1.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/testb\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"env\" : [ {" + LS
      + "    \"name\" : \"DB_HOST\"," + LS
      + "    \"description\" : \"PostgreSQL host\"," + LS
      + "    \"value\" : \"localhost\"" + LS
      + "  }, {" + LS
      + "    \"name\" : \"DB_PORT\"," + LS
      + "    \"description\" : \"PostgreSQL port\"," + LS
      + "    \"value\" : \"5432\"" + LS
      + "  } ]," + LS
      + "  \"metadata\" : {" + LS
      + "    \"scm\" : \"https://github.com/folio-org/mod-something\"," + LS
      + "    \"language\" : \"java\"" + LS
      + "  }," + LS
      + "  \"requires\" : [ ]" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleModule1).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(
            "raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    // add tenant
    final String docTenantRoskilde = "{" + LS
            + "  \"id\" : \"" + okapiTenant + "\"," + LS
            + "  \"name\" : \"" + okapiTenant + "\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
            + "}";
    c = api.createRestAssured3();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docTenantRoskilde).post("/_/proxy/tenants")
            .then().statusCode(201)
            .body(equalTo(docTenantRoskilde))
            .extract().response();
    Assert.assertTrue(
            "raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationTenantRoskilde = r.getHeader("Location");
    // associate tenant for module
    final String docEnableSample = "{" + LS
      + "  \"id\" : \"sample-module-1.0.0\"" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(201)
            .body(equalTo(docEnableSample)).extract().response();
    Assert.assertTrue(
            "raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationTenantModule = r.getHeader("Location");

    // run module
    c = api.createRestAssured3();
    c.given().header("X-Okapi-Tenant", okapiTenant)
            .body("Okapi").post("/testb")
            .then().statusCode(200)
      .body(equalTo("hejsa Okapi"));
  }
}
