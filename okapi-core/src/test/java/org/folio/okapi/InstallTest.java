package org.folio.okapi;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.HttpResponse;
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

  ValidatableResponse pollComplete(TestContext context, String uri)
  {
    ValidatableResponse body = null;
    for (int i = 0; i < 10; i++) {
      RestAssuredClient c = api.createRestAssured3();
      logger.info("poll {}", i);

      body = c.given()
          .get(uri)
          .then().statusCode(200);
      Response r = body.extract().response();
      if (Boolean.TRUE.equals(r.jsonPath().getBoolean("complete"))) {
        break;
      }

      Async async = context.async();
      vertx.setTimer(300, x -> async.complete());
      async.await();
    }
    return body;
  }

  @Test
  public void installGetNotFound(TestContext context) {
    RestAssuredClient c = api.createRestAssured3();
    Response r;

    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/foo/modules/12121")
        .then().statusCode(404).body(equalTo("foo"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }

  @Test
  public void installDeployFail(TestContext context) {
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
        + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-unknown.jar\"" + LS
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

    String suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));
    pollComplete(context, suffix).body(equalTo("{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"basic-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"message\" : \"Service returned with exit code 1\"," + LS
            + "    \"status\" : \"deploy\"" + LS
            + "  } ]" + LS
            + "}"));
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

    String suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

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
            + "}"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    pollComplete(context, suffix).body(equalTo("{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"basic-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"status\" : \"done\"" + LS
            + "  } ]" + LS
            + "}"));

    // known installId but unknown tenantId
    c = api.createRestAssured3();
    c.given()
        .get(suffix.replace("roskilde", "nosuchtenant"))
        .then().statusCode(404).body(equalTo("nosuchtenant"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // unknown installId, known tenantId
    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/roskilde/modules/12121")
        .then().statusCode(404).body(equalTo("12121"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }

  private int timerDelaySum = 0;
  private int timerTenantInitStatus = 200;
  private int timerTenantPermissionsStatus = 200;
  private HttpServer listenTimer;
  private JsonObject timerPermissions = new JsonObject();
  private JsonArray edgePermissionsAtInit = null;
  private JsonObject timerTenantData;
  private int portTimer = 9235;

  private void myTimerHandle(RoutingContext ctx) {
    final String p = ctx.request().path();
    logger.info("myTimerHandle p=" + p);
    for (Map.Entry<String, String> ent : ctx.request().headers().entries()) {
      logger.info(ent.getKey() + ":" + ent.getValue());
    }
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> HttpResponse.responseText(ctx, 204).end());
    } else if (HttpMethod.POST.equals(ctx.request().method())) {
      Buffer buf = Buffer.buffer();
      ctx.request().handler(buf::appendBuffer);
      ctx.request().endHandler(res -> {
        try {
          if (p.startsWith("/_/tenant")) {
            timerTenantData = new JsonObject(buf);
            ctx.response().setStatusCode(timerTenantInitStatus);
            ctx.response().end("timer response");
          } else if (p.startsWith("/permissionscall")) {
            JsonObject permObject = new JsonObject(buf);
            if (timerTenantPermissionsStatus == 200) {
              timerPermissions.put(permObject.getString("moduleId"), permObject.getJsonArray("perms"));
            }
            ctx.response().setStatusCode(timerTenantPermissionsStatus);
            ctx.response().end("timer permissions response");
          } else if (p.startsWith("/timercall/")) {
            long delay = Long.parseLong(p.substring(11)); // assume /timercall/[0-9]+
            timerDelaySum += delay;
            vertx.setTimer(delay, x -> {
              ctx.response().setStatusCode(200);
              ctx.response().end();
            });
          } else {
            ctx.response().setStatusCode(404);
            ctx.response().end(p);
          }
        } catch (Exception ex) {
          ctx.response().setStatusCode(400);
          ctx.response().end(ex.getMessage());
        }
      });
    } else {
      ctx.response().setStatusCode(404);
      ctx.response().end("Unsupported method");
    }
  }

  Future<HttpServer> startTimerServer() {
    Router router = Router.router(vertx);
    router.routeWithRegex("/.*").handler(this::myTimerHandle);
    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    return vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(portTimer);
  }

  HttpServer timerServer = null;

  @Test
  public void installTenantInit(TestContext context) {
    RestAssuredClient c;
    Response r;

    startTimerServer().onComplete(context.asyncAssertSuccess(x -> timerServer = x));

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

    final String docTimer_1_0_0 = "{" + LS
        + "  \"id\" : \"timer-module-1.0.0\"," + LS
        + "  \"name\" : \"timer module\"," + LS
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
        + "  }, {" + LS
        + "    \"id\" : \"_tenantPermissions\"," + LS
        + "    \"version\" : \"1.1\"," + LS
        + "    \"interfaceType\" : \"system\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"POST\" ]," + LS
        + "      \"pathPattern\" : \"/permissionscall\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  }, {" + LS
        + "    \"id\" : \"_timer\"," + LS
        + "    \"version\" : \"1.0\"," + LS
        + "    \"interfaceType\" : \"system\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"POST\" ]," + LS
        + "      \"pathPattern\" : \"/timercall/1\"," + LS
        + "      \"unit\" : \"millisecond\"," + LS
        + "      \"delay\" : \"10\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "   }, {" + LS
        + "      \"methods\" : [ \"GET\" ]," + LS
        + "      \"path\" : \"/timercall/3\"," + LS
        + "      \"unit\" : \"millisecond\"," + LS
        + "      \"delay\" : \"30\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  }, {" + LS
        + "    \"id\" : \"myint\"," + LS
        + "    \"version\" : \"1.0\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"POST\" ]," + LS
        + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  } ]," + LS
        + "  \"requires\" : [ ]" + LS
        + "}";

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(docTimer_1_0_0).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();

    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&deploy=true")
        .then().statusCode(201)
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"timer-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"))
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    String locationInstallJob = r.getHeader("Location");
    String suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    pollComplete(context, suffix).body(equalTo("{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"timer-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"message\" : \"Module timer-module-1.0.0 has no launchDescriptor\"," + LS
            + "    \"status\" : \"deploy\"" + LS
            + "  } ]" + LS
            + "}"));

    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"timer-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"))
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    locationInstallJob = r.getHeader("Location");
    suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    pollComplete(context, suffix).body(equalTo("{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"timer-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"message\" : \"No running instances for module timer-module-1.0.0. Can not invoke /_/tenant\"," + LS
            + "    \"status\" : \"call\"" + LS
            + "  } ]" + LS
            + "}"));

    final String nodeDoc1 = "{" + LS
        + "  \"instId\" : \"localhost-" + Integer.toString(portTimer) + "\"," + LS
        + "  \"srvcId\" : \"timer-module-1.0.0\"," + LS
        + "  \"url\" : \"http://localhost:" + Integer.toString(portTimer) + "\"" + LS
        + "}";

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
        .body(nodeDoc1).post("/_/discovery/modules")
        .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    timerTenantInitStatus = 403;

    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"timer-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"))
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    locationInstallJob = r.getHeader("Location");
    suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    pollComplete(context, suffix).body(equalTo("{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"timer-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"message\" : \"POST request for timer-module-1.0.0 /_/tenant failed with 403: timer response\"," + LS
            + "    \"status\" : \"call\"" + LS
            + "  } ]" + LS
            + "}"));

    timerTenantInitStatus = 200;
    timerTenantPermissionsStatus = 500;

    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"timer-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"))
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    locationInstallJob = r.getHeader("Location");
    suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    pollComplete(context, suffix).body(equalTo("{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"timer-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"message\" : \"POST request for timer-module-1.0.0 /permissionscall failed with 500: timer permissions response\"," + LS
            + "    \"status\" : \"call\"" + LS
            + "  } ]" + LS
            + "}"));

    timerTenantInitStatus = 200;
    timerTenantPermissionsStatus = 200;

    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"timer-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"))
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    locationInstallJob = r.getHeader("Location");
    suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    pollComplete(context, suffix).body(equalTo(
        "{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"timer-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"status\" : \"done\"" + LS
            + "  } ]" + LS
            + "}"));

    c = api.createRestAssured3();
    c.given()
        .header("X-Okapi-Tenant", okapiTenant)
        .header("Content-Type", "application/json")
        .body("{}")
        .post("/timercall/1")
        .then().statusCode(200);

    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"timer-module-1.0.0\", \"action\" : \"disable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"timer-module-1.0.0\"," + LS
            + "  \"action\" : \"disable\"" + LS
            + "} ]"))
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    locationInstallJob = r.getHeader("Location");
    suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    pollComplete(context, suffix).body(equalTo(
        "{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"timer-module-1.0.0\"," + LS
            + "    \"action\" : \"disable\"," + LS
            + "    \"status\" : \"done\"" + LS
            + "  } ]" + LS
            + "}"));

    timerTenantInitStatus = 401;
    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&ignoreErrors=true")
        .then().statusCode(201)
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"timer-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"))
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    locationInstallJob = r.getHeader("Location");
    suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));
    pollComplete(context, suffix).body(equalTo(
        "{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"timer-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"message\" : \"POST request for timer-module-1.0.0 /_/tenant failed with 401: timer response\"," + LS
            + "    \"status\" : \"call\"" + LS
            + "  } ]" + LS
            + "}"));

    final String docOther_1_0_0 = "{" + LS
        + "  \"id\" : \"other-module-1.0.0\"," + LS
        + "  \"name\" : \"timer module\"," + LS
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
        + "  \"requires\" : [ ]" + LS
        + "}";

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(docOther_1_0_0).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();

    final String nodeDoc2 = "{" + LS
        + "  \"instId\" : \"localhost2-" + Integer.toString(portTimer) + "\"," + LS
        + "  \"srvcId\" : \"other-module-1.0.0\"," + LS
        + "  \"url\" : \"http://localhost:" + Integer.toString(portTimer) + "\"" + LS
        + "}";

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
        .body(nodeDoc2).post("/_/discovery/modules")
        .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    timerTenantInitStatus = 401;
    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"}, {\"id\":\"other-module-1.0.0\", \"action\":\"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&ignoreErrors=true")
        .then().statusCode(201)
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"timer-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"other-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"))
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    locationInstallJob = r.getHeader("Location");
    suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));
    pollComplete(context, suffix).body(equalTo(
        "{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"timer-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"message\" : \"POST request for timer-module-1.0.0 /_/tenant failed with 401: timer response\"," + LS
            + "    \"status\" : \"call\"" + LS
            + "  }, {" + LS
            + "    \"id\" : \"other-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"message\" : \"POST request for other-module-1.0.0 /_/tenant failed with 401: timer response\"," + LS
            + "    \"status\" : \"call\"" + LS
            + "  } ]" + LS
            + "}"));

    timerTenantInitStatus = 401;
    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"}, {\"id\":\"other-module-1.0.0\", \"action\":\"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&ignoreErrors=false")
        .then().statusCode(201)
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"timer-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"other-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"))
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    locationInstallJob = r.getHeader("Location");
    suffix = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));
    pollComplete(context, suffix).body(equalTo(
        "{" + LS
            + "  \"complete\" : true," + LS
            + "  \"modules\" : [ {" + LS
            + "    \"id\" : \"timer-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"message\" : \"POST request for timer-module-1.0.0 /_/tenant failed with 401: timer response\"," + LS
            + "    \"status\" : \"call\"" + LS
            + "  }, {" + LS
            + "    \"id\" : \"other-module-1.0.0\"," + LS
            + "    \"action\" : \"enable\"," + LS
            + "    \"status\" : \"idle\"" + LS
            + "  } ]" + LS
            + "}"));

    timerServer.close();
  }

}
