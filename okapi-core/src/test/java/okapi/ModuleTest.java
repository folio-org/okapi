package okapi;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.folio.okapi.MainVerticle;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunnerWithParametersFactory;
import io.vertx.ext.web.impl.Utils;

import java.util.*;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runners.Parameterized;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import static ru.yandex.qatools.embed.postgresql.distribution.Version.Main.V9_6;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.process.runtime.Network;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import static io.restassured.RestAssured.given;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.okapi.common.OkapiLogger;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(VertxUnitRunnerWithParametersFactory.class)
public class ModuleTest {

  @Parameterized.Parameters
  public static Iterable<String> data() {
    final String s = System.getProperty("ModuleTestStorage");
    if (s != null) {
      return Arrays.asList(s.split(" "));
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
  private Async async;

  private String locationSampleDeployment;
  private String locationHeaderDeployment;
  private String locationAuthDeployment = null;
  private String okapiToken;
  private final String okapiTenant = "roskilde";
  private HttpClient httpClient;
  private static final String LS = System.lineSeparator();
  private final int port = 9230;
  private static final int POSTGRES_PORT = 9238;
  private static final int MONGO_PORT = 9239;
  private static EmbeddedPostgres postgres;
  private static MongodExecutable mongoExe;
  private static MongodProcess mongoD;

  private final JsonObject conf;

  // the one module that's always there. When running tests, the version is at 0.0.0
  // It gets set later in the compilation process.
  private static final String internalModuleDoc = "{" + LS
    + "  \"id\" : \"okapi-0.0.0\"," + LS
    + "  \"name\" : \"Okapi\"" + LS
    + "}";

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    if (postgres != null) {
      postgres.stop();
    }
    if (mongoD != null) {
      mongoD.stop();
    }
    if (mongoExe != null) {
      mongoExe.stop();
    }
  }

  public ModuleTest(String value) throws Exception {
    conf = new JsonObject();

    conf.put("storage", value)
      .put("port", "9230")
      .put("port_start", "9231")
      .put("port_end", "9237")
      .put("nodename", "node1");

    if ("postgres".equals(value)) {
      conf.put("postgres_host", "localhost")
        .put("postgres_port", Integer.toString(POSTGRES_PORT));
      if (postgres == null) {
        postgres = new EmbeddedPostgres(V9_6);
        postgres.start("localhost", POSTGRES_PORT, "okapi", "okapi", "okapi25");
      }
    } else if ("mongo".equals(value)) {
      conf.put("mongo_host", "localhost")
        .put("mongo_port", Integer.toString(MONGO_PORT));
      if (mongoD == null) {
        MongodStarter starter = MongodStarter.getDefaultInstance();
        mongoExe = starter.prepare(new MongodConfigBuilder()
          .version(de.flapdoodle.embed.mongo.distribution.Version.V3_4_1)
          .net(new Net("localhost", MONGO_PORT, Network.localhostIsIPv6()))
          .build());
        mongoD = mongoExe.start();
      }
    }
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    httpClient = vertx.createHttpClient();
    RestAssured.port = port;

    conf.put("postgres_db_init", "1");
    conf.put("mongo_db_init", "1");
    DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    logger.info("Cleaning up after ModuleTest");
    async = context.async();
    td(context);
  }

  private void td(TestContext context) {
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

  private void checkDbIsEmpty(String label, TestContext context) {

    logger.debug("Db check '" + label + "'");
    // Check that we are not depending on td() to undeploy modules
    Assert.assertNull("locationAuthDeployment", locationAuthDeployment);
    Assert.assertNull("locationSampleDeployment", locationSampleDeployment);
    Assert.assertNull("locationSample5Deployment", locationSampleDeployment);
    Assert.assertNull("locationHeaderDeployment", locationHeaderDeployment);

    String emptyListDoc = "[ ]";

    String superTenantDoc = "[ {" + LS
      + "  \"id\" : \"supertenant\"," + LS
      + "  \"name\" : \"supertenant\"," + LS
      + "  \"description\" : \"Okapi built-in super tenant\"" + LS
      + "} ]";
    given().get("/_/deployment/modules").then()
      .log().ifValidationFails().statusCode(200)
      .body(equalTo(emptyListDoc));

    given().get("/_/discovery/nodes").then()
      .log().ifValidationFails().statusCode(200); // we still have a node!
    given().get("/_/discovery/modules").then()
      .log().ifValidationFails().statusCode(200).body(equalTo(emptyListDoc));

    given().get("/_/proxy/modules").then()
      .log().ifValidationFails().statusCode(200).body(equalTo("[ " + internalModuleDoc + " ]"));
    given().get("/_/proxy/tenants").then()
      .log().ifValidationFails().statusCode(200).body(equalTo(superTenantDoc));
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
      .header("Location",containsString("/_/proxy/tenants"))
      .log().ifValidationFails()
      .extract().header("Location");
    return Utils.urlDecode(loc, false);
  }

  /**
   * Helper to create a module.
   *
   * @param md A full ModuleDescriptor
   * @return the URL to delete when done
   */
  private String createModule(String md) {
    final String loc = given()
      .header("Content-Type", "application/json")
      .body(md)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .header("Location",containsString("/_/proxy/modules"))
      .log().ifValidationFails()
      .extract().header("Location");
    return Utils.urlDecode(loc, false);
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
    final String loc = given()
      .header("Content-Type", "application/json")
      .body(docDeploy)
      .post("/_/discovery/modules")
      .then()
      .statusCode(201)
      .header("Location",containsString("/_/discovery/modules"))
      .log().ifValidationFails()
      .extract().header("Location");
    return Utils.urlDecode(loc, false);
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
    return Utils.urlDecode(location, false);
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
    async = context.async();

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

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
    Assert.assertTrue(c.getLastReport().isEmpty());

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
      + "      \"type\" : \"request-response-1.0\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"path\" : \"/_/tenant\"," + LS
      + "      \"level\" : \"10\"," + LS
      + "      \"type\" : \"system\"" + LS // DEPRECATED, gives a warning
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"permissionSets\" : [ {" + LS
      + "    \"permissionName\" : \"everything\"," + LS
      + "    \"displayName\" : \"every possible permission\"," + LS
      + "    \"description\" : \"All permissions combined\"," + LS
      + "    \"subPermissions\" : [ \"sample.needed\", \"sample.extra\" ]" + LS
      + "  } ]," + LS
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
      .post("/_/proxy/modules")
      .then()
      .statusCode(400);

    // Actually create the module
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    String locSampleModule = r.getHeader("Location");
    Assert.assertTrue(locSampleModule.equals("/_/proxy/modules/sample-module-1%2B1"));
    locSampleModule = Utils.urlDecode(locSampleModule, false);
    // Damn restAssured encodes the urls in get(), so we need to decode this here.
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // post it again.. Allowed because it is the same MD
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule)
      .post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertEquals(Utils.urlDecode(r.getHeader("Location"), false), locSampleModule);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // post it again with slight modification
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule.replace("sample.extra\"", "sample.foo\""))
      .post("/_/proxy/modules")
      .then()
      .statusCode(400)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{\"srvcId\" : \"1\"}")
      .post("/_/discovery/modules")
      .then()
      .statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{\"srvcId\" : \"1\", \"nodeId\" : \"foo\"}")
      .post("/_/discovery/modules")
      .then()
      .statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Get the module
    c = api.createRestAssured3();
    c.given()
      .get(locSampleModule)
      .then()
      .statusCode(200).body(equalTo(docSampleModule));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // List the one module, and the built-in.
    final String expOneModList = "[ {" + LS
      + "  \"id\" : \"sample-module-1+1\"," + LS
      + "  \"name\" : \"sample module\"" + LS
      + "}, " + internalModuleDoc
      + " ]";
    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/modules")
      .then()
      .statusCode(200)
      .body(equalTo(expOneModList));
    Assert.assertTrue(c.getLastReport().isEmpty());

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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationSampleDeployment = Utils.urlDecode(r.header("Location"), false);

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
      .statusCode(404)
      .log().ifValidationFails();

     // Make a simple request to the module
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/testb")
      .then().statusCode(200)
      .body(containsString("It works"));

    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .delete("/testb")
      .then().statusCode(204)
      .log().ifValidationFails();

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
      .log().all()
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
      .log().ifValidationFails()
      .header("X-Url-Params", "query=foo")
      .statusCode(200);
    given()
      .header("Content-Type", "application/json")
      .body("Testing testb")
      .post("/_/invoke/tenant/" + okapiTenant + "/testb?query=foo")
      .then().statusCode(200);

    // double slash does not match invoke
    given()
      .header("Content-Type", "application/json")
      .body("Testing testb")
      .post("//_/invoke/tenant/" + okapiTenant + "/testb")
      .then().statusCode(403);

    // Check that the tenant API got called (exactly once)
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-tenant-reqs", "yes")
      .get("/testb")
      .then()
      .statusCode(200)
      .body(equalTo("It works Tenant requests: POST-roskilde "))
      .log().ifValidationFails();

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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
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
    given().delete(locUpgEmpty).then().log().ifValidationFails().statusCode(204);
    //given().delete(locEnableEmpty).then().log().ifValidationFails().statusCode(204);
    given().delete(locEmptyModule2).then().log().ifValidationFails().statusCode(204);
    given().delete(locEmptyModule).then().log().ifValidationFails().statusCode(204);
    given().delete(locEnable).then().log().ifValidationFails().statusCode(204);
    given().delete(locTenant).then().log().ifValidationFails().statusCode(204);
    given().delete(locSampleModule).then().log().ifValidationFails().statusCode(204);
    given().delete(locationSampleDeployment).then().log().ifValidationFails().statusCode(204);
    locationSampleDeployment = null;

    checkDbIsEmpty("testOneModule done", context);
    async.complete();
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
    async = context.async();
    checkDbIsEmpty("testSystemInterfaces starting", context);

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

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
    final String testHdrJar = "../okapi-test-header-module/target/okapi-test-header-module-fat.jar";
    final String docHdrModule = "{" + LS
      + "  \"id\" : \"header-1\"," + LS
      + "  \"name\" : \"header-module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenantPermissions\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"path\" : \"/_/tenantPermissions\"," + LS
      + "      \"level\" : \"20\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar " + testHdrJar + "\"" + LS
      + "  }" + LS
      + "}";

    // Create, deploy, and enable the header module
    final String locHdrModule = createModule(docHdrModule);
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
      .log().ifValidationFails()
      .extract().headers();
    final String locHdrEnable = headers.getValue("Location");
    List<Header> list = headers.getList("X-Tenant-Perms-Result");
    Assert.assertEquals(2, list.size()); // one for okapi, one for header-1
    Assert.assertThat("okapi perm result",
      list.get(0).getValue(), containsString("okapi.all"));
    Assert.assertThat("header-1perm result",
      list.get(1).getValue(), containsString("header-1"));

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
      + "} ] }";

    String locSampleEnable = given()
      .header("Content-Type", "application/json")
      .body(docEnable)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(201)
      .log().ifValidationFails()
      .header("X-Tenant-Perms-Result", expPerms)
      .extract().header("Location");

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
      .log().ifValidationFails()
      .header("X-Tenant-Perms-Result", expPerms2)
      .extract().header("Location");

    // Tests to see that we get a new auth token for the system calls
    // Disable sample, so we can re-enable it after we have established auth
    given().delete(locSampleEnable).then().log().ifValidationFails().statusCode(204);
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
      + "      \"type\" : \"request-response\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"*\" ]," + LS
      + "    \"path\" : \"/\"," + LS
      + "    \"phase\" : \"auth\"," + LS
      + "    \"type\" : \"request-response\"," + LS // Headers-only ?
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
      .log().ifValidationFails()
      .extract().header("Location");

    // Re-enable sample.
    locSampleEnable = given()
      .header("Content-Type", "application/json")
      .body(docEnable)
      .post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then()
      .statusCode(201)
      .log().ifValidationFails()
      .header("X-Tenant-Perms-Result", expPerms)
      .extract().header("Location");
    // Check that the tenant interface and the tenantpermission interfaces
    // were called with proper auth tokens and with ModulePermissions

    // Clean up, so the next test starts with a clean slate (in reverse order)
    logger.debug("testSystemInterfaces cleaning up");

    given().delete(locSampleEnable).then().log().ifValidationFails().statusCode(204);

    given().delete(locAuthEnable).then().log().ifValidationFails().statusCode(204);
    given().delete(locAuthDeployment).then().log().ifValidationFails().statusCode(204);
    given().delete(locAuthModule).then().log().ifValidationFails().statusCode(204);

    given().delete(locSampleEnable2).then().log().ifValidationFails().statusCode(204);
    given().delete(locationSampleDeployment2).then().log().ifValidationFails().statusCode(204);
    given().delete(locSampleModule2).then().log().ifValidationFails().statusCode(204);
    //given().delete(locSampleEnable).then().log().ifValidationFails().statusCode(204);
    given().delete(locationSampleDeployment).then().log().ifValidationFails().statusCode(204);
    given().delete(locSampleModule).then().log().ifValidationFails().statusCode(204);
    locationSampleDeployment = null;
    given().delete(locHdrEnable).then().log().ifValidationFails().statusCode(204);
    given().delete(locationHeaderDeployment).then().log().ifValidationFails().statusCode(204);
    locationHeaderDeployment = null;
    given().delete(locHdrModule).then().log().ifValidationFails().statusCode(204);
    given().delete(locInternal).then().log().ifValidationFails().statusCode(204);
    given().delete(locTenant).then().log().ifValidationFails().statusCode(204);
    checkDbIsEmpty("testSystemInterfaces done", context);
    async.complete();
  }

  /**
   * Test the various ways we can interact with /_/discovery/nodes.
   *
   * @param context
   */
  @Test
  public void testDiscoveryNodes(TestContext context) {
    async = context.async();
    RestAssuredClient c;
    Response r;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");
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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .body(nodeDoc)
      .header("Content-Type", "application/json")
      .put("/_/discovery/nodes/localhost")
      .then()
      .log().ifValidationFails()
      .statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes")
      .then()
      .statusCode(200)
      .body(equalTo(nodeListDoc.replaceFirst("node1", "NewName")))
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Test some bad PUTs
    c = api.createRestAssured3();
    c.given()
      .body(nodeDoc)
      .header("Content-Type", "application/json")
      .put("/_/discovery/nodes/foobarhost")
      .then()
      .statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .body(nodeDoc.replaceFirst("\"localhost\"", "\"foobar\""))
      .header("Content-Type", "application/json")
      .put("/_/discovery/nodes/localhost")
      .then()
      .statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .body(nodeDoc.replaceFirst("\"http://localhost:9230\"", "\"MayNotChangeUrl\""))
      .header("Content-Type", "application/json")
      .put("/_/discovery/nodes/localhost")
      .then()
      .statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Get it in various ways
    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes/localhost")
      .then()
      .statusCode(200)
      .body(equalTo(nodeDoc))
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes/NewName")
      .then()
      .statusCode(200)
      .body(equalTo(nodeDoc))
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes/http://localhost:9230")
      .then() // Note that get() encodes the url.
      .statusCode(200) // when testing with curl, you need use http%3A%2F%2Flocal...
      .body(equalTo(nodeDoc))
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    checkDbIsEmpty("testDiscoveryNodes done", context);
    async.complete();
  }

  // TODO - This function is way too long and confusing
  // Create smaller functions that test one thing at a time
  // Later, move them into separate files
  @Test
  public void testProxy(TestContext context) {
    async = context.async();

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;
    Response r;

    String nodeListDoc = "[ {" + LS
      + "  \"nodeId\" : \"localhost\"," + LS
      + "  \"url\" : \"http://localhost:9230\"," + LS
      + "  \"nodeName\" : \"node1\"" + LS
      + "} ]";

    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes").then().statusCode(200)
      .body(equalTo(nodeListDoc));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes/gyf").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/nodes/localhost").then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{ }").post("/_/xyz").then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/xyz' is not defined], "
      + "responseViolations=[], validationViolations=[]}",
      c.getLastReport().toString());

    final String badDoc = "{" + LS
      + "  \"instId\" : \"BAD\"," + LS // the comma here makes it bad json!
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(badDoc).post("/_/deployment/modules")
      .then().statusCode(400);

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{}").post("/_/deployment/modules")
      .then().statusCode(400);

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{\"srvcId\" : \"foo\"}").post("/_/deployment/modules")
      .then().statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docUnknownJar = "{" + LS
      + "  \"srvcId\" : \"auth-1\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-auth-module/target/okapi-unknown.jar\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docUnknownJar).post("/_/deployment/modules")
      .then()
      .statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docAuthDeployment = "{" + LS
      + "  \"srvcId\" : \"auth-1\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-auth-module/target/okapi-test-auth-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docAuthDeployment).post("/_/deployment/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationAuthDeployment = r.getHeader("Location");

    c = api.createRestAssured3();
    String docAuthDiscovery = c.given().get(locationAuthDeployment)
      .then().statusCode(200).extract().body().asString();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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
      + "      \"type\" : \"request-response\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"*\" ]," + LS
      + "    \"path\" : \"/\"," + LS
      + "    \"phase\" : \"auth\"," + LS
      + "    \"type\" : \"request-response\"," + LS
      + "    \"permissionsDesired\" : [ \"auth.extra\" ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]" + LS
      + "}";

    // Check that we fail on unknown route types
    final String docBadTypeModule
      = docAuthModule.replaceAll("request-response", "UNKNOWN-ROUTE-TYPE");
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docBadTypeModule).post("/_/proxy/modules")
      .then().statusCode(400);

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docAuthModule).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationAuthModule = r.getHeader("Location");

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docAuthModule).put(locationAuthModule + "misMatch").then().statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{ \"bad Json\" ").put(locationAuthModule).then().statusCode(400);

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docAuthModule).put(locationAuthModule).then().statusCode(200)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docAuthModule2 = "{" + LS
      + "  \"id\" : \"auth2-1\"," + LS
      + "  \"name\" : \"auth2\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"auth2\"," + LS
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
      + "    \"level\" : \"10\"," + LS
      + "    \"type\" : \"request-response\"," + LS
      + "    \"permissionsDesired\" : [ \"auth.extra\" ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]" + LS
      + "}";

    final String locationAuthModule2 = locationAuthModule.replace("auth-1", "auth2-1");
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docAuthModule2).put(locationAuthModule2)
      .then().statusCode(200)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().delete(locationAuthModule2).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docSampleDeployment = "{" + LS
      + "  \"srvcId\" : \"sample-module-1\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"," + LS
      + "    \"env\" : [ {" + LS
      + "      \"name\" : \"helloGreeting\"," + LS
      + "      \"value\" : \"hej\"" + LS
      + "    } ]" + LS
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
    locationSampleDeployment = r.getHeader("Location");

    c = api.createRestAssured3();
    String docSampleDiscovery = c.given().get(locationSampleDeployment)
      .then().statusCode(200).extract().body().asString();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docSampleModuleBadRequire = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"requires\" : [ {" + LS
      + "    \"id\" : \"SOMETHINGWEDONOTHAVE\"," + LS
      + "    \"version\" : \"1.2\"" + LS
      + "  } ]," + LS
      + "  \"routingEntries\" : [ ] " + LS
      + "}";

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModuleBadRequire).post("/_/proxy/modules").then().statusCode(400)
      .extract().response();

    final String docSampleModuleBadVersion = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ {" + LS
      + "    \"id\" : \"auth\"," + LS
      + "    \"version\" : \"9.9\"" + LS // We only have 1.2
      + "  } ]," + LS
      + "}";

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModuleBadVersion).post("/_/proxy/modules").then().statusCode(400)
      .extract().response();

    final String docSampleModule = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"requires\" : [ {" + LS
      + "    \"id\" : \"auth\"," + LS
      + "    \"version\" : \"1.2\"" + LS
      + "  } ]," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"level\" : \"30\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"modulePermissions\" : [ \"sample.modperm\" ]," + LS
      + "      \"permissionsRequired\" : [ \"sample.needed\" ]," + LS
      + "      \"permissionsDesired\" : [ \"sample.extra\" ]" + LS
      + "      } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"" + LS // TODO - Define paths - add test
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"/usr/bin/false\"" + LS
      + "  }" + LS
      + "}";
    logger.debug(docSampleModule);
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule).post("/_/proxy/modules")
      .then()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationSampleModule = r.getHeader("Location");

    // Try to delete the auth module that our sample depends on
    c.given().delete(locationAuthModule).then().statusCode(400);

    // Try to update the auth module to a lower version, would break
    // sample dependency
    final String docAuthLowerVersion = docAuthModule.replace("1.2", "1.0");
    c.given()
      .header("Content-Type", "application/json")
      .body(docAuthLowerVersion)
      .put(locationAuthModule)
      .then().statusCode(400);

    // Update the auth module to a bit higher version
    final String docAuthhigherVersion = docAuthModule.replace("1.2", "1.3");
    c.given()
      .header("Content-Type", "application/json")
      .body(docAuthhigherVersion)
      .put(locationAuthModule)
      .then().statusCode(200);

    // Create our tenant
    final String docTenantRoskilde = "{" + LS
      + "  \"id\" : \"" + okapiTenant + "\"," + LS
      + "  \"name\" : \"" + okapiTenant + "\"," + LS
      + "  \"description\" : \"Roskilde bibliotek\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docTenantRoskilde).post("/_/proxy/tenants/") // trailing slash fails
      .then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/proxy/tenants/' is not defined], "
      + "responseViolations=[], validationViolations=[]}",
      c.getLastReport().toString());

    // add tenant by using PUT (which will insert)
    final String locationTenantRoskilde = "/_/proxy/tenants/" + okapiTenant;
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docTenantRoskilde)
      .put(locationTenantRoskilde)
      .then().statusCode(200)
      .body(equalTo(docTenantRoskilde))
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Try to enable sample without the auth that it requires
    final String docEnableWithoutDep = "{" + LS
      + "  \"id\" : \"sample-module-1\"" + LS
      + "}";
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableWithoutDep).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(400);

    // try to enable a module we don't know
    final String docEnableAuthBad = "{" + LS
      + "  \"id\" : \"UnknonwModule-1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableAuthBad).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(404);

    final String docEnableAuth = "{" + LS
      + "  \"id\" : \"auth-1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableAuth).post("/_/proxy/tenants/" + okapiTenant + "/modules/")
      .then().statusCode(404);  // trailing slash is no good

    // Actually enable the auith
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableAuth).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201).body(equalTo(docEnableAuth));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/")
      .then().statusCode(404);  // trailing slash again

    // Get the list of one enabled module
    c = api.createRestAssured3();
    final String exp1 = "[ {" + LS
      + "  \"id\" : \"auth-1\"" + LS
      + "} ]";
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).body(equalTo(exp1));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // get the auth enabled record
    final String expAuthEnabled = "{" + LS
      + "  \"id\" : \"auth-1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/auth-1")
      .then().statusCode(200).body(equalTo(expAuthEnabled));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Enable with bad JSON
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("{").post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(400);

    // Enable the sample
    final String docEnableSample = "{" + LS
      + "  \"id\" : \"sample-module-1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .body(equalTo(docEnableSample));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Try to enable it again, should fail
    given()
      .header("Content-Type", "application/json")
      .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(400)
      .body(containsString("already provided"));

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/")
      .then().statusCode(404); // trailing slash

    c = api.createRestAssured3();
    final String expEnabledBoth = "[ {" + LS
      + "  \"id\" : \"auth-1\"" + LS
      + "}, {" + LS
      + "  \"id\" : \"sample-module-1\"" + LS
      + "} ]";
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).body(equalTo(expEnabledBoth));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Try to disable the auth module for the tenant.
    // Ought to fail, because it is needed by sample module
    c.given().delete("/_/proxy/tenants/" + okapiTenant + "/modules/auth-1")
      .then().statusCode(400);

    // Update the tenant
    String docTenant = "{" + LS
      + "  \"id\" : \"" + okapiTenant + "\"," + LS
      + "  \"name\" : \"Roskilde-library\"," + LS
      + "  \"description\" : \"Roskilde bibliotek\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docTenant).put("/_/proxy/tenants/" + okapiTenant)
      .then().statusCode(200)
      .body(equalTo(docTenant));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Check that both modules are still enabled
    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).body(equalTo(expEnabledBoth));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Request without any X-Okapi headers
    given()
      .get("/testb")
      .then().statusCode(403);

    // Request with a header, to unknown path
    // (note, should fail without invoking the auth module)
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/something.we.do.not.have")
      .then().statusCode(404)
      .body(equalTo("No suitable module found for path /something.we.do.not.have"));

    // Request without an auth token
    // This is acceptable, we get back a token that certifies that we have no
    // logged-in username. We can use this for modulePermissions still.
    // A real auth module would refuse the request because we do not have the
    // permission. But the test-auth lets it pass...
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-all-headers", "B") // ask sample to report all headers
      .get("/testb")
      .then()
      .statusCode(200)
      .body(containsString("X-Okapi-Token")) // auth created a token
      .body(containsString("X-Okapi-User-Id:?"));  // with no good userid


    // Failed login
    final String docWrongLogin = "{" + LS
      + "  \"tenant\" : \"t1\"," + LS
      + "  \"username\" : \"peter\"," + LS
      + "  \"password\" : \"peter-wrong-password\"" + LS
      + "}";
    given().header("Content-Type", "application/json").body(docWrongLogin)
      .header("X-Okapi-Tenant", okapiTenant).post("/authn/login")
      .then().statusCode(401);

    // Ok login, get token
    final String docLogin = "{" + LS
      + "  \"tenant\" : \"" + okapiTenant + "\"," + LS
      + "  \"username\" : \"peter\"," + LS
      + "  \"password\" : \"peter-password\"" + LS
      + "}";
    okapiToken = given().header("Content-Type", "application/json").body(docLogin)
      .header("X-Okapi-Tenant", okapiTenant).post("/authn/login")
      .then().statusCode(200).extract().header("X-Okapi-Token");

    // Actual requests to the module
    // Check the X-Okapi-Url header in, as well as URL parameters.
    // X-Okapi-Filter can not be checked here, but the log shows that it gets
    // passed to the auth filter, and not to the handler.
    // Check that the auth module has seen the right X-Okapi-Permissions-Required
    // and -Desired, it returns them in X-Auth-Permissions-Required and -Desired.
    // The X-Okapi-Permissions-Required and -Desired can not be checked here
    // directly, since Okapi sanitizes them away after invoking the auth module.
    // The auth module should return X-Okapi-Permissions to the sample module
    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-all-headers", "HBL") // ask sample to report all headers
      .get("/testb?query=foo&limit=10")
      .then().statusCode(200)
      .log().ifValidationFails()
      .header("X-Okapi-Url", "http://localhost:9230") // no trailing slash!
      .header("X-Okapi-User-Id", "peter")
      .header("X-Url-Params", "query=foo&limit=10")
      .header("X-Okapi-Permissions", containsString("sample.extra"))
      .header("X-Okapi-Permissions", containsString("auth.extra"))
      .header("X-Auth-Permissions-Desired", containsString("auth.extra"))
      .header("X-Auth-Permissions-Desired", containsString("sample.extra"))
      .header("X-Auth-Permissions-Required", "sample.needed")
      .body(containsString("It works"));

    // Check the CORS headers.
    // The presence of the Origin header should provoke the two extra headers.
    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Origin", "http://foobar.com")
      .get("/testb")
      .then().statusCode(200)
      .header("Access-Control-Allow-Origin", "*")
      .header("Access-Control-Expose-Headers",
        "Location,X-Okapi-Trace,X-Okapi-Token,Authorization,X-Okapi-Request-Id")
      .body(equalTo("It works"));

    // Post request.
    // Test also URL parameters.
    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "text/xml")
      .header("X-all-headers", "H") // ask sample to report all headers
      .body("Okapi").post("/testb?query=foo")
      .then().statusCode(200)
      .header("X-Url-Params", "query=foo")
      .body(equalTo("hej  (XML) Okapi"));

    // Verify that the path matching is case sensitive
    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .get("/TESTB")
      .then().statusCode(404);

    // See that a delete fails - we only match auth, which is a filter
    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .delete("/testb")
      .then().statusCode(404);

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

    // Check that we called the tenant init
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-tenant-reqs", "yes")
      .get("/testb")
      .then()
      .statusCode(200) // No longer expects a DELETE. See Okapi-252
      .body(equalTo("It works Tenant requests: POST-roskilde-auth "))
      .log().ifValidationFails();

    // Check that we refuse unknown paths, even with auth module
    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .get("/something.we.do.not.have")
      .then().statusCode(404);

    // Check that we accept Authorization: Bearer <token> instead of X-Okapi-Token,
    // and that we can extract the tenant from it.
    given()
      .header("X-all-headers", "H") // ask sample to report all headers
      .header("Authorization", "Bearer " + okapiToken)
      .get("/testb")
      .then().log().ifValidationFails()
      .header("X-Okapi-Tenant", okapiTenant)
      .statusCode(200);
    // Note that we can not check the token, the module sees a different token,
    // created by the auth module, when it saw a ModulePermission for the sample
    // module. This is all right, since we explicitly ask sample to pass its
    // request headers into its response. See Okapi-266.

    // Check that we fail on conflicting X-Okapi-Token and Auth tokens
    given().header("X-all-headers", "H") // ask sample to report all headers
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Authorization", "Bearer " + okapiToken + "WRONG")
      .get("/testb")
      .then().log().ifValidationFails()
      .statusCode(400);

    // 2nd sample module. We only create it in discovery and give it same URL as
    // for sample-module (first one). Then we delete it again.
    c = api.createRestAssured3();
    final String docSample2Deployment = "{" + LS
      + "  \"instId\" : \"sample2-inst\"," + LS
      + "  \"srvcId\" : \"sample-module2-1\"," + LS
      // + "  \"nodeId\" : null," + LS // no nodeId, we aren't deploying on any node
      + "  \"url\" : \"http://localhost:9232\"" + LS
      + "}";
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSample2Deployment).post("/_/discovery/modules")
      .then()
      .statusCode(201).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationSample2Discovery = r.header("Location");

    // Get the sample-2
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/sample-module2-1")
      .then().statusCode(200)
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // and its instance
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/sample-module2-1/sample2-inst")
      .then().statusCode(200)
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // health check
    c = api.createRestAssured3();
    c.given().get("/_/discovery/health")
      .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // health for sample2
    c = api.createRestAssured3();
    c.given().get("/_/discovery/health/sample-module2-1")
      .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // health for an instance
    c = api.createRestAssured3();
    c.given().get("/_/discovery/health/sample-module2-1/sample2-inst")
      .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Declare sample2
    final String docSample2Module = "{" + LS
      + "  \"id\" : \"sample-module2-1\"," + LS
      + "  \"name\" : \"another-sample-module2\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"level\" : \"31\"," + LS
      + "    \"type\" : \"request-response\"" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSample2Module).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationSample2Module = r.getHeader("Location");

    // enable sample2
    final String docEnableSample2 = "{" + LS
      + "  \"id\" : \"sample-module2-1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableSample2).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .body(equalTo(docEnableSample2));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // disable it, and re-enable.
    // Later we will check that we got the right calls in its
    // tenant interface.
    given()
      .delete("/_/proxy/tenants/" + okapiTenant + "/modules/sample-module2-1")
      .then().statusCode(204);
    given()
      .header("Content-Type", "application/json")
      .body(docEnableSample2).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .body(equalTo(docEnableSample2));

    // 3rd sample module. We only create it in discovery and give it same URL as
    // for sample-module (first one), just like sample2 above.
    c = api.createRestAssured3();
    final String docSample3Deployment = "{" + LS
      + "  \"instId\" : \"sample3-instance\"," + LS
      + "  \"srvcId\" : \"sample-module3-1\"," + LS
      + "  \"url\" : \"http://localhost:9232\"" + LS
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
      + "  \"id\" : \"sample-module3-1\"," + LS
      + "  \"name\" : \"sample-module3\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
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
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSample3Module).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationSample3Module = r.getHeader("Location");

    final String docEnableSample3 = "{" + LS
      + "  \"id\" : \"sample-module3-1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableSample3).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .header("Location", equalTo("/_/proxy/tenants/" + okapiTenant + "/modules/sample-module3-1"))
      .log().ifValidationFails()
      .body(equalTo(docEnableSample3));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/tenants/" + "unknown" + "/modules")
      .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/tenants/" + "unknown" + "/modules/unknown")
      .then().statusCode(404);
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
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(equalTo("hej hej OkapiX"));

    // Verify that we have seen tenant requests to POST but not DELETE
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-tenant-reqs", "yes")
      .get("/testb")
      .then()
      .statusCode(200) // No longer expects a DELETE. See Okapi-252
      .body(containsString("POST-roskilde-auth POST-roskilde-auth"))
      .log().ifValidationFails();

    // Check that the X-Okapi-Stop trick works. Sample will set it if it sees
    // a X-Stop-Here header.
    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-Stop-Here", "Enough!")
      .body("OkapiX").post("/testb")
      .then().statusCode(200)
      .header("X-Okapi-Stop", "Enough!")
      .body(equalTo("hej OkapiX")); // only one "Hello"

    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "text/xml")
      .get("/testb")
      .then().statusCode(200).body(equalTo("It works (XML) "));

    c = api.createRestAssured3();
    final String exp4Modules = "[ {" + LS
      + "  \"id\" : \"auth-1\"" + LS
      + "}, {" + LS
      + "  \"id\" : \"sample-module-1\"" + LS
      + "}, {" + LS
      + "  \"id\" : \"sample-module2-1\"" + LS
      + "}, {" + LS
      + "  \"id\" : \"sample-module3-1\"" + LS
      + "} ]";
    c.given().get(locationTenantRoskilde + "/modules")
      .then().statusCode(200)
      .body(equalTo(exp4Modules));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().delete(locationTenantRoskilde + "/modules/sample-module3-1")
      .then().statusCode(204);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    final String exp3Modules = "[ {" + LS
      + "  \"id\" : \"auth-1\"" + LS
      + "}, {" + LS
      + "  \"id\" : \"sample-module-1\"" + LS
      + "}, {" + LS
      + "  \"id\" : \"sample-module2-1\"" + LS
      + "} ]";
    c.given().get(locationTenantRoskilde + "/modules")
      .then().statusCode(200)
      .body(equalTo(exp3Modules));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules")
      .then().statusCode(200)
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // make sample 2 disappear from discovery!
    c = api.createRestAssured3();
    c.given().delete(locationSample2Discovery)
      .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules")
      .then().statusCode(200)
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "text/xml")
      .get("/testb")
      .then().statusCode(404); // because sample2 was removed

    // Disable the sample module. No tenant-destroy for sample
    given()
      .delete("/_/proxy/tenants/" + okapiTenant + "/modules/sample-module-1")
      .then().statusCode(204);

    // Disable the sample2 module. It has a tenant request handler which is
    // no longer invoked, so it does not matter we don't have a running instance
    given()
      .delete("/_/proxy/tenants/" + okapiTenant + "/modules/sample-module2-1")
      .then().statusCode(204);

    c = api.createRestAssured3();
    c.given().delete(locationTenantRoskilde)
      .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Clean up, so the next test starts with a clean slate
    logger.debug("testproxy cleaning up");
    given().delete(locationSample3Inst).then().log().ifValidationFails().statusCode(204);
    given().delete(locationSample3Module).then().log().ifValidationFails().statusCode(204);
    given().delete("/_/proxy/modules/sample-module-1").then().log().ifValidationFails().statusCode(204);
    given().delete("/_/proxy/modules/sample-module2-1").then().log().ifValidationFails().statusCode(204);
    given().delete("/_/proxy/modules/auth-1").then().log().ifValidationFails().statusCode(204);
    given().delete(locationAuthDeployment).then().log().ifValidationFails().statusCode(204);
    locationAuthDeployment = null;
    given().delete(locationSampleDeployment).then().log().ifValidationFails().statusCode(204);
    locationSampleDeployment = null;

    checkDbIsEmpty("testproxy done", context);

    async.complete();
  }

  @Test
  public void testDeployment(TestContext context) {
    async = context.async();
    Response r;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;

    c = api.createRestAssured3();
    c.given().get("/_/deployment/modules")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/deployment/modules/not_found")
      .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/not_found")
      .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String doc1 = "{" + LS
      + "  \"instId\" : \"localhost-9231\"," + LS // set so we can compare with result
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

    // with descriptor, but missing nodeId
    final String doc1a = "{" + LS
      + "  \"instId\" : \"localhost-9231\"," + LS
      + "  \"srvcId\" : \"sample-module5\"," + LS
      + "  \"descriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(doc1a).post("/_/discovery/modules")
      .then().statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // unknown nodeId
    final String doc1b = "{" + LS
      + "  \"instId\" : \"localhost-9231\"," + LS
      + "  \"srvcId\" : \"sample-module5\"," + LS
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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String doc2 = "{" + LS
      + "  \"instId\" : \"localhost-9231\"," + LS
      + "  \"srvcId\" : \"sample-module5\"," + LS
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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get(locationSampleDeployment).then().statusCode(200)
      .body(equalTo(doc2));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/deployment/modules")
      .then().statusCode(200)
      .body(equalTo("[ " + doc2 + " ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(doc2).post("/_/discovery/modules")
      .then().statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/sample-module5")
      .then().statusCode(200)
      .body(equalTo("[ " + doc2 + " ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules")
      .then().statusCode(200)
      .log().ifValidationFails()
      .body(equalTo("[ " + doc2 + " ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    if ("inmemory".equals(conf.getString("storage"))) {
      testDeployment2(async, context);
    } else {
      // just undeploy but keep it registered in discovery
      logger.info("doc2 " + doc2);
      JsonObject o2 = new JsonObject(doc2);
      String instId = o2.getString("instId");
      String loc = "http://localhost:9230/_/deployment/modules/" + instId;
      c = api.createRestAssured3();
      c.given().delete(loc).then().statusCode(204);
      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

      undeployFirst(x -> {
        conf.remove("mongo_db_init");
        conf.remove("postgres_db_init");

        DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
        vertx.deployVerticle(MainVerticle.class.getName(), opt, res -> {
          waitDeployment2();
        });
      });
      waitDeployment2(async, context);
    }
  }

  synchronized private void waitDeployment2() {
    this.notify();
  }

  synchronized private void waitDeployment2(Async async, TestContext context) {
    try {
      this.wait();
    } catch (Exception e) {
      context.asyncAssertFailure();
      async.complete();
      return;
    }
    testDeployment2(async, context);
  }

  private void testDeployment2(Async async, TestContext context) {
    logger.info("testDeployment2");
    Response r;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;
    c = api.createRestAssured3();
    c.given().delete(locationSampleDeployment).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().delete(locationSampleDeployment).then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationSampleDeployment = null;

    // Verify that the list works also after delete
    c = api.createRestAssured3();
    c.given().get("/_/deployment/modules")
      .then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // verify that module5 is no longer there
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/sample-module5")
      .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // verify that a never-seen module returns the same
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/UNKNOWN-MODULE")
      .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Deploy a module via its own LaunchDescriptor
    final String docSampleModule = "{" + LS
      + "  \"id\" : \"sample-module-depl-1\"," + LS
      + "  \"name\" : \"sample module for deployment test\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"level\" : \"30\"," + LS
      + "      \"type\" : \"request-response\"" + LS
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
      .body(docSampleModule).post("/_/proxy/modules")
      .then()
      //.log().all()
      .statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationSampleModule = r.getHeader("Location");

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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationSampleDeployment = r.getHeader("Location");

    // Would be nice to verify that the module works, but too much hassle with
    // tenants etc.
    // Undeploy.
    c = api.createRestAssured3();
    c.given().delete(locationSampleDeployment).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    // Undeploy again, to see it is gone
    c = api.createRestAssured3();
    c.given().delete(locationSampleDeployment).then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationSampleDeployment = null;

    // and delete from the proxy
    c = api.createRestAssured3();
    c.given().delete(locationSampleModule)
      .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    checkDbIsEmpty("testDeployment done", context);

    async.complete();
  }

  @Test
  public void testNotFound(TestContext context) {
    async = context.async();

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
    for (String type : Arrays.asList("request-response", "request-only", "headers")) {

      final String docSampleModule = "{" + LS
        + "  \"id\" : \"sample-module-1\"," + LS
        + "  \"filters\" : [ {" + LS
        + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
        + "    \"path\" : \"/test2\"," + LS
        + "    \"level\" : \"20\"," + LS
        + "    \"type\" : \"" + type + "\"" + LS
        + "  } ]" + LS
        + "}";
      r = given()
        .header("Content-Type", "application/json")
        .body(docSampleModule).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();
      final String locationSampleModule = r.getHeader("Location");

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
    }
    given().delete(locationSampleDeployment).then().statusCode(204);
    locationSampleDeployment = null;
    given().delete(locationTenantRoskilde)
      .then().statusCode(204);

    async.complete();
  }

  @Test
  public void testHeader(TestContext context) {
    async = context.async();

    Response r;
    ValidatableResponse then;

    final String docLaunch1 = "{" + LS
      + "  \"srvcId\" : \"sample-module-5\"," + LS
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
      + "  \"srvcId\" : \"header-module-1\"," + LS
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
      + "  \"id\" : \"sample-module-5\"," + LS
      + "  \"filters\" : [ {" + LS
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
      + "  \"id\" : \"header-module-1\"," + LS
      + "  \"filters\" : [ {" + LS
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
      + "  \"id\" : \"sample-module-5\"" + LS
      + "}";
    given()
      .header("Content-Type", "application/json")
      .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .body(equalTo(docEnableSample));

    final String docEnableHeader = "{" + LS
      + "  \"id\" : \"header-module-1\"" + LS
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

    given().delete("/_/proxy/tenants/" + okapiTenant + "/modules/sample-module-5")
      .then().statusCode(204);

    given().delete(locationSampleModule)
      .then().statusCode(204);

    final String docSampleModule2 = "{" + LS
      + "  \"id\" : \"sample-module-5\"," + LS
      + "  \"filters\" : [ {" + LS
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

    async.complete();
  }

  @Test
  public void testUiModule(TestContext context) {
    async = context.async();
    Response r;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    String location = r.getHeader("Location");

    c = api.createRestAssured3();
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

  /*
   * Test redirect types. Sets up two modules, our sample, and the header test
   * module.
   *
   * Both modules support the /testb path.
   * Test also supports /testr path.
   * Header will redirect /red path to /testr, which will end up in the test module.
   * Header will also attempt to support /loop, /loop1, and /loop2 for testing
   * looping redirects. These are expected to fail.
   *
   */
  @Test
  public void testRedirect(TestContext context) {
    logger.info("Redirect test starting");
    async = context.async();
    RestAssuredClient c;
    Response r;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml")
      .load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

    // Set up a tenant to test with
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
      .log().ifValidationFails()
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationTenantRoskilde = r.getHeader("Location");

    // Set up, deploy, and enable a sample module
    final String docSampleModule = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"level\" : \"50\"," + LS
      + "    \"type\" : \"request-response\"" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testr\"," + LS
      + "    \"level\" : \"59\"," + LS
      + "    \"type\" : \"request-response\"," + LS
      + "    \"permissionsDesired\" : [ \"sample.testr\" ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/loop2\"," + LS
      + "    \"level\" : \"52\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/loop1\"" + LS
      + "  }, {" + LS
      + "    \"modulePermissions\" : [ \"sample.modperm\" ]," + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/chain3\"," + LS
      + "    \"level\" : \"53\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/testr\"," + LS
      + "    \"permissionsDesired\" : [ \"sample.chain3\" ]" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSampleModule).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    final String locationSampleModule = r.getHeader("Location");

    final String docSampleDeploy = "{" + LS
      + "  \"srvcId\" : \"sample-module-1\"," + LS
      + "  \"nodeId\" : \"localhost\"" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given().header("Content-Type", "application/json")
      .body(docSampleDeploy).post("/_/discovery/modules")
      .then().statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationSampleDeployment = r.getHeader("Location");

    final String docEnableSample = "{" + LS
      + "  \"id\" : \"sample-module-1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201).body(equalTo(docEnableSample));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/testr")
      .then().statusCode(200)
      .body(containsString("It works"))
      .log().ifValidationFails();

    // Set up, deploy, and enable the header module
    final String docHeaderModule = "{" + LS
      + "  \"id\" : \"header-module-1\"," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"level\" : \"20\"," + LS
      + "    \"type\" : \"request-response\"" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/red\"," + LS
      + "    \"level\" : \"21\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/testr\"" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/badredirect\"," + LS
      + "    \"level\" : \"22\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/nonexisting\"" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/simpleloop\"," + LS
      + "    \"level\" : \"23\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/simpleloop\"" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/loop1\"," + LS
      + "    \"level\" : \"24\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/loop2\"" + LS
      + "  }, {" + LS
      + "    \"modulePermissions\" : [ \"hdr.modperm\" ]," + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/chain1\"," + LS
      + "    \"level\" : \"25\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/chain2\"," + LS
      + "    \"permissionsDesired\" : [ \"hdr.chain1\" ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/chain2\"," + LS
      + "    \"level\" : \"26\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/chain3\"," + LS
      + "    \"permissionsDesired\" : [ \"hdr.chain2\" ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"POST\" ]," + LS
      + "    \"path\" : \"/multiple\"," + LS
      + "    \"level\" : \"27\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/testr\"" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"POST\" ]," + LS
      + "    \"path\" : \"/multiple\"," + LS
      + "    \"level\" : \"28\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/testr\"" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : \"java -Dport=%p -jar ../okapi-test-header-module/target/okapi-test-header-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docHeaderModule).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    final String locationHeaderModule = r.getHeader("Location");

    final String docHeaderDeploy = "{" + LS
      + "  \"srvcId\" : \"header-module-1\"," + LS
      + "  \"nodeId\" : \"localhost\"" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given().header("Content-Type", "application/json")
      .body(docHeaderDeploy).post("/_/discovery/modules")
      .then().statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    locationHeaderDeployment = r.getHeader("Location");

    final String docEnableHeader = "{" + LS
      + "  \"id\" : \"header-module-1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableHeader).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201).body(equalTo(docEnableHeader));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/testb")
      .then().statusCode(200)
      .body(containsString("It works"))
      .log().ifValidationFails();

    // Actual redirecting request
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/red")
      .then().statusCode(200)
      .body(containsString("It works"))
      .header("X-Okapi-Trace", containsString("GET sample-module-1 http://localhost:9231/testr"))
      .log().ifValidationFails();

    // Bad redirect
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/badredirect")
      .then().statusCode(500)
      .body(equalTo("Redirecting /badredirect to /nonexisting FAILED. No suitable module found"))
      .log().ifValidationFails();

    // catch redirect loops
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/simpleloop")
      .then().statusCode(500)
      .body(containsString("loop:"))
      .log().ifValidationFails();

    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/loop1")
      .then().statusCode(500)
      .body(containsString("loop:"))
      .log().ifValidationFails();

    // redirect to multiple modules
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("Content-Type", "application/json")
      .body("{}")
      .post("/multiple")
      .then().statusCode(200)
      .body(containsString("Hello Hello")) // test-module run twice
      .log().ifValidationFails();

    // Redirect with parameters
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/red?foo=bar")
      .then().statusCode(200)
      .body(containsString("It works"))
      .log().ifValidationFails();

    // A longer chain of redirects
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-all-headers", "B")
      .get("/chain1")
      .then().statusCode(200)
      .body(containsString("It works"))
      // No auth header should be included any more, since we don't have an auth filter
      .log().ifValidationFails();

    // What happens on prefix match
    // /red matches, replaces with /testr, getting /testrlight which is not found
    // This is odd, and subotimal, but not a serious failure. okapi-253
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/redlight")
      .then().statusCode(404)
      .header("X-Okapi-Trace", containsString("sample-module-1 http://localhost:9231/testrlight : 404"))
      .log().ifValidationFails();

    // Verify that we replace only the beginning of the path
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/red/blue/red?color=/red")
      .then().statusCode(404)
      .log().ifValidationFails();

    // Clean up
    logger.info("Redirect test done. Cleaning up");
    given().delete(locationTenantRoskilde)
      .then().statusCode(204);
    given().delete(locationSampleModule)
      .then().statusCode(204);
    given().delete(locationSampleDeployment)
      .then().statusCode(204);
    locationSampleDeployment = null;
    given().delete(locationHeaderModule)
      .then().statusCode(204);
    given().delete(locationHeaderDeployment)
      .then().statusCode(204);
    locationHeaderDeployment = null;

    checkDbIsEmpty("testRedirect done", context);

    async.complete();

  }
  @Test
  public void testMultipleInterface(TestContext context) {
    logger.info("Redirect test starting");
    async = context.async();
    RestAssuredClient c;
    Response r;

    Assert.assertNull("locationSampleDeployment", locationSampleDeployment);
    Assert.assertNull("locationHeaderDeployment", locationHeaderDeployment);

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

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
      + "      \"path\" : \"/testb\"" + LS
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
      .log().ifValidationFails()
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
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
      + "      \"path\" : \"/testb\"" + LS
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
      .log().ifValidationFails()
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationSampleModule2 = r.getHeader("Location");

    final String locTenant = createTenant();
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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces?full=true")
            .then().statusCode(200)
            .body(equalTo("[ {" + LS
                    + "  \"id\" : \"sample\"," + LS
                    + "  \"version\" : \"1.0\"," + LS
                    + "  \"interfaceType\" : \"proxy\"," + LS
                    + "  \"handlers\" : [ {" + LS
                    + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
                    + "    \"path\" : \"/testb\"" + LS
                    + "  } ]" + LS
                    + "} ]"))
            .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces?full=false")
            .then().statusCode(200)
            .body(equalTo("[ {" + LS
                    + "  \"id\" : \"sample\"," + LS
                    + "  \"version\" : \"1.0\"" + LS
                    + "} ]"))
            .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces?full=false&type=proxy")
            .then().statusCode(200)
            .body(equalTo("[ {" + LS
                    + "  \"id\" : \"sample\"," + LS
                    + "  \"version\" : \"1.0\"" + LS
                    + "} ]"))
            .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces?full=false&type=system")
            .then().statusCode(200)
            .body(equalTo("[ ]"))
            .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces/sample")
            .then().statusCode(200)
            .body(equalTo("[ {" + LS + "  \"id\" : \"sample-module-1\"" + LS + "} ]"))
            .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces/sample?type=proxy")
            .then().statusCode(200)
            .body(equalTo("[ {" + LS + "  \"id\" : \"sample-module-1\"" + LS + "} ]"))
            .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());


    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + "foo" + "/interfaces/sample")
      .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces/bar")
      .then().statusCode(200).body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    r = c.given().delete(locEnable1)
      .then().statusCode(204).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    r = c.given().delete(locationSampleModule1)
      .then().statusCode(204).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    r = c.given().delete(locationSampleModule2)
      .then().statusCode(204).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docSampleModule3 = "{" + LS
      + "  \"id\" : \"sample-module-3\"," + LS
      + "  \"name\" : \"sample module 3\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"interfaceType\" : \"multiple\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"" + LS
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
      .log().ifValidationFails()
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
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
      + "      \"path\" : \"/testb\"" + LS
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
      .log().ifValidationFails()
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationSampleModule4 = r.getHeader("Location");

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/interfaces/sample")
      .then().statusCode(200).body(equalTo("[ ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
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
      .log().ifValidationFails()
      .statusCode(200)
      .body(equalTo("Hello OkapiX"));

    given().header("X-Okapi-Module-Id", "sample-module-4")
      .header("X-Okapi-Tenant", okapiTenant)
      .body("OkapiX").post("/testb")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(equalTo("hej OkapiX"));

    // cleanup
    c = api.createRestAssured3();
    r = c.given().delete(locEnable3)
      .then().statusCode(204).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    c = api.createRestAssured3();
    r = c.given().delete(locEnable4)
      .then().statusCode(204).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    c = api.createRestAssured3();
    r = c.given().delete(locationSampleModule3)
      .then().statusCode(204).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    c = api.createRestAssured3();
    r = c.given().delete(locationSampleModule4)
      .then().statusCode(204).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    r = c.given().delete(locationSampleDeployment)
      .then().statusCode(204).extract().response();
    locationSampleDeployment = null;
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    c = api.createRestAssured3();
    r = c.given().delete(locationHeaderDeployment)
      .then().statusCode(204).extract().response();
    locationHeaderDeployment = null;
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    async.complete();
  }

  @Test
  public void testVersion(TestContext context) {
    logger.info("testVersion starting");
    async = context.async();
    RestAssuredClient c;
    Response r;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

    c = api.createRestAssured3();
    r = c.given().get("/_/version").then().statusCode(200).log().ifValidationFails().extract().response();

    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    async.complete();
  }

  @Test
  public void testSemVer(TestContext context) {
    async = context.async();

    RestAssuredClient c;
    Response r;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

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
      .log().ifValidationFails()
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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
      .log().ifValidationFails()
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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
      .log().ifValidationFails()
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    async.complete();
  }

  @Test
  public void testManyModules(TestContext context) {
    async = context.async();

    RestAssuredClient c;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");
    Response r;

    int i;
    for (i = 0; i < 10; i++) {
      String docSampleModule = "{" + LS
        + "  \"id\" : \"sample-1.2." + Integer.toString(i) + "\"," + LS
        + "  \"name\" : \"sample module " + Integer.toString(i) + "\"," + LS
        + "  \"requires\" : [ ]" + LS
        + "}";
      c = api.createRestAssured3();
      c.given()
        .header("Content-Type", "application/json")
        .body(docSampleModule)
        .post("/_/proxy/modules")
        .then()
        .statusCode(201)
        .log().ifValidationFails();
      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    }
    c = api.createRestAssured3();
    r = c.given()
      .get("/_/proxy/modules")
      .then()
      .statusCode(200).log().ifValidationFails().extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());

    async.complete();
  }

  private void undeployFirst(Handler<AsyncResult<Void>> fut) {
    Set<String> ids = vertx.deploymentIDs();
    Iterator<String> it = ids.iterator();
    if (it.hasNext()) {
      vertx.undeploy(it.next(), fut);
    } else {
      fut.handle(Future.succeededFuture());
    }
  }

  @Test
  public void testInternalModule(TestContext context) {
    logger.info("testInternalModule 1");
    async = context.async();
    undeployFirst(x -> {
      conf.remove("mongo_db_init");
      conf.remove("postgres_db_init");

      logger.info("testInternalModule 2");
      DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
      vertx.deployVerticle(MainVerticle.class.getName(), opt, res -> {
        logger.info("testInternalModule 3");
        testInternalModule2();
      });
    });
  }

  private void testInternalModule2() {
    undeployFirst(x -> {
      conf.put("okapiVersion", "3.0.0");
      DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
      vertx.deployVerticle(MainVerticle.class.getName(), opt, res -> {
        logger.info("testInternalModule 4");
        conf.remove("okapiVersion");
        async.complete();
      });
    });
  }

}
