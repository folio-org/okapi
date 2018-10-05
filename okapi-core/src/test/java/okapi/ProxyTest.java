package okapi;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.core.logging.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import org.folio.okapi.MainVerticle;
import static org.hamcrest.Matchers.equalTo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@RunWith(VertxUnitRunner.class)
public class ProxyTest {

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
    vertx = Vertx.vertx();
    httpClient = vertx.createHttpClient();

    RestAssured.port = port;
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", Integer.toString(port)));
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  private void td(TestContext context, Async async) {
    vertx.close(x -> {
      async.complete();
    });
  }

  @After
  public void tearDown(TestContext context) {
    Async async = context.async();

    httpClient.delete(port, "localhost", "/_/discovery/modules", response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        td(context, async);
      });
    }).end();
  }

  @Test
  public void test1(TestContext context) {
    RestAssuredClient c;
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
      + "      \"pathPattern\" : \"/_/tenant/disable\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myxfirst\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\"]," + LS
      + "      \"pathPattern\" : \"/testb/client_id\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"mysecond\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\"]," + LS
      + "      \"pathPattern\" : \"/testb/{id}\"" + LS
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
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"basic-module-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    r = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-all-headers", "B")
      .get("/testb/hugo")
      .then().statusCode(200).log().ifValidationFails()
      .extract().response();
    Assert.assertTrue(r.body().asString().contains("X-Okapi-Match-Path-Pattern:/testb/{id}"));

    c = api.createRestAssured3();
    r = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-all-headers", "B")
      .get("/testb/client_id")
      .then().statusCode(200).log().ifValidationFails()
      .extract().response();
    Assert.assertTrue(r.body().asString().contains("X-Okapi-Match-Path-Pattern:/testb/client_id"));

    c = api.createRestAssured3();
    r = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-all-headers", "B")
      .get("/testb/client_id%2Fx")
      .then().statusCode(200).log().ifValidationFails()
      .extract().response();
    Assert.assertTrue(r.body().asString().contains("X-Okapi-Match-Path-Pattern:/testb/{id}"));

    c = api.createRestAssured3();
    c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/testb/client_id/x")
      .then().statusCode(404).log().ifValidationFails();

  }

  @Test
  public void testAdditionalToken(TestContext context) {
    RestAssuredClient c;
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

    final String testAuthJar = "../okapi-test-auth-module/target/okapi-test-auth-module-fat.jar";
    final String docAuthModule = "{" + LS
      + "  \"id\" : \"auth-module-1.0.0\"," + LS
      + "  \"name\" : \"auth\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"auth\"," + LS
      + "    \"version\" : \"1.2\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"path\" : \"/authn/login\"," + LS
      + "      \"level\" : \"20\"," + LS
      + "      \"type\" : \"request-response\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"*\" ]," + LS
      + "    \"path\" : \"/\"," + LS
      + "    \"phase\" : \"auth\"," + LS
      + "    \"type\" : \"headers\"" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testAuthJar + "\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docAuthModule).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\"]," + LS
      + "      \"pathPattern\" : \"/testb/{id}\"" + LS
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

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"basic-module-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"auth-module-1.0.0\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"basic-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"auth-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docLogin = "{" + LS
      + "  \"tenant\" : \"" + okapiTenant + "\"," + LS
      + "  \"username\" : \"peter\"," + LS
      + "  \"password\" : \"peter-password\"" + LS
      + "}";
    final String okapiToken = c.given().header("Content-Type", "application/json").body(docLogin)
      .header("X-Okapi-Tenant", okapiTenant).post("/authn/login")
      .then().statusCode(200).extract().header("X-Okapi-Token");

    c = api.createRestAssured3();
    c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-all-headers", "B")
      .get("/testb/hugo")
      .then().statusCode(401).log().ifValidationFails();

    c = api.createRestAssured3();
    r = c.given()
      .header("X-Okapi-Token", okapiToken)
      .get("/testb/hugo")
      .then().statusCode(200).log().ifValidationFails()
      .extract().response();
    Assert.assertEquals("It works", r.getBody().asString());

    c = api.createRestAssured3();
    r = c.given()
      .header("X-all-headers", "B")
      .header("X-Okapi-Token", okapiToken)
      .header("X-Okapi-Additional-Token", "dummyJwt")
      .get("/testb/hugo")
      .then().statusCode(200).log().ifValidationFails()
      .extract().response();
    String b = r.getBody().asString();
    Assert.assertTrue(b.contains("It works"));
    // test module must NOT receive the X-Okapi-Additional-Token
    Assert.assertTrue(!b.contains("X-Okapi-Additional-Token"));

    c = api.createRestAssured3();
    c.given()
      .header("X-all-headers", "B")
      .header("X-Okapi-Token", okapiToken)
      .header("X-Okapi-Additional-Token", "nomatch")
      .get("/testb/hugo")
      .then().statusCode(400).log().ifValidationFails();
  }
}
