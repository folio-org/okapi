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

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class ModuleTenantsTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private Vertx vertx;
  private HttpClient httpClient;
  private static final String LS = System.lineSeparator();
  private String locationBasicDeployemnt_0_9_0;
  private String locationBasicDeployment_1_0_0;
  private String locationBasicDeployment_2_0_0;
  private String locationSampleDeployment_1_0_0;
  private String locationSampleDeployment_1_2_0;
  private String locationSampleDeployment_2_0_0;
  private final int port = Integer.parseInt(System.getProperty("port", "9130"));

  @Before
  public void setUp(TestContext context) {
    logger.debug("starting ModuleTenantsTest");
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
    if (locationSampleDeployment_1_0_0 != null) {
      httpClient.delete(port, "localhost", locationSampleDeployment_1_0_0, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSampleDeployment_1_0_0 = null;
          td(context, async);
        });
      }).end();
      return;
    }
    if (locationSampleDeployment_1_2_0 != null) {
      httpClient.delete(port, "localhost", locationSampleDeployment_1_2_0, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSampleDeployment_1_2_0 = null;
          td(context, async);
        });
      }).end();
      return;
    }
    if (locationSampleDeployment_2_0_0 != null) {
      httpClient.delete(port, "localhost", locationSampleDeployment_2_0_0, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSampleDeployment_2_0_0 = null;
          td(context, async);
        });
      }).end();
      return;
    }

    if (locationBasicDeployemnt_0_9_0 != null) {
      httpClient.delete(port, "localhost", locationBasicDeployemnt_0_9_0, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationBasicDeployemnt_0_9_0 = null;
          td(context, async);
        });
      }).end();
      return;
    }
    if (locationBasicDeployment_1_0_0 != null) {
      httpClient.delete(port, "localhost", locationBasicDeployment_1_0_0, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationBasicDeployment_1_0_0 = null;
          td(context, async);
        });
      }).end();
      return;
    }
    if (locationBasicDeployment_2_0_0 != null) {
      httpClient.delete(port, "localhost", locationBasicDeployment_2_0_0, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationBasicDeployment_2_0_0 = null;
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
    final String docBasicDeployment_0_9_0 = "{" + LS
      + "  \"srvcId\" : \"basic-module-0.9.0\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasicDeployment_0_9_0).post("/_/deployment/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationBasicDeployemnt_0_9_0 = r.getHeader("Location");

    // create basic 0.9.0
    final String docBasicModule_0_9_0 = "{" + LS
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
      .body(docBasicModule_0_9_0).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // deploy basic 1.0.0
    final String docBasicDeployment_1_0_0 = "{" + LS
      + "  \"srvcId\" : \"basic-module-1.0.0\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasicDeployment_1_0_0).post("/_/deployment/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationBasicDeployment_1_0_0 = r.getHeader("Location");

    // create basic 1.0.0
    final String docBasic_1_0_0 = "{" + LS
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
      .body(docBasic_1_0_0).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationBasic_1_0_0 = r.getHeader("Location");

    // deploy sample 1.0.0
    final String docSampleDeployment_1_0_0 = "{" + LS
      + "  \"srvcId\" : \"sample-module-1.0.0\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleDeployment_1_0_0).post("/_/deployment/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationSampleDeployment_1_0_0 = r.getHeader("Location");
    // create sample 1.0.0
    final String docSample_1_0_0 = "{" + LS
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
      .body(docSample_1_0_0).post("/_/proxy/modules").then().statusCode(201)
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
    final String docEnableSampleNoSemVer = "{" + LS
      + "  \"id\" : \"sample-module\"" + LS
      + "}";
    final String docEnableSample = "{" + LS
      + "  \"id\" : \"sample-module-1.0.0\"" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docEnableSampleNoSemVer).post("/_/proxy/tenants/" + okapiTenant + "/modules")
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
    final String docSampleModule_1_2_0 = "{" + LS
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
      .body(docSampleModule_1_2_0).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // deploy sample 1.2.0
    final String docSampleDeployment_1_2_0 = "{" + LS
      + "  \"srvcId\" : \"sample-module-1.2.0\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleDeployment_1_2_0).post("/_/deployment/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationSampleDeployment_1_2_0 = r.getHeader("Location");

    // Upgrade with bad JSON.
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("{").post(locationTenantModule)
      .then().statusCode(400);

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

    // upgrade service: nothing is installed yet
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true&preRelease=true")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // enable modules -- wrong type
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("{ \"foo\" : \"bar\"}").post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(400);

    // enable modules -- bad JSON
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("{").post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(400);

    // enable modules: post unknown module with semver
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-foo-1.2.3\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // enable modules: post unknown module without semver
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-foo\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // enable modules: post known module
    c = api.createRestAssured();
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
    c = api.createRestAssured();
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
    c = api.createRestAssured();
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
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=false")
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
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=false")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // upgrade service: autoDeloy is unsupported
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?autoDeploy=true")
      .then().statusCode(500);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // deploy basic 2.0.0
    final String docBasicDeployment_2_0_0 = "{" + LS
      + "  \"srvcId\" : \"basic-module-2.0.0\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docBasicDeployment_2_0_0).post("/_/deployment/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationBasicDeployment_2_0_0 = r.getHeader("Location");

    // create basic 2.0.0
    final String docBasic_2_0_0 = "{" + LS
      + "  \"id\" : \"basic-module-2.0.0\"," + LS
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
      + "    \"version\" : \"2.0\"," + LS
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
    c = api.createRestAssured();
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
      + "  \"requires\" : [ { \"id\" : \"bint\", \"version\" : \"2.0\" } ]" + LS
      + "}";

    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule_2_0_0).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // deploy sample 2.0.0
    final String docSampleDeployment_2_0_0 = "{" + LS
      + "  \"srvcId\" : \"sample-module-2.0.0\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleDeployment_2_0_0).post("/_/deployment/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationSampleDeployment_2_0_0 = r.getHeader("Location");

    // upgrade service: now bint 2.0 is in use
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=false")
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
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.2.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(404);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // simulate removal of sample
    c = api.createRestAssured();
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
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"basic-module-2.0.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
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
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"basic-module\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install")
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
  }
}
