package org.folio.okapi;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.response.Response;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import static org.hamcrest.Matchers.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class PullTest {

  private final Logger logger = OkapiLogger.get();

  private Vertx vertx;

  private static final String LS = System.lineSeparator();
  private final int port1 = 9231; // where we define MDs
  private final int port2 = 9230; // where we pull
  private final int port3 = 9232; // other non-proxy server
  private final int port4 = 9233; // non-existing server!

  private static RamlDefinition api;

  @BeforeClass
  public static void setUpBeforeClass() {
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
  }

  @Before
  public void setUp(TestContext context) {
    logger.debug("staring PullTest");
    vertx = Vertx.vertx();
    DeploymentOptions opt1 = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", Integer.toString(port1))
      );
    DeploymentOptions opt2 = new DeploymentOptions()
        .setConfig(new JsonObject().put("port", Integer.toString(port2))
        );
    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);

    vertx.deployVerticle(MainVerticle.class, opt1)
      .compose(x -> vertx.deployVerticle(MainVerticle.class, opt2))
      .compose(x -> vertx.createHttpServer(so)
          .requestHandler(Router.router(vertx))
          .listen(port3))
      .onComplete(context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void test1() {
    RestAssuredClient c;

    c = api.createRestAssured3();
    c.given().port(port1).get("/_/version").then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2).get("/_/version").then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .body("{ bad json").post("/_/proxy/pull/modules")
      .then().statusCode(400).log().ifValidationFails();

  }

  @Test
  public void test2() {
    RestAssuredClient c;

    final String pullDoc = "{" + LS
      + "\"urls\" : [" + LS
      + "  \"http://localhost:" + port1 + "\"" + LS
      + "  ]" + LS
      + "}";

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .body(pullDoc).post("/_/proxy/pull/modules")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docBriefModuleA = "{" + LS
      + "  \"id\" : \"module-a-1.0.0\"," + LS
      + "  \"name\" : \"A\"" + LS
      + "}";

    final String docModuleA = "{" + LS
      + "  \"id\" : \"module-a-1.0.0\"," + LS
      + "  \"name\" : \"A\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"int-a\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().port(port1)
      .header("Content-Type", "application/json")
      .body(docModuleA).post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // list of the one module that's always there.
    final String internalModuleDoc = "[ {" + LS
      + "  \"id\" : \"okapi-0.0.0\"," + LS
      + "  \"name\" : \"Okapi\"" + LS
      + "} ]";

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo(internalModuleDoc));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    Response r;
    c = api.createRestAssured3();
    r = c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?full=true")
      .then().statusCode(200).log().ifValidationFails()
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    JsonArray a = new JsonArray(r.body().asString());
    Assert.assertEquals(1, a.size());
    JsonObject j = a.getJsonObject(0);
    Assert.assertTrue(j.containsKey("provides"));
    Assert.assertTrue(j.containsKey("permissionSets"));

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .body(pullDoc).post("/_/proxy/pull/modules")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ " + docBriefModuleA + " ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docModuleB = "{" + LS
      + "  \"id\" : \"module-b-1.0.0\"," + LS
      + "  \"name\" : \"B\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"int-b\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ {" + LS
      + "    \"id\" : \"int-a\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().port(port1)
      .header("Content-Type", "application/json")
      .body(docModuleB).post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docModuleC = "{" + LS
      + "  \"id\" : \"module-c-1.0.10000\"," + LS
      + "  \"name\" : \"C\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"int-c\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ {" + LS
      + "    \"id\" : \"int-b\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().port(port1)
      .header("Content-Type", "application/json")
      .body(docModuleC).post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // should get b and c
    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .body(pullDoc).post("/_/proxy/pull/modules")
      .then().statusCode(200).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .body(pullDoc).post("/_/proxy/pull/modules")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?orderBy=id&order=desc&preRelease=true&npmSnapshot=true&latest=2")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"okapi-0.0.0\"," + LS
        + "  \"name\" : \"Okapi\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"module-c-1.0.10000\"," + LS
        + "  \"name\" : \"C\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"module-b-1.0.0\"," + LS
        + "  \"name\" : \"B\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"module-a-1.0.0\"," + LS
        + "  \"name\" : \"A\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?orderBy=id&order=desc&preRelease=true&npmSnapshot=false")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"okapi-0.0.0\"," + LS
        + "  \"name\" : \"Okapi\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"module-b-1.0.0\"," + LS
        + "  \"name\" : \"B\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"module-a-1.0.0\"," + LS
        + "  \"name\" : \"A\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?dot=true")
      .then().statusCode(200).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?orderBy=id&order=desc&preRelease=true&require=int-b")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"module-c-1.0.10000\"," + LS
        + "  \"name\" : \"C\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?orderBy=id&order=desc&preRelease=true&provide=int-b=1.0")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"module-b-1.0.0\"," + LS
        + "  \"name\" : \"B\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?orderBy=id&order=desc&preRelease=true&provide=int-b=1.1")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?orderBy=id&order=desc&preRelease=true&provide=int-C")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?orderBy=id&order=foo&preRelease=true").then().statusCode(400);

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?orderBy=id&order=asc").then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"module-a-1.0.0\"," + LS
        + "  \"name\" : \"A\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"module-b-1.0.0\"," + LS
        + "  \"name\" : \"B\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"module-c-1.0.10000\"," + LS
        + "  \"name\" : \"C\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"okapi-0.0.0\"," + LS
        + "  \"name\" : \"Okapi\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?orderBy=bogus").then().statusCode(400);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // no RAML check below because preRelease value is invalid
    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?preRelease=sandt").then().statusCode(400);

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?filter=module-c&orderBy=id").then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"module-c-1.0.10000\"," + LS
        + "  \"name\" : \"C\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?filter=module-c-1").then().statusCode(200)
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"module-c-1.0.10000\"," + LS
        + "  \"name\" : \"C\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?filter=module-2").then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?filter=module").then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .get("/_/proxy/modules?filter=foo").then().statusCode(200)
      .body(equalTo("[ ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

  }

  @Test
  public void test3() {
    RestAssuredClient c;

    // pull from dummy server
    final String pullPort3 = "{" + LS
        + "\"urls\" : [" + LS
        + "  \"http://localhost:" + port3 + "\"" + LS
        + "  ]" + LS
        + "}";
    c = api.createRestAssured3();
    c.given().port(port2)
        .header("Content-Type", "application/json")
        .body(pullPort3).post("/_/proxy/pull/modules").then()
        .statusCode(404).log().ifValidationFails();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // pull from non-existing server
    final String pullPort4 = "{" + LS
        + "\"urls\" : [" + LS
        + "  \"http://localhost:" + port4 + "\"" + LS
        + "  ]" + LS
        + "}";
    c = api.createRestAssured3();
    c.given().port(port2)
        .header("Content-Type", "application/json")
        .body(pullPort4).post("/_/proxy/pull/modules").then()
        .statusCode(404).log().ifValidationFails();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

  }

  @Test
  public void testNonExistingDummy() {
    RestAssuredClient c;

    // first non-existing, then dummy
    final String doc = "{" + LS
        + "\"urls\" : [" + LS
        + "  \"http://localhost:" + port3 + "\"," + LS
        + "  \"http://localhost:" + port4 + "\"" + LS
        + "  ]" + LS
        + "}";

    // pull from from both
    c = api.createRestAssured3();
    c.given().port(port2)
        .header("Content-Type", "application/json")
        .body(doc).post("/_/proxy/pull/modules").then().statusCode(404).log().ifValidationFails();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

  }

  @Test
  public void testNonExisting() {
    RestAssuredClient c;

    // non-existing server
    final String doc = "{" + LS
        + "\"urls\" : [" + LS
        + "  \"http://localhost:" + port4 + "\"" + LS
        + "  ]" + LS
        + "}";

    // pull from from both
    c = api.createRestAssured3();
    c.given().port(port2)
        .header("Content-Type", "application/json")
        .body(doc).post("/_/proxy/pull/modules").then().statusCode(404).log().ifValidationFails();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }

  @Test
  public void testDummy() {
    RestAssuredClient c;

    final String doc = "{" + LS
        + "\"urls\" : [" + LS
        + "  \"http://localhost:" + port3 + "\"" + LS
        + "  ]" + LS
        + "}";

    // pull from from both
    c = api.createRestAssured3();
    c.given().port(port2)
        .header("Content-Type", "application/json")
        .body(doc).post("/_/proxy/pull/modules").then().statusCode(404).log().ifValidationFails();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }

  @Test
  public void testDummyNonExisting() {
    RestAssuredClient c;

    // first dummy, then non-existing
    final String doc = "{" + LS
        + "\"urls\" : [" + LS
        + "  \"http://localhost:" + port4 + "\"," + LS
        + "  \"http://localhost:" + port3 + "\"" + LS
        + "  ]" + LS
        + "}";

    // pull from from both
    c = api.createRestAssured3();
    c.given().port(port2)
      .header("Content-Type", "application/json")
      .body(doc).post("/_/proxy/pull/modules").then().statusCode(404).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }

}
