package okapi;

import org.folio.okapi.MainVerticle;
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
import io.vertx.core.json.JsonArray;
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
public class ModuleTenantsTest {

  private final Logger logger = OkapiLogger.get();
  private Vertx vertx;
  private HttpClient httpClient;
  private static final String LS = System.lineSeparator();
  private final int port = 9230;
  private static RamlDefinition api;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
  }

  @Before
  public void setUp(TestContext context) {
    logger.debug("starting ModuleTenantsTest");
    vertx = Vertx.vertx();
    httpClient = vertx.createHttpClient();
    JsonObject conf = new JsonObject()
      .put("port", Integer.toString(port))
      .put("logWaitMs", "200");

    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);

    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    td(context, context.async());
  }

  private void td(TestContext context, Async async) {
    httpClient.delete(port, "localhost", "/_/discovery/modules", response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        vertx.close(y -> {
          async.complete();
        });
      });
    }).end();
  }

  @Test
  public void test1() {
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RestAssuredClient c;
    Response r;

    // create basic 1.0.0
    final String docBasic_1_0_0 = "{" + LS
      + "  \"id\" : \"basic-module-1.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.1\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant/disable\"" + LS
      + "    }, {" + LS
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
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasic_1_0_0).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationBasic_1_0_0 = r.getHeader("Location");

    // deploy basic 1.0.0
    final String docBasicDeployment_1_0_0 = "{" + LS
      + "  \"srvcId\" : \"basic-module-1.0.0\"," + LS
      + "  \"nodeId\" :  \"localhost\"" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasicDeployment_1_0_0).post("/_/discovery/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // create sample 1.0.0
    final String docSample_1_0_0 = "{" + LS
      + "  \"id\" : \"sample-module-1.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.1\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant/disable\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/testb/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSample_1_0_0).post("/_/proxy/modules").then().statusCode(201)
      .body(equalTo(docSample_1_0_0))
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // deploy sample 1.0.0
    final String docSampleDeployment_1_0_0 = "{" + LS
      + "  \"srvcId\" : \"sample-module-1.0.0\"," + LS
      + "  \"nodeId\" : \"localhost\"" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleDeployment_1_0_0).post("/_/discovery/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
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
    final String docEnableSampleNoSemVer = "{" + LS
      + "  \"id\" : \"sample-module\"" + LS
      + "}";
    final String docEnableSample = "{" + LS
      + "  \"id\" : \"sample-module-1.0.0\"" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docEnableSampleNoSemVer).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .body(equalTo(docEnableSample)).extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    String locationTenantModule = r.getHeader("Location");

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200)
      .body(equalTo("[ " + docEnableSample + " ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .get("/_/proxy/tenants/" + okapiTenant + "/modules?full=true")
      .then().statusCode(200)
      .body(equalTo("[ " + docSample_1_0_0 + " ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // run module
    c = api.createRestAssured3();
    c.given().header("X-Okapi-Tenant", okapiTenant)
      .body("Okapi").post("/testb/foo")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(equalTo("Hello Okapi"));

    c = api.createRestAssured3();
    c.given().header("X-Okapi-Tenant", okapiTenant)
      .body("Okapi").post("/testb/bar")
      .then().log().ifValidationFails()
      .statusCode(404);

    // create sample 1.2.0
    final String docSampleModule_1_2_0 = "{" + LS
      + "  \"id\" : \"sample-module-1.2.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.1\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant/disable\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/testb/foo\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/testb/{id}\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"bint\", \"version\" : \"1.0\" } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule_1_2_0).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // deploy sample 1.2.0
    final String docSampleDeployment_1_2_0 = "{" + LS
      + "  \"srvcId\" : \"sample-module-1.2.0\"," + LS
      + "  \"nodeId\" : \"localhost\"" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleDeployment_1_2_0).post("/_/discovery/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Upgrade with bad JSON.
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{").post(locationTenantModule)
      .then().statusCode(400);

    // Upgrade from sample 1.0.0 to 1.2.0
    // supply the new ID in the body and the old ID in the header.
    final String docEnableSample2 = "{" + LS
      + "  \"id\" : \"sample-module-1.2.0\"" + LS
      + "}";
    c = api.createRestAssured3();
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
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docEnableBasic).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .log().ifValidationFails()
      .statusCode(201)
      .body(equalTo(docEnableBasic)).extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    String locationBasicTenantModule = r.getHeader("Location");

    // Upgrade from sample 1.0.0 to sample 1.2.0
    // supply the new ID in the body and the old ID in the header.
    c = api.createRestAssured3();
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

    // run new module (1st handler)
    c = api.createRestAssured3();
    c.given().header("X-Okapi-Tenant", okapiTenant)
      .body("Okapi").post("/testb/foo")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(equalTo("Hello Okapi"));

    // run new module (2nd handler)
    c = api.createRestAssured3();
    c.given().header("X-Okapi-Tenant", okapiTenant)
      .body("Okapi").post("/testb/someid")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(equalTo("Hello Okapi"));

    // run new module with non-matched URL
    c = api.createRestAssured3();
    c.given().header("X-Okapi-Tenant", okapiTenant)
      .body("Okapi").post("/testb/someid/other")
      .then()
      .log().ifValidationFails()
      .statusCode(404);

    // undeploy basic should not work because it is used by sample 1.2.0
    c = api.createRestAssured3();
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
    c = api.createRestAssured3();
    r = c.given()
      .delete(locationTenantModule)
      .then()
      .statusCode(204)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // undeploy basic is OK now
    c = api.createRestAssured3();
    c.given()
      .delete(locationBasicTenantModule)
      .then()
      .statusCode(204)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // it's now gone
    c = api.createRestAssured3();
    c.given().header("X-Okapi-Tenant", okapiTenant)
      .body("Okapi").post("/testb")
      .then().statusCode(404);

    // nothing left
    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // upgrade service: nothing is installed yet
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true&preRelease=true")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // enable modules -- wrong type
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{ \"foo\" : \"bar\"}").post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(400);

    // enable modules -- bad JSON
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{").post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(400);

    // enable modules: post unknown module with semver
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-foo-1.2.3\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // enable modules: post unknown module without semver
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-foo\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // enable modules: post known module with simulation
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true&preRelease=false")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // make sure sample-module-1.0.0 is not deployed anymore
    // remove with only serviceId given
    // same as locationSampleDeployment_1_0_0
    c = api.createRestAssured3();
    c.given()
      .delete("/_/discovery/modules/sample-module-1.0.0")
      .then()
      .statusCode(204);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // undeploy again
    c = api.createRestAssured3();
    c.given()
      .delete("/_/discovery/modules/sample-module-1.0.0")
      .then()
      .statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // undeploy with unknown serviceId
    c = api.createRestAssured3();
    c.given()
      .delete("/_/discovery/modules/foo")
      .then()
      .statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // undeploy with unknown serviceId and instanceId
    c = api.createRestAssured3();
    c.given()
      .delete("/_/discovery/modules/foo/bar")
      .then()
      .statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // we simulated install earlier .. This time for real
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // enable modules again: post known module
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"uptodate\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // upgrade from 1.0.0 to 1.2.0 - post known module which require basic
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.2.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"sample-module-1.2.0\"," + LS
        + "  \"from\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // simulate upgrade modules: post module without version (product only)
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"sample-module-1.2.0\"," + LS
        + "  \"from\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // upgrade service: will return same as previous one
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=false&deploy=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"sample-module-1.2.0\"," + LS
        + "  \"from\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // upgrade service: nothing to be done
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=false&deploy=true")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // create sample 1.3.0
    final String docSampleModule_1_3_0 = "{" + LS
      + "  \"id\" : \"sample-module-1.3.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.1\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant/disable\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/testb/foo\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/testb/{id}\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"bint\", \"version\" : \"1.0\" } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule_1_3_0).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.3.0\"," + LS
        + "  \"from\" : \"sample-module-1.2.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // create basic 2.0.0
    final String docBasic_2_0_0 = "{" + LS
      + "  \"id\" : \"basic-module-2.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.1\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant/disable\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"bint\"," + LS
      + "    \"version\" : \"2.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasic_2_0_0).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationBasic_2_0_0 = r.getHeader("Location");

    // upgrade service simulate: will return same as previous one
    // basic 2.0.0 should not be included because bint 2.0 is not used
    // it could be nice to return conflict.. But that is difficult to do.
    // for now 400 ..
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(400);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // create sample 2.0.0
    final String docSampleModule_2_0_0 = "{" + LS
      + "  \"id\" : \"sample-module-2.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.1\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant/disable\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/testb\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"bint\", \"version\" : \"2.0\" } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule_2_0_0).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // upgrade service: now bint 2.0 is in use
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=false&deploy=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-module-2.0.0\"," + LS
        + "  \"from\" : \"basic-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"sample-module-2.0.0\"," + LS
        + "  \"from\" : \"sample-module-1.2.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // try remove sample 1.0.0 which does not exist (removed earlier)
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.2.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // simulate removal of sample
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-2.0.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-2.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // simulate removal of basic
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"basic-module-2.0.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true&purge=false")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-2.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-module-2.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // removal of basic with product only (moduleId without version)
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"basic-module\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true&purge=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-2.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-module-2.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .get("/_/discovery/modules")
      .then()
      .statusCode(200).log().ifValidationFails()
      .body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // deploy sample 1.0.0 again
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleDeployment_1_0_0).post("/_/discovery/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .delete("/_/discovery/modules")
      .then()
      .statusCode(204).log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }

  @Test
  public void test2() {
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RestAssuredClient c;
    Response r;

    final String docMux = "{" + LS
      + "  \"id\" : \"basic-mux-1.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docMux).post("/_/proxy/modules").then().statusCode(201)
      .log().ifValidationFails().extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docMul1 = "{" + LS
      + "  \"id\" : \"basic-mul1-1.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"interfaceType\" : \"multiple\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docMul1).post("/_/proxy/modules").then().statusCode(201)
      .log().ifValidationFails().extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docMul2 = "{" + LS
      + "  \"id\" : \"basic-mul2-1.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"interfaceType\" : \"multiple\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docMul2).post("/_/proxy/modules").then().statusCode(201)
      .log().ifValidationFails().extract().response();
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

    // install with mult first
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {" + LS
        + "  \"id\" : \"basic-mux-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-mul1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-mul2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-mux-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-mul1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-mul2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {" + LS
        + "  \"id\" : \"basic-mul1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-mul2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-mux-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-mul1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-mul2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"basic-mux-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .get("/_/discovery/modules")
      .then()
      .statusCode(200).log().ifValidationFails()
      .body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }

  @Test
  public void test3() {
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RestAssuredClient c;
    Response r;

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

    final String doc1 = "{" + LS
      + "  \"id\" : \"basic-1.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(doc1).post("/_/proxy/modules").then().statusCode(201)
      .log().ifValidationFails().extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // install with mult first
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {" + LS
        + "  \"id\" : \"basic\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String doc2 = "{" + LS
      + "  \"id\" : \"basic-1.0.1\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(doc2).post("/_/proxy/modules").then().statusCode(201)
      .log().ifValidationFails().extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-1.0.1\"," + LS
        + "  \"from\" : \"basic-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {" + LS
        + "  \"id\" : \"basic\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-1.0.1\"," + LS
        + "  \"from\" : \"basic-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .get("/_/discovery/modules")
      .then()
      .statusCode(200).log().ifValidationFails()
      .body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }

  @Test
  public void test4() {
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RestAssuredClient c;
    Response r;

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

    final String doc1 = "{" + LS
      + "  \"id\" : \"sample-module-1.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/testb\"," + LS
      + "      \"type\" : \"request-response\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(doc1).post("/_/proxy/modules").then().statusCode(201)
      .log().ifValidationFails().extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-delay", "600")
      .get("/testb")
      .then()
      .statusCode(200)
      .log().ifValidationFails();

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.0.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .get("/_/discovery/modules")
      .then()
      .statusCode(200).log().ifValidationFails()
      .body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }

  @Test
  public void testDepCheck() {
    RestAssured.port = port;
    RestAssuredClient c;
    Response r;

    // create basic 1.0.0
    final String docBasic_1_0_0 = "{" + LS
      + "  \"id\" : \"basic-module-1.0.0\"," + LS
      + "  \"name\" : \"this module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.1\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant/disable\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"bint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"unknown\", \"version\" : \"1.0\" } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docBasic_1_0_0)
      .post("/_/proxy/modules?check=true")
      .then().statusCode(400).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasic_1_0_0)
      .post("/_/proxy/modules?check=false")
      .then().statusCode(201).log().ifValidationFails().extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String location = r.getHeader("Location");

    c = api.createRestAssured3();
    c.given()
      .delete(location)
      .then().statusCode(204).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }

  @Test
  public void test641() {
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RestAssuredClient c;
    Response r;

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

    final String docLevel1_1_0_0 = "{" + LS
      + "  \"id\" : \"level1-1.0.0\"," + LS
      + "  \"name\" : \"level1 module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i1\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docLevel1_1_0_0)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docLevel1_1_0_1 = "{" + LS
      + "  \"id\" : \"level1-1.0.1\"," + LS
      + "  \"name\" : \"level1 module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i1\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docLevel1_1_0_1)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docLevel2_1_0_0 = "{" + LS
      + "  \"id\" : \"level2-1.0.0\"," + LS
      + "  \"name\" : \"level2 module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i2\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"i1\", \"version\" : \"1.0\" } ]" + LS
      + "}";
    System.out.println(docLevel2_1_0_0);
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docLevel2_1_0_0)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docLevel2_1_0_1 = "{" + LS
      + "  \"id\" : \"level2-1.0.1\"," + LS
      + "  \"name\" : \"level2 module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i2\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"i1\", \"version\" : \"1.0\" } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docLevel2_1_0_1)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level1-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"level1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level1-1.0.1\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"level1-1.0.1\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level1\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"level1-1.0.1\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level1-1.0.1\", \"action\" : \"enable\"},"
        + " {\"id\" : \"level1-1.0.1\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"level1-1.0.1\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level2-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"level1-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"level1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"level2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level1-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"level2-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"level1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"level2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level1\", \"action\" : \"enable\"},"
        + " {\"id\" : \"level2-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"level1-1.0.1\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"level2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level1\", \"action\" : \"enable\"},"
        + " {\"id\" : \"level2\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"level1-1.0.1\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"level2-1.0.1\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level2\", \"action\" : \"enable\"},"
        + " {\"id\" : \"level1\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"level1-1.0.1\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"level2-1.0.1\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level1\", \"action\" : \"enable\"},"
        + " {\"id\" : \"level2\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level1-1.0.1\", \"action\" : \"enable\"},"
        + " {\"id\" : \"level2-1.0.1\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"level2-1.0.1\", \"action\" : \"enable\"},"
        + " {\"id\" : \"level1-1.0.1\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

  }

  @Test
  public void test647() {
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RestAssuredClient c;
    Response r;

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

    final String docProv_1_0_0 = "{" + LS
      + "  \"id\" : \"prov-1.0.0\"," + LS
      + "  \"name\" : \"prov module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i1\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docProv_1_0_0)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docProv2 = "{" + LS
      + "  \"id\" : \"prov-2.0.0\"," + LS
      + "  \"name\" : \"prov module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i1\"," + LS
      + "    \"version\" : \"2.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docProv2)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"prov-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"prov-2.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-2.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"prov-2.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"prov-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docReq1 = "{" + LS
      + "  \"id\" : \"req1-1.0.0\"," + LS
      + "  \"name\" : \"req1 module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i2\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"i1\", \"version\" : \"1.0\" } ]" + LS
      + "}";
    System.out.println(docReq1);
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docReq1)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docReq2 = "{" + LS
      + "  \"id\" : \"req2-1.0.0\"," + LS
      + "  \"name\" : \"req2 module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i3\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"i1\", \"version\" : \"2.0\" } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docReq2)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docReq1or2 = "{" + LS
      + "  \"id\" : \"req1or2-1.0.0\"," + LS
      + "  \"name\" : \"req1or2 module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i4\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"i1\", \"version\" : \"1.0 2.0\" } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docReq1or2)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"req1-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"req2-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-2.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"req1-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"req2-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(400);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"req1-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"req2-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(400);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"req2-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"req1-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(400);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"req1-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"req1or2-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1or2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"req1or2-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"req1-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1or2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"req2-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"req1or2-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-2.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1or2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"req1or2-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"req2-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-2.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1or2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docReqI1or2 = "{" + LS
      + "  \"id\" : \"reqI1or2-1.0.0\"," + LS
      + "  \"name\" : \"reqI1or2 module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i5\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"i4\", \"version\" : \"1.0\"} ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docReqI1or2)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"reqI1or2-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"req1-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1or2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"reqI1or2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"req1-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"reqI1or2-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1or2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"reqI1or2-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docReqI1 = "{" + LS
      + "  \"id\" : \"reqI1-1.0.0\"," + LS
      + "  \"name\" : \"reqI1or2 module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i5\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ { \"id\" : \"i2\", \"version\" : \"1.0\"} ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docReqI1)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"reqI1-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"req1-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"prov-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"req1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"reqI1-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"reqI1-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"req2-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(400);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }

  @Test
  public void test648() {
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RestAssuredClient c;
    Response r;

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

    final String docProv_1_0_0 = "{" + LS
      + "  \"id\" : \"prov-1.0.0\"," + LS
      + "  \"name\" : \"prov module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i1\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docProv_1_0_0)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final int sz = 20;
    for (int i = 0; i < sz; i++) {
      final String docReq1 = "{" + LS
        + "  \"id\" : \"req" + i + "-1.0.0\"," + LS
        + "  \"name\" : \"req" + i + " module\"," + LS
        + "  \"provides\" : [ ]," + LS
        + "  \"requires\" : [ { \"id\" : \"i1\", \"version\" : \"1.0\" } ]" + LS
        + "}";
      System.out.println(docReq1);
      c = api.createRestAssured3();
      c.given()
        .header("Content-Type", "application/json")
        .body(docReq1)
        .post("/_/proxy/modules")
        .then().statusCode(201).log().ifValidationFails();
      Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    }
    StringBuilder b = new StringBuilder();
    b.append("[ ");
    for (int i = 0; i < sz; i++) {
      b.append("{" + LS
        + "  \"id\" : \"req" + i + "-1.0.0\"," + LS + ""
        + "  \"action\" : \"enable\"" + LS
        + "}");
      if (i < sz - 1) {
        b.append(", ");
      } else {
        b.append(" ]");
      }
    }
    System.out.println(b);
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(b.toString())
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    JsonArray a = new JsonArray(r.getBody().asString());
    Assert.assertEquals("prov-1.0.0", a.getJsonObject(0).getString("id"));
    for (int i = 0; i < sz; i++) {
      Assert.assertEquals("req" + i + "-1.0.0", a.getJsonObject(i + 1).getString("id"));
    }
  }

  @Test
  public void test666() {
    final String okapiTenant = "roskilde";
    RestAssured.port = port;
    RestAssuredClient c;
    Response r;

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

    final String docProv_1_0_0 = "{" + LS
      + "  \"id\" : \"prov-1.0.0-alpha\"," + LS
      + "  \"name\" : \"prov module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"i1\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/foo\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docProv_1_0_0)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docReq1 = "{" + LS
      + "  \"id\" : \"req-1.0.0\"," + LS
      + "  \"name\" : \"req module\"," + LS
      + "  \"provides\" : [ ]," + LS
      + "  \"requires\" : [ { \"id\" : \"i1\", \"version\" : \"1.0\" } ]" + LS
      + "}";
    System.out.println(docReq1);
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docReq1)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    StringBuilder b = new StringBuilder();
    b.append("[ ");
    b.append("{" + LS
      + "  \"id\" : \"req-1.0.0\"," + LS + ""
      + "  \"action\" : \"enable\"" + LS
      + "} ]");
    System.out.println(b);

    r = c.given()
      .header("Content-Type", "application/json")
      .body(b.toString())
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true&preRelease=true")
      .then().statusCode(200).log().ifValidationFails()
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    String s = r.getBody().asString();
    JsonArray a = new JsonArray(s);
    Assert.assertEquals("prov-1.0.0-alpha", a.getJsonObject(0).getString("id"));
    Assert.assertEquals("req-1.0.0", a.getJsonObject(1).getString("id"));

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(b.toString())
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true&preRelease=false")
      .then().statusCode(400).log().ifValidationFails().extract().response();
  }
}
