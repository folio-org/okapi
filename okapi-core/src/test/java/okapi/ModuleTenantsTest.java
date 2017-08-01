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
public class ModuleTenantsTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  Vertx vertx;
  private HttpClient httpClient;

  private static final String LS = System.lineSeparator();
  private String locationBasicDeployment1;
  private String locationBasicDeployment2;
  private String locationSampleDeployment1;
  private String locationSampleDeployment2;
  private String locationSampleModule;
  private String locationSampleModule2;
  private final int port = Integer.parseInt(System.getProperty("port", "9130"));

  public ModuleTenantsTest() {
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
    if (locationBasicDeployment1 != null) {
      httpClient.delete(port, "localhost", locationBasicDeployment1, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationBasicDeployment1 = null;
          td(context, async);
        });
      }).end();
      return;
    }
    if (locationBasicDeployment2 != null) {
      httpClient.delete(port, "localhost", locationBasicDeployment2, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationBasicDeployment2 = null;
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
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");
    RestAssuredClient c;
    Response r;

    // deploy basic 0.9.0
    final String docBasicDeployment1 = "{" + LS
      + "  \"srvcId\" : \"basic-module-0.9.0\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasicDeployment1).post("/_/deployment/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationBasicDeployment1 = r.getHeader("Location");

    // deploy basic 1.0.0
    final String docBasicDeployment2 = "{" + LS
      + "  \"srvcId\" : \"basic-module-1.0.0\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasicDeployment2).post("/_/deployment/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationBasicDeployment2 = r.getHeader("Location");

    // create basic 0.9.0
    final String docBasidModule1 = "{" + LS
      + "  \"id\" : \"basic-module-0.9.0\"," + LS
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
      + "    \"id\" : \"bint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasidModule1).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationBasicModule1 = r.getHeader("Location");

    // create basic 1.0.0
    final String docBasidModule2 = "{" + LS
      + "  \"id\" : \"basic-module-1.0.0\"," + LS
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
      + "    \"id\" : \"bint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasidModule2).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationBasicModule2 = r.getHeader("Location");

    // deploy sample 1.0.0
    final String docSampleDeployment = "{" + LS
      + "  \"srvcId\" : \"sample-module-1.0.0\"," + LS
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
    // create sample 1.0.0
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
      + "  \"requires\" : [ ]" + LS
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
      + "  \"id\" : \"sample-module-1.0.0\"" + LS
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
    String locationTenantModule = r.getHeader("Location");

    // run module
    c = api.createRestAssured();
    c.given().header("X-Okapi-Tenant", okapiTenant)
      .body("Okapi").post("/testb")
      .then().statusCode(200)
      .body(equalTo("Hello Okapi"));

    // create sample 1.2.0
    final String docSampleModule2 = "{" + LS
      + "  \"id\" : \"sample-module-1.2.0\"," + LS
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
      + "  \"requires\" : [ { \"id\" : \"bint\", \"version\" : \"1.0\" } ]" + LS
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

    // deploy sample 1.2.0
    final String docSampleDeployment2 = "{" + LS
      + "  \"srvcId\" : \"sample-module-1.2.0\"," + LS
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

    // Upgrade from sample 1.0.0 to 1.2.0
    // supply the new ID in the body and the old ID in the header.
    final String docEnableSample2 = "{" + LS
      + "  \"id\" : \"sample-module-1.2.0\"" + LS
      + "}";
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableSample2).post(locationTenantModule)
      .then().statusCode(400); // FAIL because bint is not there
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // remedy the situation by enabling basic 1.0.0 which provides bint
    final String docEnableBasic = "{" + LS
      + "  \"id\" : \"basic-module-1.0.0\"" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docEnableBasic).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .body(equalTo(docEnableBasic)).extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    String locationBasicTenantModule = r.getHeader("Location");

    // Upgrade from sample 1.0.0 to sample 1.2.0
    // supply the new ID in the body and the old ID in the header.
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
    locationTenantModule = r.getHeader("Location");

    // run new module
    c = api.createRestAssured();
    c.given().header("X-Okapi-Tenant", okapiTenant)
      .body("Okapi").post("/testb")
      .then().statusCode(200)
      .body(equalTo("Hello Okapi"));

    // undeploy basic should not work because it is used by sample 1.2.0
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .delete(locationBasicTenantModule)
      .then()
      .statusCode(400)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // undeploy sample 1.2.0 is OK
    c = api.createRestAssured();
    r = c.given()
      .delete(locationTenantModule)
      .then()
      .statusCode(204)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // undeploy basic is OK now
    c = api.createRestAssured();
    r = c.given()
      .delete(locationBasicTenantModule)
      .then()
      .statusCode(204)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // it's now gone
    c = api.createRestAssured();
    c.given().header("X-Okapi-Tenant", okapiTenant)
      .body("Okapi").post("/testb")
      .then().statusCode(404);

    // nothing left
    c = api.createRestAssured();
    c.given()
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // enable modules -- wrong type
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("{ \"foo\" : \"bar\"}").post("/_/proxy/tenants/" + okapiTenant + "/upgrade")
      .then().statusCode(400);

    // enable modules: post unknown module
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-foo-1.2.3\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // enable modules: post known module
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // we simulated above.. Now actually insert it with old style enable
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .body(equalTo(docEnableSample)).extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationTenantModule = r.getHeader("Location");

    // enable modules again: post known module
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"uptodate\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // upgrade from 1.0.0 to 1.2.0 - post known module which require basic
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.2.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.2.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // enable basic-module-1.0.0 again
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docEnableBasic).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .body(equalTo(docEnableBasic)).extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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
    locationTenantModule = r.getHeader("Location");

    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.0.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.2.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.2.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"basic-module-1.0.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-module-1.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"sample-module-1.2.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }
}
