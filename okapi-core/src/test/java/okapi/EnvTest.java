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
public class EnvTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  Vertx vertx;
  private HttpClient httpClient;

  private static final String LS = System.lineSeparator();
  private String locationSampleDeployment1;
  private String locationSampleDeployment2;
  private String locationSampleModule;
  private String locationSampleModule2;
  private final int port = Integer.parseInt(System.getProperty("port", "9130"));

  public EnvTest() {
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    httpClient = vertx.createHttpClient();
    DeploymentOptions opt = new DeploymentOptions()
            .setConfig(new JsonObject().put("storage", "inmemory"));
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
    vertx.close(x -> {
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

  @Test
  public void test2() {
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");
    RestAssuredClient c;
    Response r;
    // add Env entry
    final String doc = "{" + LS
            + "  \"name\" : \"helloGreeting\"," + LS
            + "  \"value\" : \"hejsa\"" + LS
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
    // deploy module
    final String docSampleDeployment = "{" + LS
            + "  \"srvcId\" : \"sample-module1\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";
    c = api.createRestAssured();
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
      + "  \"id\" : \"sample-module1\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"routingEntries\" : [ {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"level\" : \"31\"," + LS
      + "    \"type\" : \"request-response\"" + LS
      + "  } ]," + LS
      + "  \"tenantInterface\" : \"/tenant\"" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleModule1).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(
            "raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    locationSampleModule = r.getHeader("Location");
    // add tenant
    final String docTenantRoskilde = "{" + LS
            + "  \"id\" : \"" + okapiTenant + "\"," + LS
            + "  \"name\" : \"" + okapiTenant + "\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
            + "}";
    c = api.createRestAssured();
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
            + "  \"id\" : \"sample-module1\"" + LS
            + "}";
    c = api.createRestAssured();
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
    c = api.createRestAssured();
    c.given().header("X-Okapi-Tenant", okapiTenant)
            .body("Okapi").post("/testb")
            .then().statusCode(200)
            .body(equalTo("hejsa Okapi"));

    final String docSampleModule2 = "{" + LS
      + "  \"id\" : \"sample-module2\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"routingEntries\" : [ {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"level\" : \"31\"," + LS
      + "    \"type\" : \"request-response\"" + LS
      + "  } ]," + LS
      + "  \"tenantInterface\" : \"/tenant\"" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleModule2).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(
            "raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    locationSampleModule2 = r.getHeader("Location");

    final String docSampleDeployment2 = "{" + LS
            + "  \"srvcId\" : \"sample-module2\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleDeployment2).post("/_/deployment/modules")
            .then()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    locationSampleDeployment2 = r.getHeader("Location");

    final String docEnableSample2 = "{" + LS
            + "  \"id\" : \"sample-module2\"" + LS
            + "}";

    logger.info("locationTenantModule: " + locationTenantModule);
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docEnableSample2).post(locationTenantModule)
            .then()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue(
            "raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
  }
}
