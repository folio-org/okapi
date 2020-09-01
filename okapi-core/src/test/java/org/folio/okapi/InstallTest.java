package org.folio.okapi;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class InstallTest {
  private static final String LS = System.lineSeparator();

  private static RamlDefinition api;

  private final Logger logger = OkapiLogger.get();
  private Vertx vertx;
  private final int port = 9230;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
  }

  Future<Void> startOkapi() {
    DeploymentOptions opt = new DeploymentOptions()
        .setConfig(new JsonObject()
            .put("port", Integer.toString(port)));
    return vertx.deployVerticle(MainVerticle.class.getName(), opt).mapEmpty();
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    RestAssured.port = port;

    Future<Void> future = startOkapi();
    future.onComplete(context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    RestAssuredClient c = api.createRestAssured3();

    c.given().delete("/_/discovery/modules").then().statusCode(204);
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void installGetNotFound(TestContext context) {
    RestAssuredClient c = api.createRestAssured3();
    Response r;

    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/foo/modules/12121")
        .then().statusCode(404);
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }

  @Test
  public void installOK(TestContext context) {
    RestAssuredClient c = api.createRestAssured3();
    Response r;

    final String okapiTenant = "roskilde";

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

    final String docBasic_1_0_0 = "{" + LS
        + "  \"id\" : \"basic-module-1.0.0\"," + LS
        + "  \"name\" : \"this module\"," + LS
        + "  \"provides\" : [ {" + LS
        + "    \"id\" : \"_tenant\"," + LS
        + "    \"version\" : \"1.1\"," + LS
        + "    \"interfaceType\" : \"system\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"POST\" ]," + LS
        + "      \"pathPattern\" : \"/_/tenant/disable\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    }, {" + LS
        + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
        + "      \"pathPattern\" : \"/_/tenant\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
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

    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"basic-module-1.0.0\", \"action\" : \"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&deploy=true")
        .then().statusCode(201)
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"basic-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"))
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    final String locationInstallJob = r.getHeader("Location");

    int pos = locationInstallJob.indexOf("/_/");
    String suffix = locationInstallJob.substring(pos);

    c = api.createRestAssured3();
    c.given()
        .get(suffix)
        .then().statusCode(200)
        .body(equalTo("{" + LS
            + "  \"complete\" : false," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"basic-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"status\" : \"deploy\"" + LS
            + "  } ]" + LS
            +   "}"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    Async async = context.async();
    vertx.setTimer(3000, x -> async.complete());
    async.await();

    RestAssuredClient c1 = api.createRestAssured3();
    c1.given()
        .get(suffix)
        .then().statusCode(200)
        .body(equalTo("{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"basic-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"status\" : \"done\"" + LS
            + "  } ]" + LS
            +   "}"));
    Assert.assertTrue(
        "raml: " + c1.getLastReport().toString(),
        c1.getLastReport().isEmpty());

    // known installId but unknown tenantId
    c = api.createRestAssured3();
    c.given()
        .get(suffix.replace("roskilde", "nosuchtenant"))
        .then().statusCode(404);
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // unknown installId, known tenantId
    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/roskilde/modules/12121")
        .then().statusCode(404);
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }
}
