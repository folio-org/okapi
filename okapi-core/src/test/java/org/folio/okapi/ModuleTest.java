package org.folio.okapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.UrlDecoder;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunnerWithParametersFactory;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(VertxUnitRunnerWithParametersFactory.class)
public class ModuleTest {

  @Parameterized.Parameters
  public static Iterable<String> data() {
    final String s = System.getProperty("testStorage");
    if (s != null) {
      return Arrays.asList(s.split("[ ,]+"));
    }
    final String f = System.getenv("okapiFastTest");
    if (f != null) {
      return Collections.singletonList("inmemory");
    } else {
      return Arrays.asList("inmemory", "postgres", "mongo");
    }
  }

  private final Logger logger = OkapiLogger.get();

  private Vertx vertx;

  private String locationSampleDeployment;
  private String locationHeaderDeployment;
  private String locationAuthDeployment;
  private String locationPreDeployment;
  private String locationPostDeployment;
  private String okapiToken;
  private final String okapiTenant = "roskilde";
  private HttpClient httpClient;
  private static final String LS = System.lineSeparator();
  private final int port = 9230;
  private static RamlDefinition api;
  private static PostgreSQLContainer<?> postgresSQLContainer;
  private static MongoDBContainer mongoDBContainer;

  private final JsonObject conf;

  // the one module that's always there. When running tests, the version is at 0.0.0
  // It gets set later in the compilation process.
  private static final String internalModuleDoc = "{" + LS
    + "  \"id\" : \"okapi-0.0.0\"," + LS
    + "  \"name\" : \"Okapi\"" + LS
    + "}";

  private String superTenantDoc = "[ {" + LS
    + "  \"id\" : \"supertenant\"," + LS
    + "  \"name\" : \"supertenant\"," + LS
    + "  \"description\" : \"Okapi built-in super tenant\"" + LS
    + "} ]";

  private void assertEmptyReport(RestAssuredClient c) {
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }

  @BeforeClass
  public static void setUpBeforeClass() {
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @AfterClass
  public static void tearDownAfterClass() {
    if (postgresSQLContainer != null) {
      postgresSQLContainer.stop();
    }
    if (mongoDBContainer != null) {
      mongoDBContainer.stop();
    }
  }

  public ModuleTest(String value) throws Exception {
    conf = new JsonObject();

    conf.put("storage", value)
        .put(ConfNames.DEPLOY_WAIT_ITERATIONS, 30)
        .put(ConfNames.ENABLE_TRACE_HEADERS, true)
        .put("port", "9230")
        .put("port_start", "9231")
        .put("port_end", "9237")
        .put("nodename", "node1");

    switch (value) {
      case "postgres":
        if (postgresSQLContainer == null) {
          postgresSQLContainer = new PostgreSQLContainer<>("postgres:12-alpine");
          postgresSQLContainer.start();
        }
        conf.put("postgres_username", postgresSQLContainer.getUsername());
        conf.put("postgres_password", postgresSQLContainer.getPassword());
        conf.put("postgres_database", postgresSQLContainer.getDatabaseName());
        conf.put("postgres_host", postgresSQLContainer.getHost());
        conf.put("postgres_port", postgresSQLContainer.getFirstMappedPort().toString());
        break;
      case "mongo":
        if (mongoDBContainer == null) {
          mongoDBContainer = new MongoDBContainer("mongo:3.6.20");
          mongoDBContainer.start();
        }
        conf.put("mongo_port", mongoDBContainer.getFirstMappedPort().toString());
        conf.put("mongo_host", mongoDBContainer.getHost());
        break;
    }
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    httpClient = vertx.createHttpClient();
    RestAssured.port = port;
    RestAssured.urlEncodingEnabled = false;

    conf.put("mode", "dev");

    JsonObject config = new JsonObject(conf.encode());
    config.put("postgres_db_init", "1");
    config.put("mongo_db_init", "1");

    DeploymentOptions opt = new DeploymentOptions().setConfig(config);
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    logger.debug("Cleaning up after ModuleTest");
    if (httpClient != null) {
      Async async = context.async();
      httpClient.request(HttpMethod.DELETE, port, "localhost", "/_/discovery/modules",
          context.asyncAssertSuccess(request -> {
            request.end();
            request.response(context.asyncAssertSuccess(response -> {
              context.assertEquals(204, response.statusCode());
              response.endHandler(x -> async.complete());
            }));
          }));
      async.await();
    }
    vertx.close(context.asyncAssertSuccess());
  }


  private void checkDbIsEmpty(String label, TestContext context) {

    logger.debug("Db check '" + label + "'");
    // Check that we are not depending on td() to undeploy modules
    Assert.assertNull("locationAuthDeployment", locationAuthDeployment);
    Assert.assertNull("locationSampleDeployment", locationSampleDeployment);
    Assert.assertNull("locationSample5Deployment", locationSampleDeployment);
    Assert.assertNull("locationHeaderDeployment", locationHeaderDeployment);

    String emptyListDoc = "[ ]";

    given().get("/_/deployment/modules").then()
      .statusCode(200)
      .body(equalTo(emptyListDoc));

    given().get("/_/discovery/nodes").then()
      .statusCode(200); // we still have a node!
    given().get("/_/discovery/modules").then()
      .statusCode(200).body(equalTo(emptyListDoc));

    given().get("/_/proxy/modules").then()
      .statusCode(200).body(equalTo("[ " + internalModuleDoc + " ]"));
    given().get("/_/proxy/tenants").then()
      .statusCode(200).body(equalTo(superTenantDoc));
    logger.debug("Db check '" + label + "' OK");
  }

  /**
   * Helper to create a tenant. So it can be done in a one-liner without
   * cluttering real tests. Actually testing the tenant stuff should be in its
   * own test.
   *
   * @return the location, for deleting it later. This has to be urldecoded,
   * because restAssured "helpfully" encodes any urls passed to it.
   */
  private String createTenant() {
    final String docTenant = "{" + LS
      + "  \"id\" : \"" + okapiTenant + "\"," + LS
      + "  \"name\" : \"" + okapiTenant + "\"," + LS
      + "  \"description\" : \"" + okapiTenant + " bibliotek\"" + LS
      + "}";
    final String loc = given()
      .header("Content-Type", "application/json")
      .body(docTenant)
      .post("/_/proxy/tenants")
      .then()
      .statusCode(201)
      .header("Location", containsString("/_/proxy/tenants"))
      .extract().header("Location");
    return loc;
  }

  private void updateCreateTenant() {
    final String docTenant = "{" + LS
      + "  \"id\" : \"" + okapiTenant + "\"," + LS
      + "  \"name\" : \"" + okapiTenant + "\"," + LS
      + "  \"description\" : \"" + okapiTenant + " bibliotek\"" + LS
      + "}";
    given().header("Content-Type", "application/json")
      .body(docTenant)
      .put("/_/proxy/tenants/" + okapiTenant)
      .then()
      .statusCode(200);
  }

  private void updateTenant(String location) {
    final String docTenant = given()
      .get(location)
      .then()
      .statusCode(200)
      .extract().body().asString();
    given().header("Content-Type", "application/json")
      .body(docTenant)
      .put(location)
      .then()
      .statusCode(200);
  }

  /**
   * Helper to create a module.
   *
   * @param md A full ModuleDescriptor
   * @return the URL to delete when done
   */
  private String createModule(String md) {
    RestAssuredClient c = api.createRestAssured3();
    final String loc = c.given()
      .header("Content-Type", "application/json")
      .body(md)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .header("Location",containsString("/_/proxy/modules"))
      .extract().header("Location");
    assertEmptyReport(c);
    return loc;
  }

  /**
   * Helper to deploy a module. Assumes that the ModuleDescriptor has a good
   * LaunchDescriptor.
   *
   * @param modId Id of the module to be deployed.
   * @return url to delete when done
   */
  private String deployModule(String modId) {
    final String instId = modId.replace("-module", "") + "-inst";
    final String docDeploy = "{" + LS
      + "  \"instId\" : \"" + instId + "\"," + LS
      + "  \"srvcId\" : \"" + modId + "\"," + LS
      + "  \"nodeId\" : \"localhost\"" + LS
      + "}";
    RestAssuredClient c = api.createRestAssured3();
    final String loc = c.given()
      .header("Content-Type", "application/json")
      .body(docDeploy)
      .post("/_/discovery/modules")
      .then()
      .statusCode(201)
      .header("Location",containsString("/_/discovery/modules"))
      .extract().header("Location");
    assertEmptyReport(c);
    return loc;
  }

  /**
   * Helper to enable a module for our test tenant.
   *
   * @param modId The module to enable
   * @return the location, so we can delete it later. Can safely be ignored.
   */
  private String enableModule(String modId) {
    final String docEnable = "{" + LS
      + "  \"id\" : \"" + modId + "\"" + LS
      + "}";
    final String location = given()
      .header("Content-Type", "application/json")
      .body(docEnable)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(201)
      .header("Location",containsString("/_/proxy/tenants"))
      .extract().header("Location");
    return location;
  }

  /**
   * Various tests around the filter modules.
   *
   * @param context
   */
  @Test
  public void testFilters(TestContext context) {
    RestAssuredClient c;
    Response r;

    checkDbIsEmpty("testFilters starting", context);
    // Set up a test tenant
    final String locTenant = createTenant();
    updateTenant(locTenant);

    // Set up our usual sample module
    final String testModJar = "../okapi-test-module/target/okapi-test-module-fat.jar";
    final String docSampleModule = "{" + LS
      + "  \"id\" : \"sample-f-module-1\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/testb\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"permissionSets\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testModJar + "\"" + LS
      + "  }" + LS
      + "}";
    String locSampleModule = createModule(docSampleModule);
    locationSampleDeployment = deployModule("sample-f-module-1");
    String locSampleEnable = enableModule("sample-f-module-1");
    logger.debug("testFilters sample: " + locSampleModule + " " + locationSampleDeployment + " " + locSampleEnable);

    // Declare and enable test-auth.
    // We use our mod-auth for all the filter phases, it can handle them
    final String testAuthJar = "../okapi-test-auth-module/target/okapi-test-auth-module-fat.jar";
    final String docAuthModule = "{" + LS
      + "  \"id\" : \"auth-f-module-1\"," + LS
      + "  \"name\" : \"auth\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"auth\"," + LS
      + "    \"version\" : \"1.2\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"path\" : \"/authn/login\"," + LS
      + "      \"level\" : \"20\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"*\" ]," + LS
      + "    \"path\" : \"/\"," + LS
      + "    \"phase\" : \"auth\"," + LS
      + "    \"type\" : \"headers\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testAuthJar + "\"" + LS
      + "  }" + LS
      + "}";
    String locAuthModule = createModule(docAuthModule);
    locationAuthDeployment = deployModule("auth-f-module-1");
    String locAuthEnable = enableModule("auth-f-module-1");
    logger.debug(" testFilters auth: " + locAuthModule + " " + locationAuthDeployment + " " + locAuthEnable);

    // login and get token
    final String docLogin = "{" + LS
      + "  \"tenant\" : \"" + okapiTenant + "\"," + LS
      + "  \"username\" : \"peter\"," + LS
      + "  \"password\" : \"peter-password\"" + LS
      + "}";
    okapiToken = given().header("Content-Type", "application/json").body(docLogin)
      .header("X-Okapi-Tenant", okapiTenant).post("/authn/login")
      .then().statusCode(200).extract().header("X-Okapi-Token");
    logger.debug(" testFilters Got auth token " + okapiToken);

    // Make a simple request. Checks that the auth filter gets called
    c = api.createRestAssured3();
    List<String> traces = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-Okapi-User-Id", "peter")
      .header("X-all-headers", "BL") // ask sample to report all headers
      .get("/testb")
      .then().statusCode(200)
      .body(containsString("It works"))
      .extract().headers().getValues("X-Okapi-Trace");
    Assert.assertTrue(traces.get(0).contains("GET auth-f-module-1"));
    Assert.assertTrue(traces.get(1).contains("GET sample-f-module-1"));

    // Test Auth filter returns error.
    // Caller should see Auth filter error.
    c = api.createRestAssured3();
    traces = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", "bad token") // ask Auth to return error
      .header("X-all-headers", "BL") // ask sample to report all headers
      .get("/testb")
      .then().statusCode(400) // should see Auth error
      .body(containsString("Auth.check: Bad JWT")) // should see Auth error content
      .extract().headers().getValues("X-Okapi-Trace");
    logger.debug("Filter test. Traces: " + Json.encode(traces));
    Assert.assertEquals(1,  traces.size()); // should be just one module in the trace
    Assert.assertTrue(traces.get(0).contains("GET auth-f-module-1"));

    // Create pre- and post- filters
    final String docFilterModule = "{" + LS
      + "  \"id\" : \"MODULE\"," + LS
      + "  \"name\" : \"MODULE\"," + LS
      + "  \"provides\" : [ ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"*\" ]," + LS
      + "    \"path\" : \"/\"," + LS
      + "    \"phase\" : \"PHASE\"," + LS // This will get replaced later
      + "    \"type\" : \"request-only\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      //      + "    \"type\" : \"request-response\"" + LS
      // The only known use case for these uses req-only, so that's what we
      // test with. Tested req-resp manually, and it seems to work too
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testAuthJar + "\"" + LS
      + "  }" + LS
      + "}";

    String docPreModule = docFilterModule
      .replaceAll("MODULE", "pre-f-module-1")
      .replaceAll("PHASE", "pre");
    logger.debug("testFilters: pre-filter: " + docPreModule);
    String locPreModule = createModule(docPreModule);
    locationPreDeployment = deployModule("pre-f-module-1");
    String locPreEnable = enableModule("pre-f-module-1");
    logger.debug("testFilters pre: " + locPreModule + " " + locationPreDeployment + " " + locPreEnable);

    String docPostModule = docFilterModule
      .replaceAll("MODULE", "post-f-module-1")
      .replaceAll("PHASE", "post");
    logger.debug("testFilters: post-filter: " + docPostModule);
    String locPostModule = createModule(docPostModule);
    locationPostDeployment = deployModule("post-f-module-1");
    String locPostEnable = enableModule("post-f-module-1");
    logger.debug("testFilters post: " + locPostModule + " " + locationPostDeployment + " " + locPostEnable);

    // Make a simple GET request. All three filters should be called
    //
    c = api.createRestAssured3();
    traces = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-all-headers", "BL") // ask sample to report all headers
      .header("X-filter-pre", "202") // ask pre-filter to return 202
      .header("X-filter-post", "203") // ask post-filter to return 203
      .get("/testb")
      .then().statusCode(200) // should see handler result
      .header("X-Handler-header", "OK") // should see handler headers
      .body(containsString("It works"))
      .extract().headers().getValues("X-Okapi-Trace");
    logger.debug("Filter test. Traces: " + Json.encode(traces));
    Assert.assertTrue(traces.get(0).contains("GET auth-f-module-1"));
    Assert.assertTrue(traces.get(1).contains("GET pre-f-module-1"));
    Assert.assertTrue(traces.get(1).contains("202"));
    Assert.assertTrue(traces.get(2).contains("GET sample-f-module-1"));
    Assert.assertTrue(traces.get(3).contains("GET post-f-module-1"));
    Assert.assertTrue(traces.get(3).contains("203"));

    // Make a GET request with special headers to test pre/post filters can
    // see request header IP, timestamp, and method (by returning 500)
    c = api.createRestAssured3();
    traces = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-all-headers", "BL") // ask sample to report all headers
      .header("X-filter-pre", "202") // ask pre-filter to return 202
      .header("X-filter-post", "203") // ask post-filter to return 203
      .header("X-request-pre-error", true) // overrule pre-filter to return 500
      .header("X-request-post-error", true) // overrule post-filter to return 500
      .get("/testb")
      .then().statusCode(200) // should see handler result
      .header("X-Handler-header", "OK") // should see handler headers
      .body(containsString("It works"))
      .extract().headers().getValues("X-Okapi-Trace");
    logger.debug("Filter test. Traces: " + Json.encode(traces));
    Assert.assertTrue(traces.get(0).contains("GET auth-f-module-1"));
    Assert.assertTrue(traces.get(1).contains("GET pre-f-module-1"));
    Assert.assertTrue(traces.get(1).contains("500"));
    Assert.assertTrue(traces.get(2).contains("GET sample-f-module-1"));
    Assert.assertTrue(traces.get(3).contains("GET post-f-module-1"));
    Assert.assertTrue(traces.get(3).contains("500"));

    // Make a simple GET request. All three filters including post-filter
    // should be called even though handler returns error
    c = api.createRestAssured3();
    traces = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-all-headers", "BL") // ask sample to report all headers
      .header("X-filter-pre", "202") // ask pre-filter to return 202
      .header("X-filter-post", "203") // ask post-filter to return 203
      .header("X-handler-error", true) // ask sample to return 500
      .get("/testb")
      .then().statusCode(500) // should see handler error
      .body(containsString("It does not work")) // should see error content
      .extract().headers().getValues("X-Okapi-Trace");
    logger.debug("Filter test. Traces: " + Json.encode(traces));
    Assert.assertTrue(traces.get(0).contains("GET auth-f-module-1"));
    Assert.assertTrue(traces.get(1).contains("GET pre-f-module-1"));
    Assert.assertTrue(traces.get(2).contains("GET sample-f-module-1"));
    // should see post-filter even though handler returns error
    Assert.assertTrue(traces.get(3).contains("GET post-f-module-1"));

    // Test Auth filter returns error.
    // Handler should be skipped, but not Pre/Post filters.
    // Caller should see Auth filter error.
    c = api.createRestAssured3();
    traces = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", "bad token") // ask Auth to return error
      .header("X-all-headers", "BL") // ask sample to report all headers
      .header("X-filter-pre", "202") // ask pre-filter to return 202
      .header("X-filter-post", "203") // ask post-filter to return 203
      .get("/testb")
      .then().statusCode(400) // should see Auth error
      .body(containsString("Auth.check: Bad JWT")) // should see Auth error content
      .extract().headers().getValues("X-Okapi-Trace");
    logger.debug("Filter test. Traces: " + Json.encode(traces));
    Assert.assertTrue(traces.get(0).contains("GET auth-f-module-1"));
    Assert.assertTrue(traces.get(1).contains("GET pre-f-module-1"));
    // should not see Handler in trace
    Assert.assertTrue(traces.get(2).contains("GET post-f-module-1"));

    // Test Pre/Post filter returns error.
    // All phases should be seen in trace.
    // Caller should see Handler response.
    List<String> modTraces = Arrays.asList("GET auth-f-module-1",
        "GET pre-f-module-1", "GET sample-f-module-1", "GET post-f-module-1");
    testPrePostFilterError(XOkapiHeaders.FILTER_PRE, modTraces);
    testPrePostFilterError(XOkapiHeaders.FILTER_POST, modTraces);

    // Make a simple POST request. All three filters should be called
    // test-module will return 200, which should not be
    // overwritten by the pre and post-filters that returns 202 and 203
    c = api.createRestAssured3();
    traces = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-all-headers", "BL") // ask sample to report all headers
      .header("X-filter-pre", "202") // ask pre-filter to return 202
      .header("X-filter-post", "203") // ask post-filter to return 203
      // Those returns coders should be overwritten by the 200 from the handler
      .body("Testing... ")
      .post("/testb")
      .then().statusCode(200)
      .body(containsString("Hello"))
      .extract().headers().getValues("X-Okapi-Trace");
    logger.debug("Filter test. Traces: " + Json.encode(traces));
    Assert.assertTrue(traces.get(0).contains("POST auth-f-module-1"));
    Assert.assertTrue(traces.get(1).contains("POST pre-f-module-1"));
    Assert.assertTrue(traces.get(1).contains("202"));
    Assert.assertTrue(traces.get(2).contains("POST sample-f-module-1"));
    Assert.assertTrue(traces.get(3).contains("POST post-f-module-1"));
    Assert.assertTrue(traces.get(3).contains("203"));

    // Clean up (in reverse order)
    logger.debug("testFilters starting to clean up");
    c = api.createRestAssured3();
    c.given().delete(locPostEnable).then().statusCode(204);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().delete(locationPostDeployment).then().statusCode(204);
    assertEmptyReport(c);
    locationPostDeployment = null;

    c = api.createRestAssured3();
    c.given().delete(locPostModule).then().statusCode(204);
    assertEmptyReport(c);

    given().delete(locPreEnable).then().statusCode(204);
    given().delete(locationPreDeployment).then().statusCode(204);
    locationPreDeployment = null;
    given().delete(locPreModule).then().statusCode(204);
    given().delete(locAuthEnable).then().statusCode(204);
    given().delete(locationAuthDeployment).then().statusCode(204);
    locationAuthDeployment = null;
    given().delete(locAuthModule).then().statusCode(204);
    given().delete(locSampleEnable).then().statusCode(204);
    given().delete(locationSampleDeployment).then().statusCode(204);
    locationSampleDeployment = null;
    given().delete(locSampleModule).then().statusCode(204);
    given().delete(locTenant).then().statusCode(204);
    logger.debug("testFilters clean up complete");
    checkDbIsEmpty("testFilters finished", context);
  }

  private void testPrePostFilterError(String phase, List<String> modTraces) {
    RestAssuredClient c = api.createRestAssured3();
    List<String> traces = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-all-headers", "BL") // ask sample to report all headers
      .header("X-filter-pre", "202") // ask pre-filter to return 202
      .header("X-filter-post", "203") // ask post-filter to return 203
      .header("X-filter-" + phase + "-error", true) // ask filter to return 500
      .get("/testb")
      .then().statusCode(200) // caller should not see pre/post filter error
      .extract().headers().getValues("X-Okapi-Trace");
    logger.debug("Filter test. Traces: " + Json.encode(traces));
    for (int i = 0, n = modTraces.size(); i < n; i++) {
      Assert.assertTrue(traces.get(i).contains(modTraces.get(i)));
      if (modTraces.get(i).contains(phase)) {
        Assert.assertTrue(traces.get(i).contains("500"));
      }
    }
  }

  /**
   * Tests that declare one module. Declares a single module in many ways, often
   * with errors. In the end the module gets deployed and enabled for a newly
   * created tenant, and a request is made to it. Uses the test module, but not
   * any auth module, that should be a separate test.
   *
   * @param context
   */
  @Test
  public void testOneModule(TestContext context) {
    RestAssuredClient c;
    Response r;
    checkDbIsEmpty("testOneModule starting", context);

    // Get a list of the one built-in module, and nothing else.
    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/modules")
      .then()
      .statusCode(200)
      .body(equalTo("[ " + internalModuleDoc + " ]"));
    assertEmptyReport(c);

    // Check that we refuse the request with a trailing slash
    given()
      .get("/_/proxy/modules/")
      .then()
      .statusCode(404);

    given()
      .get("/_/proxy/modules/no-module")
      .then()
      .statusCode(404);

    // Check that we refuse the request to unknown okapi service
    // (also check (manually!) that the parameters do not end in the log)
    given()
      .get("/_/foo?q=bar")
      .then()
      .statusCode(404);

    // This is a good ModuleDescriptor. For error tests, some things get
    // replaced out. Still some old-style fields here and there...
    // Note the '+' in the id, it is valid semver, but may give problems
    // in url-encoding things.
    final String testModJar = "../okapi-test-module/target/okapi-test-module-fat.jar";
    final String docSampleModule = "{" + LS
      + "  \"id\" : \"sample-module-1+1\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/testb\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ \"sample.needed\" ]," + LS
      + "      \"permissionsDesired\" : [ \"sample.extra\" ]," + LS
      + "      \"modulePermissions\" : [ \"sample.modperm\" ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"recurse\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\" ]," + LS
      + "      \"pathPattern\" : \"/recurse\"," + LS
      + "      \"type\" : \"request-response-1.0\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"path\" : \"/_/tenant\"," + LS
      + "      \"level\" : \"10\"," + LS
      + "      \"type\" : \"system\"," + LS // DEPRECATED, gives a warning
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"permissionSets\" : [ {" + LS
      + "    \"permissionName\" : \"everything\"," + LS
      + "    \"displayName\" : \"every possible permission\"," + LS
      + "    \"description\" : \"All permissions combined\"," + LS
      + "    \"subPermissions\" : [ \"sample.needed\", \"sample.extra\" ]" + LS
      + "  } ]," + LS
      + "  \"env\" : [ {" + LS
      + "    \"name\" : \"DB_HOST\"," + LS
      + "    \"value\" : \"localhost\"," + LS
      + "    \"description\" : \"PostgreSQL host\"" + LS
      + "  }, {" + LS
      + "    \"name\" : \"DB_PORT\"," + LS
      + "    \"value\" : \"5432\"," + LS
      + "    \"description\" : \"PostgreSQL port\"" + LS
      + "  } ]," + LS
      + "  \"metadata\" : {" + LS
      + "    \"scm\" : \"https://github.com/folio-org/mod-something\"," + LS
      + "    \"language\" : {" + LS
      + "      \"name\" : \"java\"," + LS
      + "      \"versions\" : [ 8.0, 11.0 ]" + LS
      + "    }" + LS
      + "  }," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testModJar + "\"" + LS
      + "  }" + LS
      + "}";

    // First some error checks
    // Invalid Json, a hanging comma
    String docHangingComma = docSampleModule.replace("system\"", "system\",");
    given()
      .header("Content-Type", "application/json")
      .body(docHangingComma)
      .post("/_/proxy/modules")
      .then()
      .statusCode(400);

    // Bad module id
    String docBadId = docSampleModule.replace("sample-module-1", "bad module id?!");
    given()
      .header("Content-Type", "application/json")
      .body(docBadId)
      .post("/_/proxy/modules")
      .then()
      .statusCode(400);

    // Missing module id
    given()
      .header("Content-Type", "application/json")
      .body("{\"name\" : \"sample-module\"}")
      .post("/_/proxy/modules")
      .then()
      .statusCode(400);

    // Empty module id
    given()
      .header("Content-Type", "application/json")
      .body(docSampleModule.replace("\"sample-module-1+1\"", "\"\""))
      .post("/_/proxy/modules")
      .then()
      .statusCode(400);

    // Bad interface type
    String docBadIntType = docSampleModule.replace("system", "strange interface type");
    given()
      .header("Content-Type", "application/json")
      .body(docBadIntType)
      .post("/_/proxy/modules")
      .then()
      .statusCode(400);

    // Bad RoutingEntry type
    String docBadReType = docSampleModule.replace("request-response", "strange-re-type");
    given()
      .header("Content-Type", "application/json")
      .body(docBadReType)
      .post("/_/proxy/modules")
      .then()
      .statusCode(400);

    String docMissingPath = docSampleModule.replace("/testb", "");
    given()
      .header("Content-Type", "application/json")
      .body(docMissingPath)
      .post("/_/proxy/modules")
      .then()
      .statusCode(400);

    String docBadPathPat = docSampleModule
      .replace("/testb", "/test.*b(/?)");  // invalid characters in pattern
    given()
      .header("Content-Type", "application/json")
      .body(docBadPathPat)
      .post("/_/proxy/modules")
      .then()
      .statusCode(400);

    String docbadRedir = docSampleModule.replace("request-response\"", "redirect\"");
    given()
      .header("Content-Type", "application/json")
      .body(docbadRedir)
      .post("/_/proxy/modules?check=false")
      .then()
      .statusCode(400);

    // Actually create the module
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule)
      .post("/_/proxy/modules?check=true")
      .then()
      .statusCode(201)
      .extract().response();
    assertEmptyReport(c);
    String locSampleModule = r.getHeader("Location");
    Assert.assertEquals("/_/proxy/modules/sample-module-1%2B1", locSampleModule);
    Assert.assertEquals("/_/proxy/modules/sample-module-1+1", UrlDecoder.decode(locSampleModule));

    // Damn restAssured encodes the urls in get(), so we need to decode this here.
    assertEmptyReport(c);

    // post it again.. Allowed because it is the same MD
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertEquals(r.getHeader("Location"), locSampleModule);
    assertEmptyReport(c);

    // post it again with slight modification
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule.replace("sample.extra\"", "sample.foo\""))
      .post("/_/proxy/modules")
      .then()
      .statusCode(400)
      .extract().response();
    assertEmptyReport(c);

    given()
      .header("Content-Type", "application/json")
      .body("{}")
      .post("/_/discovery/modules")
      .then()
      .statusCode(400);

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{\"srvcId\" : \"\"}")
      .post("/_/discovery/modules")
      .then()
      .statusCode(400);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{\"srvcId\" : \"1\"}")
      .post("/_/discovery/modules")
      .then()
      .statusCode(404);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{\"srvcId\" : \"1\", \"nodeId\" : \"foo\"}")
      .post("/_/discovery/modules")
      .then()
      .statusCode(404);
    assertEmptyReport(c);

    // Get the module
    c = api.createRestAssured3();
    c.given()
      .get(locSampleModule)
      .then()
      .statusCode(200).body(equalTo(docSampleModule));
    assertEmptyReport(c);

    // List the one module, and the built-in.
    final String expOneModList = "[ "
      + internalModuleDoc + ", {" + LS
      + "  \"id\" : \"sample-module-1+1\"," + LS
      + "  \"name\" : \"sample module\"" + LS
      + "} ]";
    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/modules")
      .then()
      .statusCode(200)
      .body(equalTo(expOneModList));
    assertEmptyReport(c);

    // Deploy the module - use the node name, not node id
    final String docDeploy = "{" + LS
      + "  \"instId\" : \"sample-inst\"," + LS
      + "  \"srvcId\" : \"sample-module-1+1\"," + LS
      //+ "  \"nodeId\" : \"localhost\"" + LS
      + "  \"nodeId\" : \"node1\"" + LS
      + "}";
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docDeploy)
      .post("/_/discovery/modules")
      .then()
      .statusCode(201).extract().response();
    assertEmptyReport(c);
    locationSampleDeployment = r.header("Location");

    r = c.given()
      .header("Content-Type", "application/json")
      .body(docDeploy)
      .post("/_/discovery/modules")
      .then()
      .statusCode(400).extract().response();
    assertEmptyReport(c);

    // Create a tenant and enable the module
    final String locTenant = createTenant();
    final String locEnable = enableModule("sample-module-1+1");

    // Try to enable a non-existing module
    final String docEnableNonExisting = "{" + LS
      + "  \"id\" : \"UnknownModule\"" + LS
      + "}";
    given()
      .header("Content-Type", "application/json")
      .body(docEnableNonExisting)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(404);

     // Make a simple request to the module
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/testb")
      .then().statusCode(200)
      .body(containsString("It works"));

    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .delete("/testb")
      .then().statusCode(204);

    // Make a more complex request that returns all headers and parameters
    // So the headers we check are those that the module sees and reports to
    // us, not necessarily those that Okapi would return to us.
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-all-headers", "H") // ask sample to report all headers
      .get("/testb?query=foo&limit=10")
      .then().statusCode(200)
      .header("X-Okapi-Url", "http://localhost:9230") // no trailing slash!
      .header("X-Url-Params", "query=foo&limit=10")
      .body(containsString("It works"));

    // Check that the module can call itself recursively, 5 time
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/recurse?depth=5")
      .then().statusCode(200)
      .body(containsString("5 4 3 2 1 Recursion done"));

    // Call the module via the redirect-url. No tenant header!
    // The RAML can not express this way of calling things, so there can not be
    // any tests for that...
    given()
      .get("/_/invoke/tenant/" + okapiTenant + "/testb")
      .then().statusCode(200)
      .body(containsString("It works"));
    given()
      .get("/_/invoke/tenant/" + okapiTenant + "/testb/foo/bar")
      .then().statusCode(404);
    given()
      .header("X-all-headers", "HB") // ask sample to report all headers
      .get("/_/invoke/tenant/" + okapiTenant + "/testb?query=foo")
      .then()
      .header("X-Url-Params", "query=foo")
      .statusCode(200);
    given()
      .header("Content-Type", "application/json")
      .body("Testing testb")
      .post("/_/invoke/tenant/" + okapiTenant + "/testb?query=foo")
      .then().statusCode(200);

    // Check that the tenant API got called (exactly once)
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-tenant-reqs", "yes")
      .get("/testb")
      .then()
      .statusCode(200)
      .body(equalTo("It works Tenant requests: POST-roskilde "));

    // Test a moduleDescriptor with empty arrays
    // We have seen errors with such before.
    final String docEmptyModule = "{" + LS
      + "  \"id\" : \"empty-module-1.0\"," + LS
      + "  \"name\" : \"empty module-1.0\"," + LS
      + "  \"tags\" : [ ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"provides\" : [ ]," + LS
      + "  \"filters\" : [ ]," + LS
      + "  \"permissionSets\" : [ ]," + LS
      + "  \"launchDescriptor\" : { }" + LS
      + "}";

    // create the module - no need to deploy and use, it won't work.
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docEmptyModule)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    assertEmptyReport(c);
    final String locEmptyModule = r.getHeader("Location");
    final String locEnableEmpty = enableModule("empty-module-1.0");

    // Create another empty module
    final String docEmptyModule2 = docEmptyModule
      .replaceAll("empty-module-1.0", "empty-module-1.1");
    final String locEmptyModule2 = createModule(docEmptyModule2);
    // upgrade our tenant to use the new version
    final String docEnableUpg = "{" + LS
      + "  \"id\" : \"empty-module-1.1\"" + LS
      + "}";
    final String locUpgEmpty = given()
      .header("Content-Type", "application/json")
      .body(docEnableUpg)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules/empty-module-1.0")
      .then()
      .statusCode(201)
      .header("Location", "/_/proxy/tenants/roskilde/modules/empty-module-1.1")
      .extract().header("Location");

    // Clean up, so the next test starts with a clean slate (in reverse order)
    logger.debug("testOneModule cleaning up");
    given().delete(locUpgEmpty).then().statusCode(204);
    given().delete(locEmptyModule2).then().statusCode(204);
    given().delete(locEmptyModule).then().statusCode(204);
    given().delete(locEnable).then().statusCode(204);
    given().delete(locTenant).then().statusCode(204);
    given().delete(locSampleModule).then().statusCode(204);
    given().delete(locationSampleDeployment).then().statusCode(204);
    locationSampleDeployment = null;

    checkDbIsEmpty("testOneModule done", context);
  }

  private String createHeaderModule(String tenantPermsVer) {
    final String testHdrJar = "../okapi-test-header-module/target/okapi-test-header-module-fat.jar";
    final String docHdrModule = "{" + LS
        + "  \"id\" : \"header-1\"," + LS
        + "  \"name\" : \"header-module\"," + LS
        + "  \"provides\" : [ {" + LS
        + "    \"id\" : \"_tenantPermissions\"," + LS
        + "    \"version\" : \"" + tenantPermsVer + "\"," + LS
        + "    \"interfaceType\" : \"system\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"POST\" ]," + LS
        + "      \"path\" : \"/_/tenantPermissions\"," + LS
        + "      \"level\" : \"20\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  }, {" + LS
        + "    \"id\" : \"permResult\"," + LS
        + "    \"version\" : \"1.0\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"GET\" ]," + LS
        + "      \"path\" : \"/permResult\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  } ]," + LS
        + "  \"launchDescriptor\" : {" + LS
        + "    \"exec\" : \"java -Dport=%p -jar " + testHdrJar + "\"" + LS
        + "  }" + LS
        + "}";

    // Create, deploy, and enable the header module
    return createModule(docHdrModule);
  }

  /**
   * Test system interfaces. Mostly about the system interfaces _tenant (on the
   * module itself, to initialize stuff), and _tenantPermissions to pass its
   * permissions to the permissions module.
   *
   * @param context
   */
  @Test
  public void testSystemInterfaces(TestContext context) {
    checkDbIsEmpty("testSystemInterfaces starting", context);

    RestAssuredClient c;
    Response r;

    // Set up a tenant to test with
    final String locTenant = createTenant();

    // Enable the Okapi internal module for our tenant.
    // This is not unlike what happens to the superTenant, who has the internal
    // module enabled from the boot up, before anyone can provide the
    // _tenantPermissions interface. Its permissions should be (re)loaded
    // when our Hdr module gets enabled.
    final String locInternal = enableModule("okapi-0.0.0");

    // Set up a module that does the _tenantPermissions interface that will
    // get called when sample gets enabled. We (ab)use the header module for
    // this.

    // Create, deploy, and enable the header module
    final String locHdrModule = createHeaderModule("2.0");
    locationHeaderDeployment = deployModule("header-1");
    final String docEnableHdr = "{" + LS
      + "  \"id\" : \"header-1\"" + LS
      + "}";

    // Enable the header module. Check that tenantPermissions gets called
    // both for header module, and the already-enabled okapi internal module.
    Headers headers = given()
      .header("Content-Type", "application/json")
      .body(docEnableHdr)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(201)
      .extract().headers();
    final String locHdrEnable = headers.getValue("Location");
    // one trace from Okapi, two from the header module since it's called twice
    context.assertEquals(3, headers.getValues("X-Okapi-Trace").size());

    given()
        .header("X-Okapi-Tenant", okapiTenant)
        .get("/permResult")
        .then()
        .statusCode(200)
        .body("$", hasSize(2))
        .body("[0].moduleId", is("header-1"))
        .body("[1].moduleId", is("okapi-0.0.0"));

    // Set up the test module
    // It provides a _tenant interface, but no _tenantPermissions
    // Enabling it will end up invoking the _tenantPermissions in header-module
    final String testModJar = "../okapi-test-module/target/okapi-test-module-fat.jar";
    final String docSampleModule = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"level\" : \"30\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ \"sample.needed\" ]," + LS
      + "      \"permissionsDesired\" : [ \"sample.extra\" ]," + LS
      + "      \"modulePermissions\" : [ \"sample.modperm\" ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"path\" : \"/_/tenant\"," + LS
      + "      \"level\" : \"10\"," + LS
      + "      \"type\" : \"system\"," + LS
      + "      \"permissionsRequired\" : [ ]," + LS
      + "      \"modulePermissions\" : [ \"sample.tenantperm\" ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"permissionSets\" : [ {" + LS
      + "    \"permissionName\" : \"everything\"," + LS
      + "    \"displayName\" : \"every possible permission\"," + LS
      + "    \"description\" : \"All permissions combined\"," + LS
      + "    \"subPermissions\" : [ \"sample.needed\", \"sample.extra\" ]," + LS
      + "    \"visible\" : true" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testModJar + "\"" + LS
      + "  }" + LS
      + "}";

    // Create and deploy the sample module
    final String locSampleModule = createModule(docSampleModule);
    locationSampleDeployment = deployModule("sample-module-1");

    // Enable the sample module. Verify that the _tenantPermissions gets
    // invoked.
    final String docEnable = "{" + LS
      + "  \"id\" : \"sample-module-1\"" + LS
      + "}";
    final String expPerms = "{ "
      + "\"moduleId\" : \"sample-module-1\", "
      + "\"perms\" : [ { "
      + "\"permissionName\" : \"everything\", "
      + "\"displayName\" : \"every possible permission\", "
      + "\"description\" : \"All permissions combined\", "
      + "\"subPermissions\" : [ \"sample.needed\", \"sample.extra\" ], "
      + "\"visible\" : true "
      + "}, { "
      + "\"permissionName\" : \"SYS#sample-module-1#/testb#[GET, POST]\", "
      + "\"displayName\" : \"System generated: SYS#sample-module-1#/testb#[GET, POST]\", "
      + "\"description\" : \"System generated permission set\", "
      + "\"subPermissions\" : [ \"sample.modperm\" ], "
      + "\"visible\" : false "
      + "}, { "
      + "\"permissionName\" : \"SYS#sample-module-1#/_/tenant#[POST, DELETE]\", "
      + "\"displayName\" : \"System generated: SYS#sample-module-1#/_/tenant#[POST, DELETE]\", "
      + "\"description\" : \"System generated permission set\", "
      + "\"subPermissions\" : [ \"sample.tenantperm\" ], "
      + "\"visible\" : false "
      + "} ] }";

    String locSampleEnable = given()
      .header("Content-Type", "application/json")
      .body(docEnable)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(201)
      .extract().header("Location");

    String body = given()
        .header("X-Okapi-Tenant", okapiTenant)
        .get("/permResult")
        .then()
        .statusCode(200)
        .extract().body().asString();

    JsonArray ar = new JsonArray(body);
    context.assertEquals(1, ar.size());
    context.assertEquals(new JsonObject(expPerms), ar.getJsonObject(0));

    // Try with a minimal MD, to see we don't have null pointers hanging around
    final String docSampleModule2 = "{" + LS
      + "  \"id\" : \"sample-module2-1\"," + LS
      + "  \"name\" : \"sample module2\"," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testModJar + "\"" + LS
      + "  }" + LS
      + "}";
    // Create the sample module
    final String locSampleModule2 = createModule(docSampleModule2);
    final String locationSampleDeployment2 = deployModule("sample-module2-1");

    // Enable the small module. Verify that the _tenantPermissions gets
    // invoked.
    final String docEnable2 = "{" + LS
      + "  \"id\" : \"sample-module2-1\"" + LS
      + "}";
    final String expPerms2 = "{ "
      + "\"moduleId\" : \"sample-module2-1\", "
      + "\"perms\" : null }";

    String locSampleEnable2 = given()
      .header("Content-Type", "application/json")
      .body(docEnable2)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(201)
      .extract().header("Location");

    body = given()
        .header("X-Okapi-Tenant", okapiTenant)
        .get("/permResult")
        .then()
        .statusCode(200)
        .extract().body().asString();

    ar = new JsonArray(body);
    context.assertEquals(1, ar.size());
    context.assertEquals(new JsonObject(expPerms2), ar.getJsonObject(0));

    // Tests to see that we get a new auth token for the system calls
    // Disable sample, so we can re-enable it after we have established auth
    given().delete(locSampleEnable).then().statusCode(204);
    locSampleEnable = null;

    // Declare and enable test-auth
    final String testAuthJar = "../okapi-test-auth-module/target/okapi-test-auth-module-fat.jar";
    final String docAuthModule = "{" + LS
      + "  \"id\" : \"auth-1\"," + LS
      + "  \"name\" : \"auth\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"auth\"," + LS
      + "    \"version\" : \"1.2\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"path\" : \"/authn/login\"," + LS
      + "      \"level\" : \"20\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"*\" ]," + LS
      + "    \"path\" : \"/\"," + LS
      + "    \"phase\" : \"auth\"," + LS
      + "    \"type\" : \"headers\"," + LS
      + "    \"permissionsRequired\" : [ ]," + LS
      + "    \"permissionsDesired\" : [ \"auth.extra\" ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testAuthJar + "\"" + LS
      + "  }" + LS
      + "}";
    final String docEnableAuth = "{" + LS
      + "  \"id\" : \"auth-1\"" + LS
      + "}";
    final String locAuthModule = createModule(docAuthModule);
    final String locAuthDeployment = deployModule("auth-1");
    final String locAuthEnable = given()
      .header("Content-Type", "application/json")
      .body(docEnableAuth)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(201)
      .extract().header("Location");

    // Re-enable sample.
    locSampleEnable = given()
      .header("Content-Type", "application/json")
      .body(docEnable)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(201)
      .extract().header("Location");

    body = given()
        .header("X-Okapi-Tenant", okapiTenant)
        .get("/permResult")
        .then()
        .statusCode(200)
        .extract().body().asString();

    ar = new JsonArray(body);
    context.assertEquals(3, ar.size());
    context.assertEquals(new JsonObject(expPerms), ar.getJsonObject(2));

    // Check that the tenant interface and the tenantpermission interfaces
    // were called with proper auth tokens and with ModulePermissions

    // Set up the test module
    // It provides a _tenant interface, but no _tenantPermissions
    // Enabling it will end up invoking the _tenantPermissions in header-module
    final String docSampleModuleUpdated = "{" + LS
      + "  \"id\" : \"sample-module-2\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"2.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"level\" : \"30\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ \"sample.needed\" ]," + LS
      + "      \"permissionsDesired\" : [ \"sample.extra\" ]," + LS
      + "      \"modulePermissions\" : [ \"sample.modperm\" ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"path\" : \"/_/tenant\"," + LS
      + "      \"level\" : \"10\"," + LS
      + "      \"type\" : \"system\"," + LS
      + "      \"permissionsRequired\" : [ ]," + LS
      + "      \"modulePermissions\" : [ \"sample.tenantperm\" ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"permissionSets\" : [ {" + LS
      + "    \"permissionName\" : \"all\"," + LS
      + "    \"replaces\" : [ \"everything\" ]," + LS
      + "    \"displayName\" : \"all possible permissions\"," + LS
      + "    \"description\" : \"All permissions combined\"," + LS
      + "    \"subPermissions\" : [ \"sample.needed\", \"sample.extra\" ]," + LS
      + "    \"visible\" : true" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testModJar + "\"" + LS
      + "  }" + LS
      + "}";

    // Create and deploy the sample module
    final String locSampleModuleUpdated = createModule(docSampleModuleUpdated);
    final String locationSampleDeploymentUpdated = deployModule("sample-module-2");

    // Enable the small module. Verify that the _tenantPermissions gets
    // invoked.
    final String docEnableUpdated = "[ {" + LS
      + "  \"id\" : \"sample-module-2\"," + LS
      + "  \"action\" : \"enable\"" + LS
      + "} ]";
    final String expPermsUpdated = "{ "
        + "\"moduleId\" : \"sample-module-2\", "
        + "\"perms\" : [ { "
        + "\"permissionName\" : \"all\", "
        + "\"replaces\" : [ \"everything\" ], "
        + "\"displayName\" : \"all possible permissions\", "
        + "\"description\" : \"All permissions combined\", "
        + "\"subPermissions\" : [ \"sample.needed\", \"sample.extra\" ], "
        + "\"visible\" : true "
        + "}, { "
        + "\"permissionName\" : \"SYS#sample-module-2#/testb#[GET, POST]\", "
        + "\"displayName\" : \"System generated: SYS#sample-module-2#/testb#[GET, POST]\", "
        + "\"description\" : \"System generated permission set\", "
        + "\"subPermissions\" : [ \"sample.modperm\" ], "
        + "\"visible\" : false "
        + "}, { "
        + "\"permissionName\" : \"SYS#sample-module-2#/_/tenant#[POST, DELETE]\", "
        + "\"displayName\" : \"System generated: SYS#sample-module-2#/_/tenant#[POST, DELETE]\", "
        + "\"description\" : \"System generated permission set\", "
        + "\"subPermissions\" : [ \"sample.tenantperm\" ], "
        + "\"visible\" : false "
        + "} ] }";

    given()
      .header("Content-Type", "application/json")
      .body(docEnableUpdated)
      .post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then()
      .statusCode(200);

    String locSampleEnableUpdated = "/_/proxy/tenants/" + okapiTenant + "/modules/sample-module-2";

    body = given()
        .header("X-Okapi-Tenant", okapiTenant)
        .get("/permResult")
        .then()
        .statusCode(200)
        .extract().body().asString();

    ar = new JsonArray(body);
    context.assertEquals(1, ar.size());
    context.assertEquals(new JsonObject(expPermsUpdated), ar.getJsonObject(0));

    // Clean up, so the next test starts with a clean slate (in reverse order)
    logger.debug("testSystemInterfaces cleaning up");

    given().delete(locSampleEnable).then().statusCode(204);

    given().delete(locAuthEnable).then().statusCode(204);
    given().delete(locAuthDeployment).then().statusCode(204);
    c = api.createRestAssured3();
    c.given().delete(locAuthModule).then().statusCode(204);
    assertEmptyReport(c);

    given().delete(locSampleEnableUpdated).then().statusCode(204);
    given().delete(locationSampleDeploymentUpdated).then().statusCode(204);
    given().delete(locSampleModuleUpdated).then().statusCode(204);

    given().delete(locSampleEnable2).then().statusCode(204);
    given().delete(locationSampleDeployment2).then().statusCode(204);
    given().delete(locSampleModule2).then().statusCode(204);
    //given().delete(locSampleEnable).then().statusCode(204);
    given().delete(locationSampleDeployment).then().statusCode(204);
    given().delete(locSampleModule).then().statusCode(204);
    locationSampleDeployment = null;
    given().delete(locHdrEnable).then().statusCode(204);
    given().delete(locationHeaderDeployment).then().statusCode(204);
    locationHeaderDeployment = null;
    given().delete(locHdrModule).then().statusCode(204);
    given().delete(locInternal).then().statusCode(204);
    given().delete(locTenant).then().statusCode(204);
    checkDbIsEmpty("testSystemInterfaces done", context);
  }

  /**
   * Test the various ways we can interact with /_/discovery/nodes.
   *
   * @param context
   */
  @Test
  public void testDiscoveryNodes(TestContext context) {
    RestAssuredClient c;
    Response r;
    checkDbIsEmpty("testDiscoveryNodes starting", context);

    String nodeListDoc = "[ {" + LS
      + "  \"nodeId\" : \"localhost\"," + LS
      + "  \"url\" : \"http://localhost:9230\"," + LS
      + "  \"nodeName\" : \"node1\"" + LS
      + "} ]";

    String nodeDoc = "{" + LS
      + "  \"nodeId\" : \"localhost\"," + LS
      + "  \"url\" : \"http://localhost:9230\"," + LS
      + "  \"nodeName\" : \"NewName\"" + LS
      + "}";

    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes").then().statusCode(200)
      .body(equalTo(nodeListDoc));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given()
      .body(nodeDoc)
      .header("Content-Type", "application/json")
      .put("/_/discovery/nodes/localhost")
      .then()
      .statusCode(200);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes")
      .then()
      .statusCode(200)
      .body(equalTo(nodeListDoc.replaceFirst("node1", "NewName")));
    assertEmptyReport(c);

    // Test some bad PUTs
    c = api.createRestAssured3();
    c.given()
      .body(nodeDoc)
      .header("Content-Type", "application/json")
      .put("/_/discovery/nodes/foobarhost")
      .then()
      .statusCode(404);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given()
      .body(nodeDoc.replaceFirst("\"localhost\"", "\"foobar\""))
      .header("Content-Type", "application/json")
      .put("/_/discovery/nodes/localhost")
      .then()
      .statusCode(400);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given()
      .body(nodeDoc.replaceFirst("\"http://localhost:9230\"", "\"MayNotChangeUrl\""))
      .header("Content-Type", "application/json")
      .put("/_/discovery/nodes/localhost")
      .then()
      .statusCode(400);
    assertEmptyReport(c);

    // Get it in various ways
    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes/localhost")
      .then()
      .statusCode(200)
      .body(equalTo(nodeDoc));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes/NewName")
      .then()
      .statusCode(200)
      .body(equalTo(nodeDoc));
    assertEmptyReport(c);

    logger.info("node test!!!!!!!!!!!!");
    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes/http%3A%2F%2Flocalhost%3A9230")
      .then() // Note that get() encodes the url.
      .statusCode(200) // when testing with curl, you need use http%3A%2F%2Flocal...
      .body(equalTo(nodeDoc));
    assertEmptyReport(c);

    checkDbIsEmpty("testDiscoveryNodes done", context);
  }

  @Test
  public void testDeployment(TestContext context) {
    Response r;

    RestAssuredClient c;

    final String docSampleModule = "{" + LS
      + "  \"id\" : \"sample-module-5.0\"," + LS
      + "  \"name\" : \"sample module for deployment test\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"level\" : \"30\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule).post("/_/proxy/modules")
      .then()
      //.log().all()
      .statusCode(201)
      .extract().response();
    assertEmptyReport(c);
    String locationSampleModule = r.getHeader("Location");

    c = api.createRestAssured3();
    c.given().get("/_/deployment/modules")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/deployment/modules/not_found")
      .then().statusCode(404);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/not_found")
      .then().statusCode(404);
    assertEmptyReport(c);

    final String doc1 = "{" + LS
      + "  \"instId\" : \"localhost-9231\"," + LS // set so we can compare with result
      + "  \"srvcId\" : \"sample-module-5.0\"," + LS
      + "  \"nodeId\" : \"localhost\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";

    given().header("Content-Type", "application/json")
      .body(doc1).post("/_/discovery/modules/") // extra slash !
      .then().statusCode(404);

    // with descriptor, but missing nodeId
    final String doc1a = "{" + LS
      + "  \"instId\" : \"localhost-9231\"," + LS
      + "  \"srvcId\" : \"sample-module-5.0\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(doc1a).post("/_/discovery/modules")
      .then().statusCode(400).body(containsString("missing nodeId"));
    assertEmptyReport(c);

    // missing instId
    final String docNoInstId = "{" + LS
      + "  \"srvcId\" : \"sample-module-5.0\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(docNoInstId).post("/_/discovery/modules")
      .then().statusCode(400).body(containsString("Needs instId"));
    assertEmptyReport(c);

    // unknown nodeId
    final String doc1b = "{" + LS
      + "  \"instId\" : \"localhost-9231\"," + LS
      + "  \"srvcId\" : \"sample-module-5.0\"," + LS
      + "  \"nodeId\" : \"foobarhost\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(doc1b).post("/_/discovery/modules")
      .then().statusCode(404);
    assertEmptyReport(c);

    final String doc2 = "{" + LS
      + "  \"instId\" : \"localhost-9231\"," + LS
      + "  \"srvcId\" : \"sample-module-5.0\"," + LS
      + "  \"nodeId\" : \"localhost\"," + LS
      + "  \"url\" : \"http://localhost:9231\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given().header("Content-Type", "application/json")
      .body(doc1).post("/_/discovery/modules")
      .then().statusCode(201)
      .body(equalTo(doc2))
      .extract().response();
    locationSampleDeployment = r.getHeader("Location");
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get(locationSampleDeployment).then().statusCode(200)
      .body(equalTo(doc2));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/deployment/modules")
      .then().statusCode(200)
      .body(equalTo("[ " + doc2 + " ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(doc2).post("/_/discovery/modules")
      .then().statusCode(400);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/sample-module-5.0")
      .then().statusCode(200)
      .body(equalTo("[ " + doc2 + " ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules")
      .then().statusCode(200)
      .body(equalTo("[ " + doc2 + " ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();

    final String envDoc = "{" + LS
      + "  \"name\" : \"name1\"," + LS
      + "  \"value\" : \"value1\"" + LS
      + "}";

    c.given()
      .header("Content-Type", "application/json")
      .body(envDoc).post("/_/env")
      .then().statusCode(201);
    assertEmptyReport(c);

    if (!"inmemory".equals(conf.getString("storage"))) {
      // just undeploy but keep it registered in discovery
      logger.info("doc2 " + doc2);
      JsonObject o2 = new JsonObject(doc2);
      String instId = o2.getString("instId");
      String loc = "http://localhost:9230/_/deployment/modules/" + instId;
      c = api.createRestAssured3();
      c.given().delete(loc).then().statusCode(204);
      assertEmptyReport(c);

      Async async = context.async();
      undeployAll().onComplete(x -> {
        DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
        vertx.deployVerticle(MainVerticle.class.getName(), opt, res -> {
          async.complete();
        });
      });
      async.await();
    }
    c = api.createRestAssured3();
    c.given().delete(locationSampleModule).then().statusCode(204);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .delete("/_/env/name1")
      .then().statusCode(204);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().delete(locationSampleDeployment).then().statusCode(204);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().delete(locationSampleDeployment).then().statusCode(404);
    assertEmptyReport(c);
    locationSampleDeployment = null;

    // Verify that the list works also after delete
    c = api.createRestAssured3();
    c.given().get("/_/deployment/modules")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    assertEmptyReport(c);

    // verify that module5 is no longer there
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/sample-module-5.0")
      .then().statusCode(404);
    assertEmptyReport(c);

    // verify that a never-seen module returns the same
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/UNKNOWN-MODULE")
      .then().statusCode(404);
    assertEmptyReport(c);

    // Deploy a module via its own LaunchDescriptor
    final String docSampleModuleDep1 = "{" + LS
      + "  \"id\" : \"sample-module-depl-1\"," + LS
      + "  \"name\" : \"sample module for deployment test\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"level\" : \"30\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModuleDep1).post("/_/proxy/modules")
      .then()
      //.log().all()
      .statusCode(201)
      .extract().response();
    assertEmptyReport(c);
    locationSampleModule = r.getHeader("Location");

    // Specify the node via url, to test that too
    final String docDeploy = "{" + LS
      + "  \"instId\" : \"localhost-9231\"," + LS
      + "  \"srvcId\" : \"sample-module-depl-1\"," + LS
      + "  \"nodeId\" : \"http://localhost:9230\"" + LS
      + "}";
    final String DeployResp = "{" + LS
      + "  \"instId\" : \"localhost-9231\"," + LS
      + "  \"srvcId\" : \"sample-module-depl-1\"," + LS
      + "  \"nodeId\" : \"http://localhost:9230\"," + LS
      + "  \"url\" : \"http://localhost:9231\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given().header("Content-Type", "application/json")
      .body(docDeploy).post("/_/discovery/modules")
      .then().statusCode(201)
      .body(equalTo(DeployResp))
      .extract().response();
    assertEmptyReport(c);
    locationSampleDeployment = r.getHeader("Location");

    // Would be nice to verify that the module works, but too much hassle with
    // tenants etc.
    // Undeploy.
    c = api.createRestAssured3();
    c.given().delete(locationSampleDeployment).then().statusCode(204);
    assertEmptyReport(c);
    // Undeploy again, to see it is gone
    c = api.createRestAssured3();
    c.given().delete(locationSampleDeployment).then().statusCode(404);
    assertEmptyReport(c);
    locationSampleDeployment = null;

    // and delete from the proxy
    c = api.createRestAssured3();
    c.given().delete(locationSampleModule)
      .then().statusCode(204);
    assertEmptyReport(c);

    checkDbIsEmpty("testDeployment done", context);
  }

  @Test
  public void testNotFound(TestContext context) {
    Response r;
    ValidatableResponse then;

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

    for (String type : Arrays.asList("request-response", "request-only", "headers")) {
      final String docSampleModule = "{" + LS
        + "  \"id\" : \"sample-module-1\"," + LS
        + "  \"filters\" : [ {" + LS
        + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
        + "    \"path\" : \"/test2\"," + LS
        + "    \"level\" : \"20\"," + LS
        + "    \"type\" : \"" + type + "\"," + LS
        + "    \"permissionsRequired\" : [ ]" + LS
        + "  } ]" + LS
        + "}";
      r = given()
        .header("Content-Type", "application/json")
        .body(docSampleModule).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();
      final String locationSampleModule = r.getHeader("Location");

      final String docLaunch1 = "{" + LS
        + "  \"srvcId\" : \"sample-module-1\"," + LS
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
      locationSampleDeployment = r.getHeader("Location");

      final String docEnableSample = "{" + LS
        + "  \"id\" : \"sample-module-1\"" + LS
        + "}";
      r = given()
        .header("Content-Type", "application/json")
        .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
        .then().statusCode(201)
        .body(equalTo(docEnableSample)).extract().response();
      final String enableLoc = r.getHeader("Location");

      given().header("X-Okapi-Tenant", okapiTenant)
        .body("bar").post("/test2")
        .then().statusCode(404);

      given().delete(enableLoc).then().statusCode(204);
      given().delete(locationSampleModule).then().statusCode(204);
      given().delete(locationSampleDeployment).then().statusCode(204);
      locationSampleDeployment = null;
    }
    given().delete(locationTenantRoskilde)
      .then().statusCode(204);
  }

  @Test
  public void testHeader(TestContext context) {
    RestAssuredClient c;
    Response r;
    ValidatableResponse then;

    final String docSampleModule = "{" + LS
      + "  \"id\" : \"sample-module-5.0\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";

    r = given()
      .header("Content-Type", "application/json")
      .body(docSampleModule).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    final String locationSampleModule = r.getHeader("Location");

    final String docHeaderModule = "{" + LS
      + "  \"id\" : \"header-module-1.0\"," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"level\" : \"10\"," + LS
      + "    \"type\" : \"headers\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  } ]" + LS
      + "}";
    r = given()
      .header("Content-Type", "application/json")
      .body(docHeaderModule).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    final String locationHeaderModule = r.getHeader("Location");

    final String docLaunch1 = "{" + LS
      + "  \"srvcId\" : \"sample-module-5.0\"," + LS
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
    locationSampleDeployment = r.getHeader("Location");

    final String docLaunch2 = "{" + LS
      + "  \"srvcId\" : \"header-module-1.0\"," + LS
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
      + "  \"id\" : \"sample-module-5.0\"" + LS
      + "}";
    given()
      .header("Content-Type", "application/json")
      .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .body(equalTo(docEnableSample));

    given()
      .delete(locationSampleModule)
      .then().statusCode(400)
      .body(equalTo("delete: module sample-module-5.0 is used by tenant " + okapiTenant));

    final String docEnableHeader = "{" + LS
      + "  \"id\" : \"header-module-1.0\"" + LS
      + "}";
    given()
      .header("Content-Type", "application/json")
      .body(docEnableHeader).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .body(equalTo(docEnableHeader));

    given().header("X-Okapi-Tenant", okapiTenant)
      .body("bar").post("/testb")
      .then().statusCode(200).body(equalTo("Hello foobar"))
      .extract().response();

    given().delete("/_/proxy/tenants/" + okapiTenant + "/modules/sample-module-5.0")
      .then().statusCode(204);

    given()
      .header("Content-Type", "application/json")
      .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
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
    given().delete(locationSampleDeployment).then().statusCode(204);
    locationSampleDeployment = null;
    given().delete(locationHeaderDeployment)
      .then().statusCode(204);
    locationHeaderDeployment = null;
    given().delete(locationHeaderModule)
      .then().statusCode(204);

    checkDbIsEmpty("testHeader done", context);
  }

  @Test
  public void testUiModule(TestContext context) {
    Response r;

    final String docUiModuleInput = "{" + LS
      + "  \"id\" : \"ui-1\"," + LS
      + "  \"name\" : \"sample-ui\"," + LS
      + "  \"uiDescriptor\" : {" + LS
      + "    \"npm\" : \"name-of-module-in-npm\"" + LS
      + "  }" + LS
      + "}";

    final String docUiModuleOutput = "{" + LS
      + "  \"id\" : \"ui-1\"," + LS
      + "  \"name\" : \"sample-ui\"," + LS
      + "  \"uiDescriptor\" : {" + LS
      + "    \"npm\" : \"name-of-module-in-npm\"" + LS
      + "  }" + LS
      + "}";

    RestAssuredClient c;

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docUiModuleInput).post("/_/proxy/modules").then().statusCode(201)
      .body(equalTo(docUiModuleOutput)).extract().response();
    assertEmptyReport(c);

    String location = r.getHeader("Location");

    c = api.createRestAssured3();
    c.given()
      .get(location)
      .then().statusCode(200).body(equalTo(docUiModuleOutput));
    assertEmptyReport(c);

    given().delete(location)
      .then().statusCode(204);
    checkDbIsEmpty("testUiModule done", context);
  }

  @Test
  public void testMultipleInterface(TestContext context) {
    RestAssuredClient c;
    Response r;

    Assert.assertNull("locationSampleDeployment", locationSampleDeployment);
    Assert.assertNull("locationHeaderDeployment", locationHeaderDeployment);

    final String testModJar = "../okapi-test-module/target/okapi-test-module-fat.jar";
    final String docSampleModule1 = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"name\" : \"sample module 1\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"interfaceType\" : \"proxy\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testModJar + "\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule1)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    assertEmptyReport(c);
    final String locationSampleModule1 = r.getHeader("Location");

    final String docSampleModule2 = "{" + LS
      + "  \"id\" : \"sample-module-2\"," + LS
      + "  \"name\" : \"sample module 2\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"interfaceType\" : \"proxy\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testModJar + "\"" + LS
      + "  }" + LS
      + "}";
    // Create and deploy the sample module
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule2)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    assertEmptyReport(c);
    final String locationSampleModule2 = r.getHeader("Location");

    updateCreateTenant();
    final String locEnable1 = enableModule("sample-module-1");

    // Same interface defined twice.
    final String docEnable2 = "{" + LS
      + "  \"id\" : \"" + "sample-module-2" + "\"" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docEnable2)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(400)
      .extract().response();
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces?full=true")
            .then().statusCode(200)
            .body(equalTo("[ {" + LS
                    + "  \"id\" : \"sample\"," + LS
                    + "  \"version\" : \"1.0\"," + LS
                    + "  \"interfaceType\" : \"proxy\"," + LS
                    + "  \"handlers\" : [ {" + LS
                    + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
                    + "    \"path\" : \"/testb\"," + LS
                    + "    \"permissionsRequired\" : [ ]" + LS
                    + "  } ]" + LS
                    + "} ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces?full=false")
            .then().statusCode(200)
            .body(equalTo("[ {" + LS
                    + "  \"id\" : \"sample\"," + LS
                    + "  \"version\" : \"1.0\"" + LS
                    + "} ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces?full=false&type=proxy")
            .then().statusCode(200)
            .body(equalTo("[ {" + LS
                    + "  \"id\" : \"sample\"," + LS
                    + "  \"version\" : \"1.0\"" + LS
                    + "} ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces?full=false&type=system")
            .then().statusCode(200)
            .body(equalTo("[ ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces/sample")
            .then().statusCode(200)
            .body(equalTo("[ {" + LS + "  \"id\" : \"sample-module-1\"" + LS + "} ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces/sample?type=proxy")
            .then().statusCode(200)
            .body(equalTo("[ {" + LS + "  \"id\" : \"sample-module-1\"" + LS + "} ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + "foo" + "/interfaces/sample")
      .then().statusCode(404);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces/bar")
      .then().statusCode(200).body(equalTo("[ ]"));
    assertEmptyReport(c);

    c = api.createRestAssured3();
    r = c.given().delete(locEnable1)
      .then().statusCode(204).extract().response();
    assertEmptyReport(c);

    c = api.createRestAssured3();
    r = c.given().delete(locationSampleModule1)
      .then().statusCode(204).extract().response();
    assertEmptyReport(c);

    c = api.createRestAssured3();
    r = c.given().delete(locationSampleModule2)
      .then().statusCode(204).extract().response();
    assertEmptyReport(c);

    final String docSampleModule3 = "{" + LS
      + "  \"id\" : \"sample-module-3\"," + LS
      + "  \"name\" : \"sample module 3\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"interfaceType\" : \"multiple\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testModJar + "\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule3)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    assertEmptyReport(c);
    final String locationSampleModule3 = r.getHeader("Location");

    final String docSampleModule4 = "{" + LS
      + "  \"id\" : \"sample-module-4\"," + LS
      + "  \"name\" : \"sample module 4\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"interfaceType\" : \"multiple\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testModJar + "\"," + LS
      + "    \"env\" : [ {" + LS
      + "      \"name\" : \"helloGreeting\"," + LS
      + "      \"value\" : \"hej\"" + LS
      + "    } ]" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule4)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    assertEmptyReport(c);
    final String locationSampleModule4 = r.getHeader("Location");

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces/sample")
      .then().statusCode(200).body(equalTo("[ ]"));
    assertEmptyReport(c);

    final String locEnable3 = enableModule("sample-module-3");
    this.locationSampleDeployment = deployModule("sample-module-3");

    final String locEnable4 = enableModule("sample-module-4");
    this.locationHeaderDeployment = deployModule("sample-module-4");

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces/sample")
      .then().statusCode(200).body(equalTo("[ {" + LS
      + "  \"id\" : \"sample-module-3\"" + LS
      + "}, {" + LS
      + "  \"id\" : \"sample-module-4\"" + LS
      + "} ]"));
    assertEmptyReport(c);
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/testb")
      .then().statusCode(404);

    given()
      .header("X-Okapi-Module-Id", "sample-module-u")
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/testb")
      .then().statusCode(404);

    r = given()
      .header("X-Okapi-Module-Id", "sample-module-3")
      .header("X-all-headers", "H") // makes module echo headers
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/testb")
      .then().statusCode(200).extract().response();
    // check that X-Okapi-Module-Id was not passed to it
    Assert.assertNull(r.headers().get("X-Okapi-Module-Id"));

    given()
      .header("X-Okapi-Module-Id", "sample-module-4")
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/testb")
      .then().statusCode(200);

    given().header("X-Okapi-Module-Id", "sample-module-3")
      .header("X-Okapi-Tenant", okapiTenant)
      .body("OkapiX").post("/testb")
      .then()
      .statusCode(200)
      .body(equalTo("Hello OkapiX"));

    given().header("X-Okapi-Module-Id", "sample-module-4")
      .header("X-Okapi-Tenant", okapiTenant)
      .body("OkapiX").post("/testb")
      .then()
      .statusCode(200)
      .body(equalTo("hej OkapiX"));

    // cleanup
    c = api.createRestAssured3();
    r = c.given().delete(locEnable3)
      .then().statusCode(204).extract().response();
    assertEmptyReport(c);

    c = api.createRestAssured3();
    r = c.given().delete(locEnable4)
      .then().statusCode(204).extract().response();
    assertEmptyReport(c);

    c = api.createRestAssured3();
    r = c.given().delete(locationSampleModule3)
      .then().statusCode(204).extract().response();
    assertEmptyReport(c);

    c = api.createRestAssured3();
    r = c.given().delete(locationSampleModule4)
      .then().statusCode(204).extract().response();
    assertEmptyReport(c);

    c = api.createRestAssured3();
    r = c.given().delete(locationSampleDeployment)
      .then().statusCode(204).extract().response();
    locationSampleDeployment = null;
    assertEmptyReport(c);

    c = api.createRestAssured3();
    r = c.given().delete(locationHeaderDeployment)
      .then().statusCode(204).extract().response();
    locationHeaderDeployment = null;
    assertEmptyReport(c);
  }

  @Test
  public void testVersion(TestContext context) {
    logger.info("testVersion starting");
    RestAssuredClient c;

    c = api.createRestAssured3();
    c.given().get("/_/version").then().statusCode(200).extract().response();
    assertEmptyReport(c);
  }

  @Test
  public void testSemVer(TestContext context) {
    RestAssuredClient c;
    Response r;
    c = api.createRestAssured3();

    String docSampleModule = "{" + LS
      + "  \"id\" : \"sample-1.2.3-alpha.1\"," + LS
      + "  \"name\" : \"sample module 3\"" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    assertEmptyReport(c);

    docSampleModule = "{" + LS
      + "  \"id\" : \"sample-1.2.3-SNAPSHOT.5\"," + LS
      + "  \"name\" : \"sample module 3\"" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    assertEmptyReport(c);

    docSampleModule = "{" + LS
      + "  \"id\" : \"sample-1.2.3-alpha.1+2017\"," + LS
      + "  \"name\" : \"sample module 3\"" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body(docSampleModule)
        .post("/_/proxy/modules")
        .then()
        .statusCode(201)
        .extract().response();
    assertEmptyReport(c);
  }

  private Future<Void> undeployAll() {
    Set<String> ids = vertx.deploymentIDs();
    Iterator<String> it = ids.iterator();
    Future<Void> future = Future.succeededFuture();
    while (it.hasNext()) {
      future = future.compose(x -> vertx.undeploy(it.next()));
    }
    return future;
  }

  private Future<String> redeploy() {
    httpClient = null;
    return undeployAll().compose(x -> {
      DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
      return vertx.deployVerticle(MainVerticle.class.getName(), opt);
    });
  }

  private Future<String> redeploy(TestContext context) {
    Async async = context.async();
    Future<String> future = redeploy().onComplete(res -> async.complete());
    async.await();
    return future;
  }

  @Test
  public void testInitdatabase(TestContext context) {
    conf.put("mode", "initdatabase");
    redeploy().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testInitdatabaseBadCredentials(TestContext context) {
    if (!"postgres".equals(conf.getString("storage"))) {
      return;
    }
    conf.put("mode", "initdatabase");
    conf.put("postgres_password", "badpass");
    redeploy().onComplete(context.asyncAssertFailure(cause ->
        context.assertTrue(cause.getMessage().contains(
          "password authentication failed for user "),
          cause.getMessage())));
  }

  @Test
  public void testPurgedatabase(TestContext context) {
    conf.put("mode", "purgedatabase");
    redeploy().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testInternalModule(TestContext context) {
    int foundStatus = "inmemory".equals(conf.getString("storage")) ? 404 : 200;

      // check that supertenant has okapi module enabled already
    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/supertenant/modules/okapi-0.0.0")
        .then().statusCode(200);

    // testlib has okapi module enabled
    given()
        .header("Content-Type", "application/json")
        .body(new JsonObject().put("id", "testlib").encode())
        .post("/_/proxy/tenants")
        .then().statusCode(201);

    given()
        .header("Content-Type", "application/json")
        .body(new JsonObject().put("id", "okapi").encode())
        .post("/_/proxy/tenants/testlib/modules")
        .then().statusCode(201);

    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/testlib/modules/okapi-0.0.0")
        .then().statusCode(200);

    // create & enable sample-module also for testlib
    JsonObject docSampleModule1 = new JsonObject()
        .put("id", "sample-module-1.0.0")
        .put("requires", new JsonArray())
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_timer")
                .put("version", "1.0")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/foo")
                        .put("unit", "second")
                        .put("delay", "20")
                    )
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/bar")
                        .put("unit", "second")
                        .put("delay", "60")
                    )
                )
            )
        );

    given()
        .get("/_/proxy/tenants/testlib/timers")
        .then().statusCode(200)
        .body("$", hasSize(0));

    given()
        .header("Content-Type", "application/json")
        .body(docSampleModule1.encode())
        .post("/_/proxy/modules")
        .then().statusCode(201);

    given()
        .header("Content-Type", "application/json")
        .body(new JsonObject().put("id", "sample-module").encode())
        .post("/_/proxy/tenants/testlib/modules")
        .then().statusCode(201);

    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/testlib/modules/sample-module-1.0.0")
        .then().statusCode(200);

    given()
        .get("/_/proxy/tenants/testlib/timers")
        .then().statusCode(200)
        .body("$", hasSize(2));

    JsonObject routingEntry = new JsonObject()
        .put("unit", "second")
        .put("delay", "19");

    given()
        .header("Content-Type", "application/json")
        .body(routingEntry.encode())
        .patch("/_/proxy/tenants/testlib/timers/sample-module_0") // patch foo
        .then().statusCode(204);

    given()
        .header("Content-Type", "application/json")
        .body(routingEntry.encode())
        .patch("/_/proxy/tenants/testlib/timers/sample-module_1") // patch bar
        .then().statusCode(204);

    given()
        .get("/_/proxy/tenants/testlib/timers")
        .then().statusCode(200)
        .body("$", hasSize(2));

    JsonObject docSampleModule2 = new JsonObject()
        .put("id", "sample-module-1.0.1")
        .put("requires", new JsonArray())
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_timer")
                .put("version", "1.0")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/foo")
                        .put("unit", "second")
                        .put("delay", "20")
                    )
                )
            )
        );

    given()
        .header("Content-Type", "application/json")
        .body(docSampleModule2.encode())
        .post("/_/proxy/modules")
        .then().statusCode(201);

    given()
        .header("Content-Type", "application/json")
        .body(new JsonObject().put("id", "sample-module-1.0.1").encode())
        .post("/_/proxy/tenants/testlib/modules/sample-module-1.0.0")
        .then().statusCode(201);

    given()
        .get("/_/proxy/tenants/testlib/timers")
        .then().statusCode(200)
        .body("$", hasSize(1));

    // nookapi tenant does not have okapi enabled
    given()
        .header("Content-Type", "application/json")
        .body(new JsonObject().put("id", "nookapi").encode())
        .post("/_/proxy/tenants")
        .then().statusCode(201);

    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/nookapi/modules/okapi-0.0.0")
        .then().statusCode(404);

    redeploy(context).onComplete(context.asyncAssertSuccess());

    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/supertenant/modules/okapi-0.0.0")
        .then().statusCode(200);
    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/testlib/modules/okapi-0.0.0")
        .then().statusCode(foundStatus);

    final String locHdrModule = createHeaderModule("1.1");

    given()
        .header("Content-Type", "application/json")
        .body("[{\"id\":\"header-1\", \"action\":\"enable\"}]")
        .post("/_/proxy/tenants/testlib/install?deploy=true")
        .then().statusCode(foundStatus);

    if (foundStatus == 200) {
      given()
          .header("X-Okapi-Tenant", "testlib")
          .get("/permResult")
          .then()
          .statusCode(200)
          .body("$", hasSize(3))
          .body("[0].moduleId", is("header-1"))
          .body("[1].moduleId", is("okapi-0.0.0"))
          .body("[2].moduleId", is("sample-module-1.0.1"));
    }

    conf.put("okapiVersion", "3.0.0");  // upgrade from 0.0.0 to 3.0.0

    redeploy(context).onComplete(context.asyncAssertSuccess());

    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/supertenant/modules/okapi-3.0.0")
        .then().statusCode(200);

    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/testlib/modules/okapi-3.0.0")
        .then().statusCode(foundStatus);

    if (foundStatus == 200) {
      given()
          .get("/_/discovery/modules")
          .then().statusCode(200)
          .body("$", hasSize(1))
          .body("[0].srvcId", is("header-1"));
    }

    if (foundStatus == 200) {
      given()
          .get("/_/proxy/tenants/testlib/modules")
          .then().statusCode(200)
          .body("$", hasSize(3));

      given()
          .header("X-Okapi-Tenant", "testlib")
          .get("/permResult")
          .then()
          .statusCode(200)
          .body("$", hasSize(1))
          .body("[0].moduleId", is("okapi-3.0.0"));
    }

    if (foundStatus == 200) {
      given()
          .get("/_/proxy/tenants/testlib/timers")
          .then().statusCode(200)
          .body("$", hasSize(1))
          .body("[0].id", is("sample-module_0"))
          .body("[0].routingEntry.delay", is("19"))
          .body("[0].routingEntry.unit", is("second"))
          .body("[0].modified", is(true));
    }

    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/nookapi/modules/okapi-3.0.0")
        .then().statusCode(404);

    conf.put("okapiVersion", "2.0.0"); // downgrade from 3.0.0 to 2.0.0 (which is not possible)

    redeploy(context).onComplete(context.asyncAssertSuccess());

    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/supertenant/modules/okapi-3.0.0")
        .then().statusCode(foundStatus);
    given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/testlib/modules/okapi-3.0.0")
        .then().statusCode(foundStatus);
    conf.remove("okapiVersion");
  }

  @Test
  public void testEnvStored(TestContext context) {
    RestAssuredClient c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(new JsonObject().put("name", "name1").put("value", "value1").encode()).post("/_/env")
        .then().statusCode(201);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .get("/_/env/name1")
        .then().statusCode(200);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .delete("/_/env/name1")
        .then().statusCode(204);
    assertEmptyReport(c);

    redeploy(context).onComplete(context.asyncAssertSuccess());

    // check that it is still gone after Okapi is restarted with any storage OKAPI-931
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .get("/_/env/name1")
        .then().statusCode(404);
    assertEmptyReport(c);
  }


  @Test
  public void testNoTenant(TestContext context) {
    RestAssuredClient c;
    Response r;

    final String tenant = "noTenant";

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + tenant).then().statusCode(404);
    assertEmptyReport(c);

    c = api.createRestAssured3();
    c.given().delete("/_/proxy/tenants/" + tenant).then().statusCode(404);
    assertEmptyReport(c);

    final String docEnable = "{" + LS
        + "  \"id\" : \"" + "mod-1.2.3"+ "\"" + LS
        + "}";
    c.given()
        .header("Content-Type", "application/json")
        .body(docEnable)
        .post("/_/proxy/tenants/" + tenant + "/modules")
        .then()
        .statusCode(404);
    assertEmptyReport(c);
  }

  @Test
  public void testDeleteNonExistingModule() {
    RestAssuredClient c = api.createRestAssured3();
    c.given().delete("/_/proxy/modules/foo-1.0.0")
        .then().statusCode(404).body(containsString("delete: module foo-1.0.0 does not exist"));
    assertEmptyReport(c);
  }

  /**
   * Test that unknown versions of _tenantPermissions are rejected.
   *
   * @param context
   */
  @Test
  public void testTenantPermissionsUnknownVersion(TestContext context) {
    checkDbIsEmpty("testTenantPermissionsUnknownVersion starting", context);

    // Set up a tenant to test with
    final String locTenant = createTenant();

    // Enable the Okapi internal module for our tenant.
    // This is not unlike what happens to the superTenant, who has the internal
    // module enabled from the boot up, before anyone can provide the
    // _tenantPermissions interface. Its permissions should normally be (re)loaded
    // when our Hdr module gets enabled, but we're expecting the call to enable
    // mod-permissions to fail due to an unknown version of _tenantPermissions
    final String locInternal = enableModule("okapi-0.0.0");

    // Set up a module that does the _tenantPermissions interface that will
    // get called when sample gets enabled. We (ab)use the header module for
    // this.

    // Create, deploy, and enable the header module w/ unknown _tenantPermissions version
    final String locHdrModule = createHeaderModule("9.0");
    locationHeaderDeployment = deployModule("header-1");
    final String docEnableHdr = "{" + LS
      + "  \"id\" : \"header-1\"" + LS
      + "}";

    // Enable the header module. Check that we get an appropriate error response.
    String body = given()
      .header("Content-Type", "application/json")
      .body(docEnableHdr)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(400)
      .extract().asString();
    context.assertEquals("Unknown version of _tenantPermissions interface in use 9.0.", body);

    // Clean up, so the next test starts with a clean slate (in reverse order)
    logger.debug("testTenantPermissionsUnknownVersion cleaning up");

    given().delete(locationHeaderDeployment).then().statusCode(204);
    locationHeaderDeployment = null;
    given().delete(locHdrModule).then().statusCode(204);
    given().delete(locInternal).then().statusCode(204);
    given().delete(locTenant).then().statusCode(204);
    checkDbIsEmpty("testSystemInterfaces done", context);
  }

  @Test
  public void testPullModules(TestContext context) {
    vertx
        .createHttpServer()
        .requestHandler(request -> {
          if (request.path().equals("/_/version")) {
            request.response().setStatusCode(200).end("registry 4.5.6");
            return;
          }
          request.response().setStatusCode(200).end(
              "[      {\"id\":\"foo-1.2.3\", \"name\":\"foo\"}, "
                  + " {\"id\":\"bar-4.5.6\", \"name\":\"bar\"} ]");
        })
        .listen(0)
        .compose(registry -> vertx.executeBlocking(promise -> {
          given()
              .header("Content-Type", "application/json")
              .body("{ \"urls\" : [ \"http://localhost:" + registry.actualPort() + "\" ] }")
              .post("/_/proxy/pull/modules")
          .then().log().ifValidationFails()
              .statusCode(200)
              .body("$", hasSize(2));

          given()
              .header("Content-Type", "application/json")
              .body("{ \"urls\" : [ \"http://localhost:" + registry.actualPort() + "\" ] }")
              .post("/_/proxy/pull/modules")
          .then().log().ifValidationFails()
              .statusCode(200)
              .body("$", hasSize(0));

          promise.complete();
        }))
        .onComplete(context.asyncAssertSuccess());
  }
}
