package org.folio.okapi;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import static io.restassured.RestAssured.given;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.core.logging.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.okapi.common.HttpResponse;
import static org.folio.okapi.common.XOkapiHeaders.HANDLER_RESULT;
import static org.hamcrest.Matchers.containsString;
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
  private final int portPre = 9236;
  private final int portPost = 9237;
  private final int portEdge = 9238;
  private final int port = 9230;
  private Buffer preBuffer;
  private Buffer postBuffer;
  private MultiMap postHandlerHeaders;
  private static RamlDefinition api;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
  }

  private void myPreHandle(RoutingContext ctx) {
    logger.info("myPreHandle!");
    preBuffer = Buffer.buffer();
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> HttpResponse.responseText(ctx, 204).end());
    } else {
      ctx.response().setStatusCode(200);
      ctx.request().handler(preBuffer::appendBuffer);
      ctx.request().endHandler(res -> {
        logger.info("myPreHandle end=" + preBuffer.toString());
        ctx.response().end();
      });
    }
  }

  private void myPostHandle(RoutingContext ctx) {
    logger.info("myPostHandle!");
    postHandlerHeaders = ctx.request().headers();
    postBuffer = Buffer.buffer();
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> HttpResponse.responseText(ctx, 204).end());
    } else {
      ctx.response().setStatusCode(200);
      ctx.request().handler(postBuffer::appendBuffer);
      ctx.request().endHandler(res -> {
        logger.info("myPostHandle end=" + postBuffer.toString());
        ctx.response().end();
      });
    }
  }

  private void myEdgeCallTest(RoutingContext ctx, String token) {
    HttpClientRequest get = httpClient.get(port, "localhost", "/testb/1", res1 -> {
      Buffer resBuf = Buffer.buffer();
      res1.handler(resBuf::appendBuffer);
      res1.endHandler(res2 -> {
        ctx.response().setStatusCode(res1.statusCode());
        ctx.response().end(resBuf);
      });
    });
    get.putHeader("X-Okapi-Token", token);
    get.end();
  }

  private void myEdgeHandle(RoutingContext ctx) {
    logger.info("myEdgeHandle");
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> HttpResponse.responseText(ctx, 204).end());
    } else if (HttpMethod.GET.equals(ctx.request().method())) {
      Buffer buf = Buffer.buffer();
      ctx.request().handler(buf::appendBuffer);
      ctx.request().endHandler(res -> {
        String p = ctx.request().path();
        if (!p.startsWith("/edge/")) {
          ctx.response().setStatusCode(404);
          ctx.response().end("Edge module reports not found");
          return;
        }
        String tenant = p.substring(6);
        final String docLogin = "{" + LS
          + "  \"tenant\" : \"" + tenant + "\"," + LS
          + "  \"username\" : \"peter\"," + LS
          + "  \"password\" : \"peter-password\"" + LS
          + "}";
        HttpClientRequest post = httpClient.post(port, "localhost", "/authn/login", res1 -> {
          Buffer loginBuf = Buffer.buffer();
          res1.handler(loginBuf::appendBuffer);
          res1.endHandler(res2 -> {
            if (res1.statusCode() != 200) {
              ctx.response().setStatusCode(res1.statusCode());
              ctx.response().end(loginBuf);
            } else {
              myEdgeCallTest(ctx, res1.getHeader("X-Okapi-Token"));
            }
          });
        });
        post.putHeader("Content-Type", "application/json");
        post.putHeader("Accept", "application/json");
        post.putHeader("X-Okapi-Tenant", tenant);
        post.end(docLogin);
      });
    } else {
      ctx.response().setStatusCode(404);
      ctx.response().end("Unsupported method");
    }
  }

  private void setupPreServer(TestContext context, Async async) {
    Router router = Router.router(vertx);

    router.routeWithRegex("/.*").handler(this::myPreHandle);

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
      .requestHandler(router::accept)
      .listen(
        portPre,
        result -> setupPostServer(context, async)
      );
  }

  private void setupPostServer(TestContext context, Async async) {
    Router router = Router.router(vertx);

    router.routeWithRegex("/.*").handler(this::myPostHandle);

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
      .requestHandler(router::accept)
      .listen(
        portPost,
        result -> setupEdgeServer(context, async)
      );
  }

  private void setupEdgeServer(TestContext context, Async async) {
    Router router = Router.router(vertx);

    router.routeWithRegex("/.*").handler(this::myEdgeHandle);

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
      .requestHandler(router::accept)
      .listen(
        portEdge,
        result -> {
          if (result.failed()) {
            context.fail(result.cause());
          }
          async.complete();
        }
      );
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    httpClient = vertx.createHttpClient();
    Async async = context.async();

    RestAssured.port = port;
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", Integer.toString(port)));
    vertx.deployVerticle(MainVerticle.class.getName(), opt, res -> {
      if (res.failed()) {
        context.fail(res.cause());
      } else {
        this.setupPreServer(context, async);
      }
    });
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
        httpClient.close();
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

    // tenant but no token
    c = api.createRestAssured3();
    r = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/testb/hugo")
      .then().statusCode(200).log().ifValidationFails()
      .extract().response();
    Assert.assertEquals("It works", r.getBody().asString());

    // token with implied tenant
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

  @Test
  public void testProxy(TestContext context) {
    final String okapiTenant = "roskilde";

    RestAssuredClient c;
    Response r;

    String nodeListDoc = "[ {" + LS
      + "  \"nodeId\" : \"localhost\"," + LS
      + "  \"url\" : \"http://localhost:9230\"" + LS
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
    String locationAuthDeployment = r.getHeader("Location");

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
    c.given()
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
    String locationSampleDeployment = r.getHeader("Location");

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
      + "  }]," + LS
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
    c.given()
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
    given()
      .header("Content-Type", "application/json")
      .body(docEnableWithoutDep).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(400);

    // try to enable a module we don't know
    final String docEnableAuthBad = "{" + LS
      + "  \"id\" : \"UnknonwModule-1\"" + LS
      + "}";
    given()
      .header("Content-Type", "application/json")
      .body(docEnableAuthBad).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(404);

    final String docEnableAuth = "{" + LS
      + "  \"id\" : \"auth-1\"" + LS
      + "}";
    given()
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

    given().get("/_/proxy/tenants/" + okapiTenant + "/modules/")
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
    given()
      .header("Content-Type", "application/json")
      .body("{").post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(400);

    // Enable the sample
    // Note that we can do this without the auth token. The test-auth module
    // will create a non-login token certifying that we do not have a login,
    // but will allow requests to any /_/ path,
    final String docEnableSample = "{" + LS
      + "  \"id\" : \"sample-module-1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().log().ifValidationFails()
      .statusCode(201)
      .body(equalTo(docEnableSample));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Try to enable it again, should fail
    given()
      .header("Content-Type", "application/json")
      .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(400)
      .body(containsString("already provided"));

    given().get("/_/proxy/tenants/" + okapiTenant + "/modules/")
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
    given().delete("/_/proxy/tenants/" + okapiTenant + "/modules/auth-1")
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
    // In theory, this is acceptable, we should get back a token that certifies
    // that we have no logged-in username. We can use this for modulePermissions
    // still. A real auth module would be likely to refuse the request because
    // we do not have the necessary ModulePermissions.
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-all-headers", "B") // ask sample to report all headers
      .get("/testb")
      .then().log().ifValidationFails()
      .statusCode(200);

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
    String okapiToken = given().header("Content-Type", "application/json").body(docLogin)
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
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .header("X-all-headers", "H") // ask sample to report all headers
      .body("Okapi").post("/testb?query=foo")
      .then().statusCode(200)
      .header("X-Url-Params", "query=foo")
      .header("Content-Type", "text/xml")
      .body(equalTo("<test>hej Okapi</test>"));

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
      .get("/testbZZZ")
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

    // Get with unknown instanceId AND serviceId
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/foo/xyz")
      .then().statusCode(404)
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Get with unknown instanceId
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/sample-module2-1/xyz")
      .then().statusCode(404)
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Get with unknown serviceId
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/foo/sample2-inst")
      .then().statusCode(404)
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

    // Health with unknown instanceId
    c = api.createRestAssured3();
    c.given().get("/_/discovery/health/sample-module2-1/xyz")
      .then().statusCode(404)
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // health with unknown serviceId
    c = api.createRestAssured3();
    c.given().get("/_/discovery/health/foo/sample2-inst")
      .then().statusCode(404)
      .log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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
      .then().statusCode(200).body(equalTo("It works"));

    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .body("OkapiX").post("/testb")
      .then().statusCode(200).body(equalTo("hej <test>hej OkapiX</test>"));

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
    given().delete(locationSample3Inst).then().log().ifValidationFails().statusCode(204);
    given().delete(locationSample3Module).then().log().ifValidationFails().statusCode(204);
    given().delete("/_/proxy/modules/sample-module-1").then().log().ifValidationFails().statusCode(204);
    given().delete("/_/proxy/modules/sample-module2-1").then().log().ifValidationFails().statusCode(204);
    given().delete("/_/proxy/modules/auth-1").then().log().ifValidationFails().statusCode(204);
    given().delete(locationAuthDeployment).then().log().ifValidationFails().statusCode(204);
    locationAuthDeployment = null;
    given().delete(locationSampleDeployment).then().log().ifValidationFails().statusCode(204);
    locationSampleDeployment = null;
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
    Async async = context.async();
    RestAssuredClient c;
    Response r;
    final String okapiTenant = "roskilde";

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
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"interfaceType\" : \"proxy\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testr\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/loop2\"," + LS
      + "    \"level\" : \"22\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/loop1\"" + LS
      + "  }, {" + LS
      + "    \"modulePermissions\" : [ \"sample.modperm\" ]," + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/chain3\"," + LS
      + "    \"level\" : \"23\"," + LS
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
    String locationSampleDeployment = r.getHeader("Location");

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
    String locationHeaderDeployment = r.getHeader("Location");

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

    async.complete();
  }

  @Test
  public void testRequestOnly(TestContext context) {
    final String okapiTenant = "roskilde";
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

    final String docRequestPre = "{" + LS
      + "  \"id\" : \"request-pre-1.0.0\"," + LS
      + "  \"name\" : \"request-pre\"," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"phase\" : \"pre\"," + LS
      + "    \"type\" : \"request-log\"" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docRequestPre).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docRequestPost = "{" + LS
      + "  \"id\" : \"request-post-1.0.0\"," + LS
      + "  \"name\" : \"request-post\"," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"phase\" : \"post\"," + LS
      + "    \"type\" : \"request-log\"" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docRequestPost).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docRequestOnly = "{" + LS
      + "  \"id\" : \"request-only-1.0.0\"," + LS
      + "  \"name\" : \"request-only\"," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"level\" : \"33\"," + LS
      + "    \"type\" : \"request-only\"" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docRequestOnly).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docSample = "{" + LS
      + "  \"id\" : \"sample-module-1.0.0\"," + LS
      + "  \"name\" : \"sample-module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myfirst\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\", \"DELETE\"]," + LS
      + "      \"path\" : \"/testb\"" + LS
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
      .body(docSample).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"sample-module-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"request-only-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"request-only-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .body("Okapi").post("/testb")
      .then().statusCode(200)
      .header("Content-Type", "text/xml")
      .body(equalTo("<test>Hello Okapi</test>"));

    given().header("X-Okapi-Tenant", okapiTenant)
      .header("Content-Type", "text/plain")
      .header("X-Handler-error", "true")
      .header("Accept", "text/xml")
      .body("Okapi").post("/testb")
      .then().statusCode(500)
      .body(equalTo("Okapi"));

    final String nodeDoc1 = "{" + LS
      + "  \"instId\" : \"localhost-" + Integer.toString(portPre) + "\"," + LS
      + "  \"srvcId\" : \"request-pre-1.0.0\"," + LS
      + "  \"url\" : \"http://localhost:" + Integer.toString(portPre) + "\"" + LS
      + "}";

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(nodeDoc1).post("/_/discovery/modules")
      .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String nodeDoc2 = "{" + LS
      + "  \"instId\" : \"localhost-" + Integer.toString(portPost) + "\"," + LS
      + "  \"srvcId\" : \"request-post-1.0.0\"," + LS
      + "  \"url\" : \"http://localhost:" + Integer.toString(portPost) + "\"" + LS
      + "}";

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(nodeDoc2).post("/_/discovery/modules")
      .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"request-pre-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"auth-f-module-1\", \"action\" : \"enable\"},"
        + " {\"id\" : \"request-only-1.0.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"request-pre-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"auth-f-module-1\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"request-only-1.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // login and get token
    final String docLogin = "{" + LS
      + "  \"tenant\" : \"" + okapiTenant + "\"," + LS
      + "  \"username\" : \"peter\"," + LS
      + "  \"password\" : \"peter-password\"" + LS
      + "}";
    final String okapiToken = given().header("Content-Type", "application/json").body(docLogin)
      .header("X-Okapi-Tenant", okapiTenant).post("/authn/login")
      .then().statusCode(200).extract().header("X-Okapi-Token");

    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .body("Okapi").post("/testb")
      .then().statusCode(200).log().ifValidationFails()
      .header("Content-Type", "text/xml")
      .body(equalTo("<test>Hello Okapi</test>"));
    Assert.assertEquals("Okapi", preBuffer.toString());

    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .delete("/testb")
      .then().statusCode(204).log().ifValidationFails();

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"request-post-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"request-post-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));

    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .delete("/testb")
      .then().statusCode(204).log().ifValidationFails();

    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .body("Okapi").post("/testb")
      .then().statusCode(200).log().ifValidationFails()
      .header("Content-Type", "text/xml")
      .body(equalTo("<test>Hello Okapi</test>"));
    Assert.assertEquals("Okapi", preBuffer.toString());

    Async async = context.async();
    vertx.setTimer(300, res -> {
      context.assertEquals("<test>Hello Okapi</test>", postBuffer.toString());
      context.assertNotNull(postHandlerHeaders);
      context.assertEquals("200", postHandlerHeaders.get(HANDLER_RESULT));
      async.complete();
    });
  }

  @Test
  public void testEdgeCase(TestContext context) {
    RestAssuredClient c;
    Response r;
    final String superTenant = "supertenant";

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
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
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

    final String docEdge_1_0_0 = "{" + LS
      + "  \"id\" : \"edge-module-1.0.0\"," + LS
      + "  \"name\" : \"edge module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"edge\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/edge/{id}\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docEdge_1_0_0).post("/_/proxy/modules").then().statusCode(201)
      .extract().response();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String nodeDiscoverEdge = "{" + LS
      + "  \"instId\" : \"localhost-" + Integer.toString(portEdge) + "\"," + LS
      + "  \"srvcId\" : \"edge-module-1.0.0\"," + LS
      + "  \"url\" : \"http://localhost:" + Integer.toString(portEdge) + "\"" + LS
      + "}";

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(nodeDiscoverEdge).post("/_/discovery/modules")
      .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"edge-module-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"auth-module-1.0.0\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + superTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String okapiTenant = "roskilde";
    // add tenant
    final String docTenantRoskilde = "{" + LS
      + "  \"id\" : \"" + okapiTenant + "\"," + LS
      + "  \"name\" : \"" + okapiTenant + "\"," + LS
      + "  \"description\" : \"Roskilde bibliotek\"" + LS
      + "}";
    c = api.createRestAssured3();
    given()
      .header("Content-Type", "application/json")
      .body(docTenantRoskilde).post("/_/proxy/tenants")
      .then().statusCode(201)
      .body(equalTo(docTenantRoskilde));

    given()
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .body("Okapi").get("/edge/roskilde")
      .then().statusCode(404).log().ifValidationFails();

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"basic-module-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"auth-module-1.0.0\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails();

    given()
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .body("Okapi").get("/edge/roskilde")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("It works"));

    given()
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .body("Okapi").get("/edge/unknown")
      .then().statusCode(400).log().ifValidationFails()
      .body(equalTo("No such Tenant unknown"));
  }
}
