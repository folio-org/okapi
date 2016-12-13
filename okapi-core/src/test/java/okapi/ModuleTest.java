package okapi;

import org.folio.okapi.MainVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.jayway.restassured.RestAssured;
import static com.jayway.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.response.ValidatableResponse;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import java.util.Iterator;

@RunWith(VertxUnitRunner.class)
public class ModuleTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  Vertx vertx;
  Async async;

  private String locationSampleDeployment;
  private String locationSample5Deployment;
  private String locationHeaderDeployment;
  private String locationAuthDeployment = null;
  private String okapiToken;
  private final String okapiTenant = "roskilde";
  private HttpClient httpClient;
  private static final String LS = System.lineSeparator();
  private final int port = Integer.parseInt(System.getProperty("port", "9130"));

  public ModuleTest() {
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    JsonObject conf = new JsonObject()
            .put("storage", "inmemory");

    DeploymentOptions opt = new DeploymentOptions()
            .setConfig(conf);
    vertx.deployVerticle(MainVerticle.class.getName(),
            opt, context.asyncAssertSuccess());
    httpClient = vertx.createHttpClient();
    RestAssured.port = port;
  }

  @After
  public void tearDown(TestContext context) {
    logger.info("Cleaning up after ModuleTest");
    async = context.async();
    td(context);
  }

  public void td(TestContext context) {
    if (locationAuthDeployment != null) {
      httpClient.delete(port, "localhost", locationAuthDeployment, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationAuthDeployment = null;
          td(context);
        });
      }).end();
      return;
    }
    if (locationSampleDeployment != null) {
      httpClient.delete(port, "localhost", locationSampleDeployment, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSampleDeployment = null;
          td(context);
        });
      }).end();
      return;
    }
    if (locationSample5Deployment != null) {
      httpClient.delete(port, "localhost", locationSample5Deployment, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample5Deployment = null;
          td(context);
        });
      }).end();
      return;
    }
    if (locationHeaderDeployment != null) {
      httpClient.delete(port, "localhost", locationHeaderDeployment, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationHeaderDeployment = null;
          td(context);
        });
      }).end();
      return;
    }

    vertx.close(x -> {
      async.complete();
    });
  }

  /**
   * Check that the tests have not left anything in the database. Since the
   *
   * @Tests are run in a nondeterministic order, each ought to clean up after
   * itself. This should be called in the beginning and end of each @Test
   *
   * @param context
   */
  private void checkDbIsEmpty(String label, TestContext context) {

    logger.debug("Db check " + label);
    // Check that we are not depending on td() to undeploy modules
    Assert.assertNull("locationAuthDeployment", locationAuthDeployment);
    Assert.assertNull("locationSampleDeployment", locationSampleDeployment);
    Assert.assertNull("locationSample5Deployment", locationSample5Deployment);
    Assert.assertNull("locationHeaderDeployment", locationHeaderDeployment);

    String emptyListDoc = "[ ]";

    given().get("/_/deployment/modules").then()
            .log().ifError().statusCode(200)
            .body(equalTo(emptyListDoc));

    given().get("/_/discovery/nodes").then()
            .log().ifError().statusCode(200); // we still have a node!
    given().get("/_/discovery/modules").then()
            .log().ifError().statusCode(200).body(equalTo(emptyListDoc));

    given().get("/_/proxy/modules").then()
            .log().ifError().statusCode(200).body(equalTo(emptyListDoc));
    given().get("/_/proxy/tenants").then()
            .log().ifError().statusCode(200).body(equalTo(emptyListDoc));
    logger.debug("Db check " + label + " done");

  }

  @Test
  public void testProxy(TestContext context) {
    async = context.async();

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;
    Response r;

    String nodeListDoc = "[ {" + LS
            + "  \"nodeId\" : \"localhost\"," + LS
            + "  \"url\" : \"http://localhost:9130\"" + LS
            + "} ]";

    c = api.createRestAssured();
    c.given().get("/_/discovery/nodes").then().statusCode(200)
            .body(equalTo(nodeListDoc));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/discovery/nodes/gyf").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/discovery/nodes/localhost").then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body("{ }").post("/_/xyz").then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/xyz' is not defined], "
            + "responseViolations=[], validationViolations=[]}",
            c.getLastReport().toString());

    final String badDoc = "{" + LS
            + "  \"instId\" : \"BAD\"," + LS // the comma here makes it bad json!
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(badDoc).post("/_/deployment/modules")
            .then().statusCode(400);

    final String docUnknownJar = "{" + LS
            + "  \"srvcId\" : \"auth\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-auth-module/target/okapi-unknown.jar\"" + LS
            + "  }" + LS
            + "}";

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docUnknownJar).post("/_/deployment/modules")
            .then()
            .statusCode(500);

    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String docAuthDeployment = "{" + LS
            + "  \"srvcId\" : \"auth\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-auth-module/target/okapi-test-auth-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docAuthDeployment).post("/_/deployment/modules")
            .then()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    locationAuthDeployment = r.getHeader("Location");

    c = api.createRestAssured();
    String docAuthDiscovery = c.given().get(locationAuthDeployment)
            .then().statusCode(200).extract().body().asString();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String docAuthModule = "{" + LS
            + "  \"id\" : \"auth\"," + LS
            + "  \"name\" : \"auth\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"auth\"," + LS
            + "    \"version\" : \"1.2.3\"" + LS
            + "  } ]," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"*\" ]," + LS
            + "    \"path\" : \"/s\"," + LS
            + "    \"level\" : \"10\"," + LS
            + "    \"type\" : \"request-response\"," + LS
            + "    \"permissionsDesired\" : [ \"auth.extra\" ]" + LS
            + "  }, {"
            + "    \"methods\" : [ \"POST\" ]," + LS
            + "    \"path\" : \"/login\"," + LS
            + "    \"level\" : \"20\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]" + LS
            + "}";

    // Check that we fail on unknown route types
    final String docBadTypeModule
            = docAuthModule.replaceAll("request-response", "UNKNOWN-ROUTE-TYPE");
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docBadTypeModule).post("/_/proxy/modules")
            .then().statusCode(400);

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docAuthModule).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationAuthModule = r.getHeader("Location");

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docAuthModule).put(locationAuthModule).then().statusCode(200)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String docAuthModule2 =  "{" + LS
            + "  \"id\" : \"auth2\"," + LS
            + "  \"name\" : \"auth2\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"auth2\"," + LS
            + "    \"version\" : \"1.2.3\"" + LS
            + "  } ]," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"*\" ]," + LS
            + "    \"path\" : \"/s\"," + LS
            + "    \"level\" : \"10\"," + LS
            + "    \"type\" : \"request-response\"," + LS
            + "    \"permissionsDesired\" : [ \"auth.extra\" ]" + LS
            + "  }, {"
            + "    \"methods\" : [ \"POST\" ]," + LS
            + "    \"path\" : \"/login\"," + LS
            + "    \"level\" : \"20\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]" + LS
            + "}";

    final String locationAuthModule2 = locationAuthModule + "2";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docAuthModule2).put(locationAuthModule2).then().statusCode(200)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(locationAuthModule2).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String docSampleDeployment = "{" + LS
            + "  \"srvcId\" : \"sample-module\"," + LS
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
    locationSampleDeployment = r.getHeader("Location");

    c = api.createRestAssured();
    String docSampleDiscovery = c.given().get(locationSampleDeployment)
            .then().statusCode(200).extract().body().asString();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String docSampleModuleBadRequire = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"requires\" : [ {" + LS
            + "    \"id\" : \"SOMETHINGWEDONOTHAVE\"," + LS
            + "    \"version\" : \"1.2.3\"" + LS
            + "  } ]," + LS
            + "  \"routingEntries\" : [ ] " + LS
            + "}";

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docSampleModuleBadRequire).post("/_/proxy/modules").then().statusCode(400)
            .extract().response();

    final String docSampleModuleBadVersion = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"sample\"," + LS
            + "    \"version\" : \"1.0.0\"" + LS
            + "  } ]," + LS
            + "  \"requires\" : [ {" + LS
            + "    \"id\" : \"auth\"," + LS
            + "    \"version\" : \"9.9.3\"" + LS // We only have 1.2.3
            + "  } ]," + LS
            + "  \"routingEntries\" : [ ] " + LS
            + "}";

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docSampleModuleBadVersion).post("/_/proxy/modules").then().statusCode(400)
            .extract().response();

    final String docSampleModule = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"sample\"," + LS
            + "    \"version\" : \"1.0.0\"" + LS
            + "  } ]," + LS
            + "  \"requires\" : [ {" + LS
            + "    \"id\" : \"auth\"," + LS
            + "    \"version\" : \"1.2.3\"" + LS
            + "  } ]," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/testb\"," + LS
            + "    \"level\" : \"30\"," + LS
            + "    \"type\" : \"request-response\"," + LS
            + "    \"permissionsRequired\" : [ \"sample.needed\" ]," + LS
            + "    \"permissionsDesired\" : [ \"sample.extra\" ]" + LS
            + "  } ]," + LS
            + "  \"modulePermissions\" : [ \"sample.modperm\" ]," + LS
            + "  \"launchDescriptor\" : {" + LS
            + "    \"exec\" : \"/usr/bin/false\"" + LS
            + "  }" + LS
            + "}";
    logger.debug(docSampleModule);
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleModule).post("/_/proxy/modules")
            .then()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSampleModule = r.getHeader("Location");

    c = api.createRestAssured(); // trailing slash is no good
    c.given().get("/_/proxy/modules/").then().statusCode(404);

    c = api.createRestAssured();
    c.given().get("/_/proxy/modules").then().statusCode(200);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given()
            .get(locationSampleModule)
            .then().statusCode(200).body(equalTo(docSampleModule));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    // Try to delete the auth module that our sample depends on
    c.given().delete(locationAuthModule).then().statusCode(400);

    // Try to update the auth module to a lower version, would break
    // sample dependency
    final String docAuthLowerVersion = docAuthModule.replace("1.2.3", "1.1.1");
    c.given()
            .header("Content-Type", "application/json")
            .body(docAuthLowerVersion)
            .put(locationAuthModule)
            .then().statusCode(400);

    // Update the auth module to a bit higher version
    final String docAuthhigherVersion = docAuthModule.replace("1.2.3", "1.2.4");
    c.given()
            .header("Content-Type", "application/json")
            .body(docAuthhigherVersion)
            .put(locationAuthModule)
            .then().statusCode(200);

    final String docTenantRoskilde = "{" + LS
            + "  \"id\" : \"" + okapiTenant + "\"," + LS
            + "  \"name\" : \"" + okapiTenant + "\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docTenantRoskilde).post("/_/proxy/tenants/")
            .then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/proxy/tenants/' is not defined], "
            + "responseViolations=[], validationViolations=[]}",
            c.getLastReport().toString());

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docTenantRoskilde).post("/_/proxy/tenants")
            .then().statusCode(201)
            .body(equalTo(docTenantRoskilde))
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationTenantRoskilde = r.getHeader("Location");

    // Try to enable sample without the auth that it requires
    final String docEnableWithoutDep = "{" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}";
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableWithoutDep).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(400);

    final String docEnableAuth = "{" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableAuth).post("/_/proxy/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404);  // trailing slash is no good

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableAuth).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableAuth));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404);

    c = api.createRestAssured();
    final String exp1 = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "} ]";
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(exp1));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String expAuthEnabled = "{" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/auth")
            .then().statusCode(200).body(equalTo(expAuthEnabled));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String docEnableSample = "{" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableSample));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404); // trailing slash

    c = api.createRestAssured();
    final String expEnabledBoth = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "} ]";
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(expEnabledBoth));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    // Try to disable the auth module for the tenant.
    // Ought to fail, because it is needed by sample module
    c.given().delete("/_/proxy/tenants/" + okapiTenant + "/modules/auth")
            .then().statusCode(400);

    String docTenant = "{" + LS
            + "  \"id\" : \"" + okapiTenant + "\"," + LS
            + "  \"name\" : \"Roskilde-library\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docTenant).put("/_/proxy/tenants/" + okapiTenant)
            .then().statusCode(200)
            .body(equalTo(docTenant));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(expEnabledBoth));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    given().get("/sample")
            .then().statusCode(403);

    given().header("X-Okapi-Tenant", okapiTenant).get("/q")
            .then().statusCode(404);

    given().header("X-Okapi-Tenant", okapiTenant).get("/sample")
            .then().statusCode(401);

    final String docWrongLogin = "{" + LS
            + "  \"tenant\" : \"t1\"," + LS
            + "  \"username\" : \"peter\"," + LS
            + "  \"password\" : \"peter-wrong-password\"" + LS
            + "}";

    given().header("Content-Type", "application/json").body(docWrongLogin)
            .header("X-Okapi-Tenant", okapiTenant).post("/login")
            .then().statusCode(401);

    final String docLogin = "{" + LS
            + "  \"tenant\" : \"" + okapiTenant + "\"," + LS
            + "  \"username\" : \"peter\"," + LS
            + "  \"password\" : \"peter-password\"" + LS
            + "}";
    okapiToken = given().header("Content-Type", "application/json").body(docLogin)
            .header("X-Okapi-Tenant", okapiTenant).post("/login")
            .then().statusCode(200).extract().header("X-Okapi-Token");

    // Check that okapi sets up the permission headers
    // Check also the X-Okapi-Url header in the same go, as well as
    // URL parameters
    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("X-all-headers", "HB") // ask sample to report all headers
            .get("/testb?query=foo&limit=10")
            .then().statusCode(200)
            .header("X-Okapi-Permissions-Required", "sample.needed")
            .header("X-Okapi-Module-Permissions", "{\"sample-module\":[\"sample.modperm\"]}")
            .header("X-Okapi-Url", "http://localhost:9130")  // no trailing slash!
            .header("X-Url-Params", "query=foo&limit=10")
            .body(containsString("It works"));
    // Check only the required permission bit, since there is only one.
    // There are wanted bits too, two of them, but their order is not
    // well defined...

    // Check the CORS headers
    // The presence of the Origin header should provoke the two extra headers
    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("Origin", "http://foobar.com")
            .get("/testb")
            .then().statusCode(200)
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Expose-Headers", "Location,X-Okapi-Trace,X-Okapi-Token")
            .body(equalTo("It works"));

    // Post request
    // Test also URL parameters
    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("Content-Type", "text/xml")
            .header("X-all-headers", "H") // ask sample to report all headers
            .body("Okapi").post("/testb?query=foo")
            .then().statusCode(200)
            .header("X-Url-Params", "query=foo")
            .body(equalTo("Hello  (XML) Okapi"));

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .get("/samplE")
            .then().statusCode(202);

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .delete("/sample")
            .then().statusCode(202);

    // Check that we don't do prefix matching
    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .get("/testbXXX")
            .then().statusCode(404);
    // Check that parameters don't mess with the routing
    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .get("/testb?p=parameters&q=query")
            .then().statusCode(200);

    // Check that we accept Authorization: Bearer <token> instead of X-Okapi-Token,
    // and that we can extract the tenant from it
    given().header("X-all-headers", "H") // ask sample to report all headers
            .header("Authorization", "Bearer " + okapiToken)
            .get("/testb")
            .then().log().ifError()
            .header("X-Okapi-Token", okapiToken)
            .header("X-Okapi-Tenant", okapiTenant)
            .statusCode(200);

    // Check that we fail on conflicting X-Okapi-Token and Auth tokens
    given().header("X-all-headers", "H") // ask sample to report all headers
            .header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("Authorization", "Bearer " + okapiToken + "WRONG")
            .get("/testb")
            .then().log().ifError()
            .statusCode(400);

    // 2nd sample module.. We only create it in discovery and give it same URL as
    // for sample-module (first one)
    c = api.createRestAssured();
    final String docSample2Deployment = "{" + LS
            + "  \"instId\" : \"sample2-inst\"," + LS
            + "  \"srvcId\" : \"sample-module2\"," + LS
            // + "  \"nodeId\" : null," + LS // no nodeId, we aren't deploying on any node
            + "  \"url\" : \"http://localhost:9132\"" + LS
            + "}";
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSample2Deployment).post("/_/discovery/modules")
            .then()
            .statusCode(201).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSample2Discovery = r.header("Location");

    c = api.createRestAssured();
    c.given().get("/_/discovery/modules/sample-module2")
            .then().statusCode(200)
            .log().ifError();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/discovery/modules/sample-module2/sample2-inst")
            .then().statusCode(200)
            .log().ifError();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/discovery/health")
            .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/discovery/health/sample-module2")
            .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/discovery/health/sample-module2/sample2-inst")
            .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String docSample2Module = "{" + LS
            + "  \"id\" : \"sample-module2\"," + LS
            + "  \"name\" : \"another-sample-module2\"," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/testb\"," + LS
            + "    \"level\" : \"31\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]" + LS
            + "}";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSample2Module).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSample2Module = r.getHeader("Location");

    final String docEnableSample2 = "{" + LS
            + "  \"id\" : \"sample-module2\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableSample2).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableSample2));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    // 3rd sample module.. We only create it in discovery and give it same URL as
    // for sample-module (first one)
    c = api.createRestAssured();
    final String docSample3Deployment = "{" + LS
            + "  \"instId\" : \"sample3-instance\"," + LS
            + "  \"srvcId\" : \"sample-module3\"," + LS
            + "  \"url\" : \"http://localhost:9132\"" + LS
            + "}";
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSample3Deployment).post("/_/discovery/modules")
            .then()
            .statusCode(201).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSample3Inst = r.getHeader("Location");
    logger.debug("Deployed: locationSample3Inst " + locationSample3Inst);

    final String docSample3Module = "{" + LS
            + "  \"id\" : \"sample-module3\"," + LS
            + "  \"name\" : \"sample-module3\"," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/testb\"," + LS
            + "    \"level\" : \"05\"," + LS
            + "    \"type\" : \"headers\"" + LS
            + "  }, {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/testb\"," + LS
            + "    \"level\" : \"45\"," + LS
            + "    \"type\" : \"headers\"" + LS
            + "  }, {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/testb\"," + LS
            + "    \"level\" : \"33\"," + LS
            + "    \"type\" : \"request-only\"" + LS
            + "  } ]" + LS
            + "}";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSample3Module).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSample3Module = r.getHeader("Location");

    final String docEnableSample3 = "{" + LS
            + "  \"id\" : \"sample-module3\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableSample3).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableSample3));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .get("/testb")
            .then().statusCode(200).body(equalTo("It works"));

    // Verify that both modules get executed
    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .body("OkapiX").post("/testb")
            .then().statusCode(200)
            .body(equalTo("Hello Hello OkapiX"));

    // Check that the X-Okapi-Stop trick works. Sample will set it if it sees
    // a X-Stop-Here header
    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("X-Stop-Here", "Enough!")
            .body("OkapiX").post("/testb")
            .then().statusCode(200)
            .header("X-Okapi-Stop", "Enough!")
            .body(equalTo("Hello OkapiX")); // only one "Hello"

    given().get("/_/test/reloadmodules")
            .then().statusCode(204);

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("Content-Type", "text/xml")
            .get("/testb")
            .then().statusCode(200).body(equalTo("It works (XML) "));

    c = api.createRestAssured();
    final String exp4Modules = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module2\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module3\"" + LS
            + "} ]";
    c.given().get(locationTenantRoskilde + "/modules")
            .then().statusCode(200)
            .body(equalTo(exp4Modules));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(locationTenantRoskilde + "/modules/sample-module3")
            .then().statusCode(204);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    final String exp3Modules = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module2\"" + LS
            + "} ]";
    c.given().get(locationTenantRoskilde + "/modules")
            .then().statusCode(200)
            .body(equalTo(exp3Modules));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/discovery/modules")
            .then().statusCode(200)
            .log().ifError();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    // make sample 2 disappear from discovery!
    c = api.createRestAssured();
    c.given().delete(locationSample2Discovery)
            .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/discovery/modules")
            .then().statusCode(200)
            .log().ifError();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("Content-Type", "text/xml")
            .get("/testb")
            .then().statusCode(404); // because sample2 was removed

    c = api.createRestAssured();
    c.given().delete(locationTenantRoskilde)
            .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    // Clean up, so the next test starts with a clean slate
    logger.debug("testproxy cleaning up");
    given().delete(locationSample3Inst).then().log().ifError().statusCode(204);
    given().delete(locationSample3Module).then().log().ifError().statusCode(204);
    given().delete("/_/proxy/modules/sample-module").then().log().ifError().statusCode(204);
    given().delete("/_/proxy/modules/sample-module2").then().log().ifError().statusCode(204);
    given().delete("/_/proxy/modules/auth").then().log().ifError().statusCode(204);
    given().delete(locationAuthDeployment).then().log().ifError().statusCode(204);
    locationAuthDeployment = null;
    given().delete(locationSampleDeployment).then().log().ifError().statusCode(204);
    locationSampleDeployment = null;

    checkDbIsEmpty("testproxy done", context);

    async.complete();
  }

  @Test
  public void testDeployment(TestContext context) {
    async = context.async();
    Response r;

    given().get("/_/deployment/modules")
            .then().statusCode(200)
            .body(equalTo("[ ]"));

    given().get("/_/deployment/modules/not_found")
            .then().statusCode(404);

    given().get("/_/discovery/modules")
            .then().statusCode(200)
            .body(equalTo("[ ]"));

    given().get("/_/discovery/modules/not_found")
            .then().statusCode(404);

    final String doc1 = "{" + LS
            + "  \"srvcId\" : \"sample-module5\"," + LS
            + "  \"nodeId\" : \"localhost\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";

    given().header("Content-Type", "application/json")
            .body(doc1).post("/_/discovery/modules/") // extra slash !
            .then().statusCode(404);

    final String doc2 = "{" + LS
            + "  \"instId\" : \"localhost-9131\"," + LS
            + "  \"srvcId\" : \"sample-module5\"," + LS
            + "  \"nodeId\" : \"localhost\"," + LS
            + "  \"url\" : \"http://localhost:9131\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";

    r = given().header("Content-Type", "application/json")
            .body(doc1).post("/_/discovery/modules")
            .then().statusCode(201)
            .body(equalTo(doc2))
            .extract().response();
    locationSample5Deployment = r.getHeader("Location");

    given().get(locationSample5Deployment)
            .then().statusCode(200)
            .body(equalTo(doc2));

    given().get("/_/deployment/modules")
            .then().statusCode(200)
            .body(equalTo("[ " + doc2 + " ]"));

    given().header("Content-Type", "application/json")
            .body(doc2).post("/_/discovery/modules")
            .then().statusCode(400);

    given().get("/_/discovery/modules/sample-module5")
            .then().statusCode(200)
            .body(equalTo("[ " + doc2 + " ]"));

    given().get("/_/discovery/modules")
            .then().statusCode(200)
            .log().ifError()
            .body(equalTo("[ " + doc2 + " ]"));

    System.out.println("delete: " + locationSample5Deployment);
    given().delete(locationSample5Deployment).then().statusCode(204);
    locationSample5Deployment = null;

    // Verify that the list works also after delete
    given().get("/_/deployment/modules")
            .then().statusCode(200)
            .body(equalTo("[ ]"));

    // verify that module5 is no longer there
    given().get("/_/discovery/modules/sample-module5")
            .then().statusCode(404);

    // verify that a never-seen module returns the same
    given().get("/_/discovery/modules/UNKNOWN-MODULE")
            .then().statusCode(404);

    // Deploy a module via its own LaunchDescriptor
    final String docSampleModule = "{" + LS
            + "  \"id\" : \"sample-module-depl\"," + LS
            + "  \"name\" : \"sample module for deployment test\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"sample\"," + LS
            + "    \"version\" : \"1.0.0\"" + LS
            + "  } ]," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/testb\"," + LS
            + "    \"level\" : \"30\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]," + LS
            + "  \"launchDescriptor\" : {" + LS
            + "    \"exec\" : \"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleModule).post("/_/proxy/modules")
            .then()
            //.log().all()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSampleModule = r.getHeader("Location");

    final String docDeploy = "{" + LS
            + "  \"srvcId\" : \"sample-module-depl\"," + LS
            + "  \"nodeId\" : \"localhost\"" + LS
            + "}";
    final String DeployResp = "{" + LS
            + "  \"instId\" : \"localhost-9131\"," + LS
            + "  \"srvcId\" : \"sample-module-depl\"," + LS
            + "  \"nodeId\" : \"localhost\"," + LS
            + "  \"url\" : \"http://localhost:9131\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : \"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";

    r = given().header("Content-Type", "application/json")
            .body(docDeploy).post("/_/discovery/modules")
            .then().statusCode(201)
            .body(equalTo(DeployResp))
            .extract().response();
    locationSample5Deployment = r.getHeader("Location");

    // Would be nice to verify that the module works, but too much hassle with
    // tenants etc
    // Undeploy
    given().delete(locationSample5Deployment)
            .then().statusCode(204);
    // Undeploy again, to see it is gone
    given().delete(locationSample5Deployment)
            .then().statusCode(404);
    locationSample5Deployment = null;

    // and delete from the proxy
    given().delete(locationSampleModule)
            .then().statusCode(204);

    checkDbIsEmpty("testDeployment done", context);
    async.complete();
  }

  @Test
  public void testHeader(TestContext context) {
    async = context.async();

    Response r;
    ValidatableResponse then;

    final String docLaunch1 = "{" + LS
            + "  \"srvcId\" : \"sample-module5\"," + LS
            + "  \"nodeId\" : \"localhost\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";

    r = given().header("Content-Type", "application/json")
            .body(docLaunch1).post("/_/discovery/modules")
            .then().statusCode(201)
            .extract().response();
    locationSample5Deployment = r.getHeader("Location");

    final String docLaunch2 = "{" + LS
            + "  \"srvcId\" : \"header-module\"," + LS
            + "  \"nodeId\" : \"localhost\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-header-module/target/okapi-test-header-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";

    r = given().header("Content-Type", "application/json")
            .body(docLaunch2).post("/_/discovery/modules")
            .then().statusCode(201)
            .extract().response();
    locationHeaderDeployment = r.getHeader("Location");

    final String docSampleModule = "{" + LS
            + "  \"id\" : \"sample-module5\"," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/testb\"," + LS
            + "    \"level\" : \"20\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]" + LS
            + "}";
    r = given()
            .header("Content-Type", "application/json")
            .body(docSampleModule).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    final String locationSampleModule = r.getHeader("Location");

    final String docHeaderModule = "{" + LS
            + "  \"id\" : \"header-module\"," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/testb\"," + LS
            + "    \"level\" : \"10\"," + LS
            + "    \"type\" : \"headers\"" + LS
            + "  } ]" + LS
            + "}";
    r = given()
            .header("Content-Type", "application/json")
            .body(docHeaderModule).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    final String locationHeaderModule = r.getHeader("Location");

    final String docTenantRoskilde = "{" + LS
            + "  \"id\" : \"" + okapiTenant + "\"," + LS
            + "  \"name\" : \"" + okapiTenant + "\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
            + "}";
    r = given()
            .header("Content-Type", "application/json")
            .body(docTenantRoskilde).post("/_/proxy/tenants")
            .then().statusCode(201)
            .body(equalTo(docTenantRoskilde))
            .extract().response();
    final String locationTenantRoskilde = r.getHeader("Location");

    final String docEnableSample = "{" + LS
            + "  \"id\" : \"sample-module5\"" + LS
            + "}";
    given()
            .header("Content-Type", "application/json")
            .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableSample));

    final String docEnableHeader = "{" + LS
            + "  \"id\" : \"header-module\"" + LS
            + "}";
    given()
            .header("Content-Type", "application/json")
            .body(docEnableHeader).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableHeader));

    given().header("X-Okapi-Tenant", okapiTenant)
            .body("bar").post("/testb")
            .then().statusCode(200).body(equalTo("Hello foobar"))
            .extract().response();

    given().delete("/_/proxy/tenants/" + okapiTenant + "/modules/sample-module5")
            .then().statusCode(204);

    given().delete(locationSampleModule)
            .then().statusCode(204);

    final String docSampleModule2 = "{" + LS
            + "  \"id\" : \"sample-module5\"," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/testb\"," + LS
            + "    \"level\" : \"5\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]" + LS
            + "}";

    given()
            .header("Content-Type", "application/json")
            .body(docSampleModule2).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    final String locationSampleModule2 = r.getHeader("Location");

    given()
            .header("Content-Type", "application/json")
            .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableSample));

    given().header("X-Okapi-Tenant", okapiTenant)
            .body("bar").post("/testb")
            .then().statusCode(200).body(equalTo("Hello foobar"))
            .extract().response();

    logger.debug("testHeader cleaning up");
    given().delete(locationTenantRoskilde)
            .then().statusCode(204);
    given().delete(locationSampleModule)
            .then().statusCode(204);
    given().delete(locationSample5Deployment)
            .then().statusCode(204);
    locationSample5Deployment = null;
    given().delete(locationHeaderDeployment)
            .then().statusCode(204);
    locationHeaderDeployment = null;
    given().delete(locationHeaderModule)
            .then().statusCode(204);

    checkDbIsEmpty("testHeader done", context);

    async.complete();
  }

  @Test
  public void testUiModule(TestContext context) {
    async = context.async();
    Response r;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    final String docUiModuleInput = "{" + LS
            + "  \"id\" : \"11-22-11-22-11\"," + LS
            + "  \"name\" : \"sample-ui\"," + LS
            + "  \"routingEntries\" : [ ]," + LS
            + "  \"uiDescriptor\" : {" + LS
            + "    \"npm\" : \"name-of-module-in-npm\"" + LS
            + "  }" + LS
            + "}";

    final String docUiModuleOutput = "{" + LS
            + "  \"id\" : \"11-22-11-22-11\"," + LS
            + "  \"name\" : \"sample-ui\"," + LS
            + "  \"routingEntries\" : [ ]," + LS
            + "  \"uiDescriptor\" : {" + LS
            + "    \"npm\" : \"name-of-module-in-npm\"" + LS
            + "  }" + LS
            + "}";

    RestAssuredClient c;

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docUiModuleInput).post("/_/proxy/modules").then().statusCode(201)
            .body(equalTo(docUiModuleOutput)).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    String location = r.getHeader("Location");

    c = api.createRestAssured();
    c.given()
            .get(location)
            .then().statusCode(200).body(equalTo(docUiModuleOutput));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    given().delete(location)
            .then().statusCode(204);
    checkDbIsEmpty("testUiModule done", context);

    async.complete();
  }

  private void checkDocker(Handler<AsyncResult<Void>> future) {
    HttpClient client = vertx.createHttpClient();
    final String dockerUrl = "http://localhost:4243";
    final String url = dockerUrl + "/images/json?all=1";
    HttpClientRequest req = client.getAbs(url, res -> {
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> {
        future.handle(Future.failedFuture(d.getCause()));
      });
      res.handler(d -> {
        body.appendBuffer(d);
      });
      res.endHandler(d -> {
        if (res.statusCode() == 200) {
          boolean gotIt = false;
          logger.info("RESULT\n" + body.toString());
          JsonArray ar = body.toJsonArray();
          for (int i = 0; i < ar.size(); i++) {
            JsonObject ob = ar.getJsonObject(i);
            JsonArray ar1 = ob.getJsonArray("RepoTags");
            for (int j = 0; j < ar1.size(); j++) {
              String tag = ar1.getString(j);
              if (tag != null && tag.startsWith("okapi-test-module")) {
                gotIt = true;
              }
            }
          }
          if (gotIt) {
            future.handle(Future.succeededFuture());
          } else {
            future.handle(Future.failedFuture("okapi-test-module not found"));
          }
        } else {
          String m = "checkDocker HTTP error "
                  + Integer.toString(res.statusCode()) + "\n"
                  + body.toString();
          logger.error(m);
          future.handle(Future.failedFuture(m));
        }
      });
    });
    req.exceptionHandler(d -> {
      future.handle(Future.failedFuture(d.getCause()));
    });
    req.end();
  }

  @Test
  public void testDockerModule(TestContext context) {
    async = context.async();
    checkDocker(res -> {
      if (res.succeeded()) {
        dockerTests(context);
      } else {
        logger.info("NOT running module within Docker test");
        async.complete();
      }
    });
  }

  private void dockerTests(TestContext context) {
    RestAssuredClient c;
    Response r;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    final String docSampleDockerModule = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"sample\"," + LS
            + "    \"version\" : \"1.0.0\"" + LS
            + "  } ]," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/test\"," + LS
            + "    \"level\" : \"30\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]," + LS
            + "  \"launchDescriptor\" : {" + LS
            + "    \"dockerImage\" : \"okapi-test-module\"," + LS
            + "    \"dockerCMD\" : [\"-Dfoo=bar\"]" + LS
            + "  }" + LS
            + "}";

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleDockerModule).post("/_/proxy/modules")
            .then()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSampleModule = r.getHeader("Location");

    final String doc1 = "{" + LS
            + "  \"srvcId\" : \"sample-module\"," + LS
            + "  \"nodeId\" : \"localhost\"" + LS
            + "}";

    c = api.createRestAssured();
    r = c.given().header("Content-Type", "application/json")
            .body(doc1).post("/_/discovery/modules")
            .then().statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    locationSample5Deployment = r.getHeader("Location");

    given().delete(locationSampleModule).then().statusCode(204);

    given().delete(locationSample5Deployment).then().statusCode(204);
    locationSample5Deployment = null;

    checkDbIsEmpty("testDockerModule done", context);

    async.complete();
  }
}
