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
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.testutil.ModuleTenantInitAsync;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@RunWith(VertxUnitRunner.class)
public class InstallTest {
  private static RamlDefinition api;

  private final Logger logger = OkapiLogger.get();
  private Vertx vertx;
  private final int portOkapi = 9230;
  private final int portModule = 9235;
  private final int portModule2 = 9236;
  private HttpServer httpServerV1 = null;
  private Async asyncV1 = null;  // used to wake up the v1 server's init.
  private int v1TenantInitStatus = 200;
  private int v1TenantPermissionsStatus = 200;

  @BeforeClass
  public static void setUpBeforeClass() {
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  Future<Void> startOkapi() {
    DeploymentOptions opt = new DeploymentOptions()
        .setConfig(new JsonObject()
            .put("port", Integer.toString(portOkapi)));
    return vertx.deployVerticle(MainVerticle.class.getName(), opt).mapEmpty();
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    RestAssured.port = portOkapi;

    Future<Void> future = startOkapi();
    future.onComplete(context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    RestAssuredClient c = api.createRestAssured3();

    c.given().delete("/_/discovery/modules").then().statusCode(204);
    vertx.close(context.asyncAssertSuccess());
  }

  JsonObject pollComplete(TestContext context, String path) {
    for (int i = 0; i < 20; i++) {
      RestAssuredClient c = api.createRestAssured3();
      logger.info("poll {}", i);

      ValidatableResponse body = c.given()
          .get(path)
          .then().statusCode(200);
      Response r = body.extract().response();
      JsonObject job = new JsonObject(r.body().asString());
      if (Boolean.TRUE.equals(job.getBoolean("complete"))) {
        return job;
      }
      Async async = context.async();
      vertx.setTimer(300, x -> async.complete());
      async.await();
    }
    return new JsonObject();
  }

  JsonObject pollCompleteStrip(TestContext context, String path) {
    JsonObject job = pollComplete(context, path);
    job.remove("startDate");
    job.remove("endDate");
    job.remove("id");
    return job;
  }

  @Test
  public void installGetNotFound(TestContext context) {
    RestAssuredClient c;
    final String okapiTenant = "roskilde";

    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/" + okapiTenant + "/install/12121")
        .then().statusCode(404).body(equalTo(okapiTenant));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    createTenant(context, okapiTenant);

    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/" + okapiTenant + "/install/12121")
        .then().statusCode(404).body(equalTo("roskilde/12121"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }

  @Test
  public void installDeleteNotFound(TestContext context) {
    RestAssuredClient c;
    final String okapiTenant = "roskilde";

    c = api.createRestAssured3();
    c.given()
        .delete("/_/proxy/tenants/" + okapiTenant + "/install/12121")
        .then().statusCode(404).body(equalTo(okapiTenant));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
        .delete("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(404).body(equalTo(okapiTenant));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    createTenant(context, okapiTenant);

    c = api.createRestAssured3();
    c.given()
        .delete("/_/proxy/tenants/" + okapiTenant + "/install/12121")
        .then().statusCode(404).body(equalTo("roskilde/12121"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }

  String createTenant(TestContext context, String tenant) {
    RestAssuredClient c;

    c = api.createRestAssured3();
    String location = c.given()
        .header("Content-Type", "application/json")
        .body(new JsonObject().put("id", tenant).encode()).post("/_/proxy/tenants")
        .then().statusCode(201).extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    return location;
  }

  @Test
  public void installDeleteNotCompleter(TestContext context) {
    RestAssuredClient c;
    final String okapiTenant = "roskilde";

    startV1Server().onComplete(context.asyncAssertSuccess(x -> httpServerV1 = x));

    createTenant(context, okapiTenant);
    createV1Module(context);

    JsonObject nodeObject = new JsonObject()
        .put("instId", "localhost-" + portModule)
        .put("srvcId", "init-v1-module-1.0.0")
        .put("url", "http://localhost:" + portModule);

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
        .body(nodeObject.encode()).post("/_/discovery/modules")
        .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    JsonArray installAr = new JsonArray().add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "enable"));

    asyncV1 = context.async(); // make our module wait in tenant init ..
    c = api.createRestAssured3();
    final String locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installAr.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo(installAr.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    String uri = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));
    c = api.createRestAssured3();
    c.given()
        .delete(uri)
        .then().statusCode(400).body(containsString("Cannot delete non-completed job"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // delete all jobs (leaves the non-completed job there
    c = api.createRestAssured3();
    c.given()
        .delete("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    asyncV1.complete(); // make tenant init complete
    asyncV1 = null;

    JsonObject job = pollCompleteStrip(context, uri);
    JsonObject jobExpected = new JsonObject()
        .put("complete", true)
            .put("modules", new JsonArray()
                .add(new JsonObject()
                    .put("id", "init-v1-module-1.0.0")
                    .put("action", "enable")
                    .put("stage", "done")
                ));
    context.assertEquals(jobExpected, job);

    c = api.createRestAssured3();
    c.given()
        .delete(uri)
        .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
        .delete(uri)
        .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
        .get(uri)
        .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    httpServerV1.close();
  }

  @Test
  public void installDeployFail(TestContext context) {
    RestAssuredClient c;
    final String okapiTenant = "roskilde";

    createTenant(context, okapiTenant);

    JsonObject basic_1_0_0 = new JsonObject()
        .put("id", "basic-module-1.0.0")
        .put("name", "this module")
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_tenant")
                .put("version", "1.1")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/_/tenant/disable")
                        .put("permissionsRequired", new JsonArray())
                    )
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST").add("DELETE"))
                        .put("pathPattern", "/_/tenant")
                        .put("permissionsRequired", new JsonArray())
                    )
                )
            )
        )
        .put("requires", new JsonArray())
        .put("launchDescriptor", new JsonObject()
            .put("exec", "java -Dport=%p -jar ../okapi-test-module/target/okapi-unknown.jar"));

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(basic_1_0_0.encode()).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    JsonArray installBody = new JsonArray()
        .add(new JsonObject()
            .put("id", "basic-module-1.0.0")
            .put("action", "enable")
        );
    c = api.createRestAssured3();
    final String locationInstallJob  = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&deploy=true")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    String path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    JsonObject job = pollCompleteStrip(context, path);
    JsonObject jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "basic-module-1.0.0")
                .put("action", "enable")
                .put("message", "Service basic-module-1.0.0 returned with exit code 1")
                .put("stage", "deploy")
            )
        );
    context.assertEquals(jobExpected, job);
  }

  @Test
  public void installOK(TestContext context) {
    RestAssuredClient c;
    final String okapiTenant = "roskilde";

    createTenant(context, okapiTenant);

    JsonObject basic_1_0_0 = new JsonObject()
        .put("id", "basic-module-1.0.0")
        .put("name", "this module")
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_tenant")
                .put("version", "1.1")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/_/tenant/disable")
                        .put("permissionsRequired", new JsonArray())
                    )
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST").add("DELETE"))
                        .put("pathPattern", "/_/tenant")
                        .put("permissionsRequired", new JsonArray())
                    )
                )
            )
        )
        .put("requires", new JsonArray())
        .put("launchDescriptor", new JsonObject()
            .put("exec", "java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar"));

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(basic_1_0_0.encode()).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    JsonArray installBody = new JsonArray()
        .add(new JsonObject()
            .put("id", "basic-module-1.0.0")
            .put("action", "enable")
        );

    c = api.createRestAssured3();
    String locationInstallJob  = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&deploy=true")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    String path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    JsonObject job = pollCompleteStrip(context, path);
    JsonObject jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "basic-module-1.0.0")
                .put("action", "enable")
                .put("stage", "done")
            )
        );
    context.assertEquals(jobExpected, job);

    c = api.createRestAssured3();
    locationInstallJob  = c.given()
        .header("Content-Type", "application/json")
        .body("")
        .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?async=true&deploy=true")
        .then().statusCode(201)
        .body(equalTo("[ ]"))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));
    job = pollCompleteStrip(context, path);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray());
    context.assertEquals(jobExpected, job);

    // known installId but unknown tenantId
    c = api.createRestAssured3();
    c.given()
        .get(path.replace("roskilde", "nosuchtenant"))
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

  private void v1Handle(RoutingContext ctx) {
    final String p = ctx.request().path();
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> HttpResponse.responseText(ctx, 204).end());
    } else if (HttpMethod.POST.equals(ctx.request().method())) {
      Buffer buf = Buffer.buffer();
      ctx.request().handler(buf::appendBuffer);
      ctx.request().endHandler(res -> {
        try {
          if (p.startsWith("/_/tenant")) {
            ctx.response().setStatusCode(v1TenantInitStatus);
            ctx.response().end("tenant response");
            if (asyncV1 != null) {
              asyncV1.await();
            }
          } else if (p.startsWith("/permissionscall")) {
            ctx.response().setStatusCode(v1TenantPermissionsStatus);
            ctx.response().end("permissions response");
          } else if (p.startsWith("/call")) {
            ctx.response().setStatusCode(200);
            ctx.response().end();
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

  Future<HttpServer> startV1Server() {
    Router router = Router.router(vertx);
    router.routeWithRegex("/.*").handler(this::v1Handle);
    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    return vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(portModule);
  }

  private void createV1Module(TestContext context) {
    RestAssuredClient c;

    JsonObject basic_1_0_0 = new JsonObject()
        .put("id", "init-v1-module-1.0.0")
        .put("name", "this module")
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_tenant")
                .put("version", "1.1")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/_/tenant/disable")
                        .put("permissionsRequired", new JsonArray())
                    )
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST").add("DELETE"))
                        .put("pathPattern", "/_/tenant")
                        .put("permissionsRequired", new JsonArray())
                    )
                )
            )
            .add(new JsonObject()
                .put("id", "_tenantPermissions")
                .put("version", "1.1")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/permissionscall")
                        .put("permissionsRequired", new JsonArray())
                    )
                )
            )
            .add(new JsonObject()
                .put("id", "myint")
                .put("version", "1.0")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/call")
                        .put("permissionsRequired", new JsonArray())
                    )
                )
            )
        )
        .put("requires", new JsonArray());

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(basic_1_0_0.encode()).post("/_/proxy/modules").then().statusCode(201);
  }

  @Test
  public void installTenantInitVersion1(TestContext context) {
    RestAssuredClient c;
    Response r;

    startV1Server().onComplete(context.asyncAssertSuccess(x -> httpServerV1 = x));

    final String okapiTenant = "roskilde";

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(404)
        .body(equalTo(okapiTenant));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    createTenant(context, okapiTenant);

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(200)
        .body(equalTo("[ ]"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    createV1Module(context);

    JsonArray installBody = new JsonArray()
        .add(new JsonObject()
            .put("id", "init-v1-module-1.0.0")
            .put("action", "enable")
        );

    c = api.createRestAssured3();
    String locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&deploy=true")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    String path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(200)
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    context.assertEquals(1, r.jsonPath().getList("$").size());
    String jobId = path.substring(path.lastIndexOf('/') + 1);

    JsonObject job = pollComplete(context, path);
    context.assertNotNull(job.remove("startDate"));
    context.assertNotNull(job.remove("endDate"));
    JsonObject jobExpected = new JsonObject()
        .put("id", jobId)
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "enable")
                .put("message", "Module init-v1-module-1.0.0 has no launchDescriptor")
                .put("stage", "deploy")
            )
        );
    context.assertEquals(jobExpected, job);

    c = api.createRestAssured3();
    locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    job = pollCompleteStrip(context, path);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "enable")
                .put("message", "No running instances for module init-v1-module-1.0.0. Can not invoke /_/tenant")
                .put("stage", "invoke")
            )
        );
    context.assertEquals(jobExpected, job);

    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(200)
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    context.assertEquals(2, r.jsonPath().getList("$").size());

    final JsonObject nodeDoc = new JsonObject()
        .put("instId", "localhost-" + portModule)
        .put("srvcId", "init-v1-module-1.0.0")
        .put("url", "http://localhost:" + portModule);

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
        .body(nodeDoc.encode()).post("/_/discovery/modules")
        .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    v1TenantInitStatus = 403;

    c = api.createRestAssured3();
    locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    job = pollCompleteStrip(context, path);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "enable")
                .put("message", "POST request for init-v1-module-1.0.0 /_/tenant failed with 403: tenant response")
                .put("stage", "invoke")
            )
        );
    context.assertEquals(jobExpected, job);

    v1TenantInitStatus = 200;
    v1TenantPermissionsStatus = 500;

    c = api.createRestAssured3();
    locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    job = pollCompleteStrip(context, path);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "enable")
                .put("message", "POST request for init-v1-module-1.0.0 /permissionscall failed with 500: permissions response")
                .put("stage", "invoke")
            )
        );
    context.assertEquals(jobExpected, job);

    v1TenantInitStatus = 200;
    v1TenantPermissionsStatus = 200;

    c = api.createRestAssured3();
    locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    job = pollCompleteStrip(context, path);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "enable")
                .put("stage", "done")
            )
        );
    context.assertEquals(jobExpected, job);

    c = api.createRestAssured3();
    c.given()
        .header("X-Okapi-Tenant", okapiTenant)
        .header("Content-Type", "application/json")
        .body("{}")
        .post("/call")
        .then().statusCode(200);

    installBody = new JsonArray()
        .add(new JsonObject()
            .put("id", "init-v1-module-1.0.0")
            .put("action", "disable")
        );

    c = api.createRestAssured3();
    locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    job = pollCompleteStrip(context, path);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "disable")
                .put("stage", "done")
            )
        );
    context.assertEquals(jobExpected, job);

    installBody = new JsonArray()
        .add(new JsonObject()
            .put("id", "init-v1-module-1.0.0")
            .put("action", "enable")
        );
    v1TenantInitStatus = 401;
    c = api.createRestAssured3();
    locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&ignoreErrors=true")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    job = pollCompleteStrip(context, path);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "enable")
                .put("message", "POST request for init-v1-module-1.0.0 /_/tenant failed with 401: tenant response")
                .put("stage", "invoke")
            )
        );
    context.assertEquals(jobExpected, job);

    JsonObject other_1_0_0 = new JsonObject()
        .put("id", "other-module-1.0.0")
        .put("name", "other module")
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_tenant")
                .put("version", "1.1")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/_/tenant/disable")
                        .put("permissionsRequired", new JsonArray())
                    )
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST").add("DELETE"))
                        .put("pathPattern", "/_/tenant")
                        .put("permissionsRequired", new JsonArray())
                    )
                )
            )
        )
        .put("requires", new JsonArray());

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(other_1_0_0.encode()).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();

    JsonObject nodeBody2 = new JsonObject()
        .put("instId", "localhost2-" + portModule)
        .put("srvcId", "other-module-1.0.0")
        .put("url", "http://localhost:" + portModule);

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
        .body(nodeBody2.encode()).post("/_/discovery/modules")
        .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    JsonObject fail_1_0_0 = new JsonObject()
        .put("id", "fail-module-1.0.0")
        .put("name", "failing module")
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_tenant")
                .put("version", "1.1")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST").add("DELETE"))
                        .put("pathPattern", "/_/badpath")
                        .put("permissionsRequired", new JsonArray())
                    )
                )
            )
        )
        .put("requires", new JsonArray());

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(fail_1_0_0.encode()).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();

    JsonObject nodeBody3 = new JsonObject()
        .put("instId", "localhost3-" + portModule)
        .put("srvcId", "fail-module-1.0.0")
        .put("url", "http://localhost:" + portModule);

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
        .body(nodeBody3.encode()).post("/_/discovery/modules")
        .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    installBody = new JsonArray()
        .add(new JsonObject()
            .put("id", "init-v1-module-1.0.0")
            .put("action", "enable")
        )
        .add(new JsonObject()
            .put("id", "other-module-1.0.0")
            .put("action", "enable")
        );

    v1TenantInitStatus = 401;
    c = api.createRestAssured3();
    locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&ignoreErrors=true")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    job = pollCompleteStrip(context, path);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "enable")
                .put("message", "POST request for init-v1-module-1.0.0 /_/tenant failed with 401: tenant response")
                .put("stage", "invoke")
            )
            .add(new JsonObject()
                .put("id", "other-module-1.0.0")
                .put("action", "enable")
                .put("message", "POST request for other-module-1.0.0 /_/tenant failed with 401: tenant response")
                .put("stage", "invoke")
            )
        );
    context.assertEquals(jobExpected, job);

    v1TenantInitStatus = 401;
    c = api.createRestAssured3();
    locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&ignoreErrors=false")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    job = pollCompleteStrip(context, path);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "enable")
                .put("message", "POST request for init-v1-module-1.0.0 /_/tenant failed with 401: tenant response")
                .put("stage", "invoke")
            )
            .add(new JsonObject()
                .put("id", "other-module-1.0.0")
                .put("action", "enable")
                .put("stage", "pending")
            )
        );
    context.assertEquals(jobExpected, job);

    v1TenantInitStatus = 200;
    c = api.createRestAssured3();
    locationInstallJob = c.given()
        .header("Content-Type", "application/json")
        .body(installBody.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?async=true&ignoreErrors=false")
        .then().statusCode(201)
        .body(equalTo(installBody.encodePrettily()))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    path = locationInstallJob.substring(locationInstallJob.indexOf("/_/"));

    job = pollCompleteStrip(context, path);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", "init-v1-module-1.0.0")
                .put("action", "enable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", "other-module-1.0.0")
                .put("action", "enable")
                .put("stage", "done")
            )
        );
    context.assertEquals(jobExpected, job);

    // get all install jobs and test that they are returned with oldest first
    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(200)
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    JsonArray ar = new JsonArray(r.body().asString());
    context.assertEquals(10, ar.size());
    String prevDate = "0";
    for (int i = 0; i < ar.size(); i++) {
      String thisDate = ar.getJsonObject(i).getString("startDate");
      context.assertTrue(thisDate.compareTo(prevDate) > 0);
      prevDate = thisDate;
    }

    // delete all jobs
    c = api.createRestAssured3();
    c.given()
        .delete("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(200).body(equalTo("[ ]"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    httpServerV1.close();
  }

  JsonObject createAsyncInitModule(String module) {
    final JsonObject md = new JsonObject()
        .put("id", module)
        .put("name", "async tenant init module")
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_tenant")
                .put("version", "2.0")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/_/tenant")
                        .put("permissionsRequired", new JsonArray())
                    )
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("GET").add("DELETE"))
                        .put("pathPattern", "/_/tenant/{id}")
                        .put("permissionsRequired", new JsonArray())
                    )
                )
            )
        )
        .put("requires", new JsonArray());
    return md;
  }

  void postAsyncInitModule(TestContext context, String module) {
    RestAssuredClient c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(createAsyncInitModule(module).encode()).post("/_/proxy/modules").then().statusCode(201);
  }

  void deployAsyncInitModule(TestContext context, String module, int port) {
    JsonObject node = new JsonObject()
        .put("instId", module + "-localhost-" + port)
        .put("srvcId", module)
        .put("url", "http://localhost:" + port);

    RestAssuredClient c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
        .body(node.encode()).post("/_/discovery/modules")
        .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }

  JsonObject installAndWait(TestContext context, String tenant, String module) {
    return installAndWait(context, tenant, module, "enable", "?async=true");
  }

  JsonObject installAndWait(TestContext context, String tenant, String module, String action, String installParameters) {
    RestAssuredClient c = api.createRestAssured3();
    String location = c.given()
        .header("Content-Type", "application/json")
        .body(new JsonArray().add(new JsonObject()
            .put("id", module)
            .put("action", action)
        ).encode())
        .post("/_/proxy/tenants/" + tenant + "/install" + installParameters)
        .then().statusCode(201)
        .body("[0].id", is(module))
        .body("[0].action", is(action))
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    return pollCompleteStrip(context, location);
  }

  @Test
  public void installTenantInitVersion2OK(TestContext context) {
    final String okapiTenant = "roskilde";
    final String module1 = "v2-module-1.0.0";

    createTenant(context, okapiTenant);
    postAsyncInitModule(context, module1);
    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);

    tModule.start().onComplete(context.asyncAssertSuccess());

    deployAsyncInitModule(context, module1, portModule);
    JsonObject job = installAndWait(context, okapiTenant, module1, "enable", "?async=true");
    context.assertEquals(new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", module1)
                .put("action", "enable")
                .put("stage", "done")
            )
        ), job);

    final String module2 = "v2-module-1.0.1";
    postAsyncInitModule(context, module2);
    ModuleTenantInitAsync tModule2 = new ModuleTenantInitAsync(vertx, portModule2);
    tModule2.start().onComplete(context.asyncAssertSuccess());

    deployAsyncInitModule(context, module2, portModule2);
    job = installAndWait(context, okapiTenant, module2, "enable", "?async=true");
    context.assertEquals(new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", module2)
                .put("from", module1)
                .put("action", "enable")
                .put("stage", "done")
            )
        ), job);

    job = installAndWait(context, okapiTenant, module2, "disable", "?async=true&purge=true");
    context.assertEquals(new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", module2)
                .put("action", "disable")
                .put("stage", "done")
            )
        ), job);

    // initial module called once..
    context.assertEquals(tModule.getOperations().get(0),
        new JsonObject()
            .put("module_to", module1)
            .put("purge", false));

    // second module called on upgrade
    context.assertEquals(tModule2.getOperations().get(0),
        new JsonObject()
            .put("module_to", module2)
            .put("module_from", module1)
            .put("purge", false));

    // second module called on purge
    context.assertEquals(tModule2.getOperations().get(1),
        new JsonObject()
            .put("module_from", module2)
            .put("purge", true));

    tModule.stop().onComplete(context.asyncAssertSuccess());
    tModule2.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void installTenantInitVersion2EnableWithPurge(TestContext context) {
    final String okapiTenant = "roskilde";
    final String module = "init-v2-module-1.0.0";

    createTenant(context, okapiTenant);
    postAsyncInitModule(context, module);
    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);

    tModule.start().onComplete(context.asyncAssertSuccess());

    deployAsyncInitModule(context, module, portModule);
    JsonObject job = installAndWait(context, okapiTenant, module, "enable", "?async=true&purge=true");
    context.assertEquals(new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", module)
                .put("action", "enable")
                .put("stage", "done")
            )
        ), job);
    context.assertEquals(tModule.getOperations().get(0),
        new JsonObject()
            .put("module_from", module)
            .put("purge", true));

    context.assertEquals(tModule.getOperations().get(1),
        new JsonObject()
            .put("module_to", module)
            .put("purge", false));
    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void installTenantInitVersion2EnableWithPurgeFailing(TestContext context) {
    final String okapiTenant = "roskilde";
    final String module = "init-v2-module-1.0.0";

    createTenant(context, okapiTenant);
    postAsyncInitModule(context, module);
    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);

    tModule.start().onComplete(context.asyncAssertSuccess());
    tModule.setPurgeFail(true);
    deployAsyncInitModule(context, module, portModule);
    JsonObject job = installAndWait(context, okapiTenant, module, "enable", "?async=true&purge=true");
    context.assertEquals(new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", module)
                .put("action", "enable")
                .put("stage", "done")
            )
        ), job);
    context.assertEquals(tModule.getOperations().get(0),
        new JsonObject()
            .put("module_from", module)
            .put("purge", true));

    context.assertEquals(tModule.getOperations().get(1),
        new JsonObject()
            .put("module_to", module)
            .put("purge", false));
    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void installTenantInitVersion2WrongVersion(TestContext context) {
    final String okapiTenant = "roskilde";
    final String module = "init-v2-module-1.0.0";

    createTenant(context, okapiTenant);

    final JsonObject md = new JsonObject()
        .put("id", module)
        .put("name", "async tenant init module")
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_tenant")
                .put("version", "1.2")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/_/tenant")
                        .put("permissionsRequired", new JsonArray())
                    )
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("DELETE"))
                        .put("pathPattern", "/_/tenant")
                        .put("permissionsRequired", new JsonArray())
                    )
                )
            )
        )
        .put("requires", new JsonArray());

    RestAssuredClient c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(md.encode()).post("/_/proxy/modules").then().statusCode(201);

    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);

    tModule.start().onComplete(context.asyncAssertSuccess());

    deployAsyncInitModule(context, module, portModule);

    JsonObject job = installAndWait(context, okapiTenant, module);
    JsonObject jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", module)
                .put("action", "enable")
                .put("message","Unexpected Location header in response for module " + module + ": POST /_/tenant")
                .put("stage", "invoke")
            ));
    context.assertEquals(jobExpected, job);

    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void installTenantInitVersion2MissingDeleteMethod(TestContext context) {
    final String okapiTenant = "roskilde";
    final String module = "init-v2-module-1.0.0";

    createTenant(context, okapiTenant);

    final JsonObject md = new JsonObject()
        .put("id", module)
        .put("name", "async tenant init module")
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_tenant")
                .put("version", "2.0")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/_/tenant")
                        .put("permissionsRequired", new JsonArray())
                    )
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("GET"))
                        .put("pathPattern", "/_/tenant/{id}")
                        .put("permissionsRequired", new JsonArray())
                    )
                )
            )
        )
        .put("requires", new JsonArray());

    RestAssuredClient c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(md.encode()).post("/_/proxy/modules").then().statusCode(201);

    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);

    tModule.start().onComplete(context.asyncAssertSuccess());

    deployAsyncInitModule(context, module, portModule);

    JsonObject job = installAndWait(context, okapiTenant, module);
    JsonObject jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", module)
                .put("action", "enable")
                .put("message", "Missing DELETE method for tenant interface version 2 for module " + module)
                .put("stage", "invoke")
            ));
    context.assertEquals(jobExpected, job);

    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void installTenantInitVersion2NoLocation(TestContext context) {
    final String okapiTenant = "roskilde";
    String module = "init-v2-module-1.0.0";

    createTenant(context, okapiTenant);
    postAsyncInitModule(context, module);
    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);

    tModule.setOmitLocationInResponse(true);
    tModule.start().onComplete(context.asyncAssertSuccess());

    deployAsyncInitModule(context, module, portModule);

    JsonObject job = installAndWait(context, okapiTenant, module);
    JsonObject jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", module)
                .put("action", "enable")
                .put("stage", "done")
            ));
    context.assertEquals(jobExpected, job);

    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void installTenantInitVersion2NoId(TestContext context) {
    final String okapiTenant = "roskilde";
    final String module ="init-v2-module-1.0.0";

    createTenant(context, okapiTenant);
    postAsyncInitModule(context, module);
    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);

    tModule.setOmitIdInResponse(true);
    tModule.start().onComplete(context.asyncAssertSuccess());

    deployAsyncInitModule(context, module, portModule);

    JsonObject job = installAndWait(context, okapiTenant, module);
    JsonObject jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", module)
                .put("action", "enable")
                .put("message", "Missing id property in JSON response for module " + module + ": POST /_/tenant")
                .put("stage", "invoke")
            )
        );
    context.assertEquals(jobExpected, job);


    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void installTenantInitVersion2BadJson(TestContext context) {
    final String okapiTenant = "roskilde";
    final String module = "init-v2-module-1.0.0";

    createTenant(context, okapiTenant);
    postAsyncInitModule(context, module);
    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);

    tModule.setBadJsonResponse(true);
    tModule.start().onComplete(context.asyncAssertSuccess());

    deployAsyncInitModule(context, module, portModule);

    JsonObject job = installAndWait(context, okapiTenant, module);
    context.assertTrue(job.getJsonArray("modules").getJsonObject(0).
        getString("message").startsWith("Failed to decode:Unexpected close marker"));

    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void installTenantInitVersion2Status400(TestContext context) {
    final String okapiTenant = "roskilde";
    final String module = "init-v2-module-1.0.0";

    createTenant(context, okapiTenant);
    postAsyncInitModule(context, module);
    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);

    tModule.setGetStatusResponse(400);
    tModule.start().onComplete(context.asyncAssertSuccess());

    deployAsyncInitModule(context, module, portModule);

    JsonObject job = installAndWait(context, okapiTenant, module);
    context.assertTrue(job.getJsonArray("modules").getJsonObject(0).
        getString("message").contains("failed with 400:"), job.encodePrettily());

    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void installTenantInitVersion2JobError(TestContext context) {
    final String okapiTenant = "roskilde";
    final String module = "init-v2-module-1.0.0";

    createTenant(context, okapiTenant);
    postAsyncInitModule(context, module);
    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);

    tModule.setErrorMessage("foo bar error", null);
    tModule.start().onComplete(context.asyncAssertSuccess());

    deployAsyncInitModule(context, module, portModule);

    JsonObject job = installAndWait(context, okapiTenant, module);
    context.assertEquals("Tenant operation failed for module init-v2-module-1.0.0: foo bar error", job.getJsonArray("modules")
        .getJsonObject(0).getString("message"));

    tModule.setErrorMessage("foo bar error", new JsonArray().add("msg1").add("msg2"));

    job = installAndWait(context, okapiTenant, module);
    context.assertEquals("Tenant operation failed for module init-v2-module-1.0.0: foo bar error\nmsg1\nmsg2", job.getJsonArray("modules")
        .getJsonObject(0).getString("message"));

    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void parallelMigration(TestContext context) {
    JsonObject moda = createAsyncInitModule("mod-a-1.0.0");
    moda.getJsonArray("provides")
        .add(new JsonObject()
            .put("id", "a")
            .put("version", "1.0")
        );
    given()
        .header("Content-Type", "application/json")
        .body(moda.encode()).post("/_/proxy/modules").then().statusCode(201);

    JsonObject modb = createAsyncInitModule("mod-b-1.0.0");
    modb.getJsonArray("provides")
        .add(new JsonObject()
            .put("id", "b")
            .put("version", "1.0")
        );
    given()
        .header("Content-Type", "application/json")
        .body(modb.encode()).post("/_/proxy/modules").then().statusCode(201);

    JsonObject mid = createAsyncInitModule("mod-m-1.0.0");
    mid.getJsonArray("requires")
        .add(new JsonObject()
            .put("id", "a")
            .put("version", "1.0")
        )
        .add(new JsonObject()
            .put("id", "b")
            .put("version", "1.0")
        );
    given()
        .header("Content-Type", "application/json")
        .body(mid.encode()).post("/_/proxy/modules").then().statusCode(201);

    final String tenant = "roskilde";
    createTenant(context, tenant);
    deployAsyncInitModule(context, moda.getString("id"), portModule);
    deployAsyncInitModule(context, modb.getString("id"), portModule);
    deployAsyncInitModule(context, mid.getString("id"), portModule);
    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);
    tModule.start().onComplete(context.asyncAssertSuccess());

    JsonArray installOp = new JsonArray()
        .add(new JsonObject()
            .put("id", moda.getString("id"))
            .put("action", "enable")
        )
        .add(new JsonObject()
            .put("id", modb.getString("id"))
            .put("action", "enable")
        )
        .add(new JsonObject()
            .put("id", mid.getString("id"))
            .put("action", "enable")
        );

    given()
        .header("Content-Type", "application/json")
        .body(installOp.encode())
        .post("/_/proxy/tenants/" + tenant + "/install?async=true&parallel=0")
        .then().statusCode(400)
        .body(containsString("parallel must be 1 or higher"));

    RestAssuredClient c = api.createRestAssured3();
    String location = c.given()
        .header("Content-Type", "application/json")
        .body(installOp.encode())
        .post("/_/proxy/tenants/" + tenant + "/install?async=true&parallel=5")
        .then().statusCode(201)
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    JsonObject job = pollCompleteStrip(context, location);
    JsonObject jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", moda.getString("id"))
                .put("action", "enable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", modb.getString("id"))
                .put("action", "enable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", mid.getString("id"))
                .put("action", "enable")
                .put("stage", "done")
            )
        );
    context.assertEquals(jobExpected, job);
    // moda , modb running in parallel
    context.assertTrue(tModule.getStartTime(moda.getString("id")).isBefore(tModule.getEndTime(modb.getString("id"))));
    context.assertTrue(tModule.getStartTime(modb.getString("id")).isBefore(tModule.getEndTime(moda.getString("id"))));
    // midb comes after
    context.assertTrue(tModule.getEndTime(moda.getString("id")).isBefore(tModule.getStartTime(mid.getString("id"))));
    context.assertTrue(tModule.getEndTime(modb.getString("id")).isBefore(tModule.getStartTime(mid.getString("id"))));

    installOp = new JsonArray()
        .add(new JsonObject()
            .put("id", moda.getString("id"))
            .put("action", "disable")
        )
        .add(new JsonObject()
            .put("id", modb.getString("id"))
            .put("action", "disable")
        )
        .add(new JsonObject()
            .put("id", mid.getString("id"))
            .put("action", "disable")
        );
    location = given()
        .header("Content-Type", "application/json")
        .body(installOp.encode())
        .post("/_/proxy/tenants/" + tenant + "/install?async=true&parallel=5")
        .then().statusCode(201)
        .extract().header("Location");
    job = pollCompleteStrip(context, location);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", mid.getString("id"))
                .put("action", "disable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", moda.getString("id"))
                .put("action", "disable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", modb.getString("id"))
                .put("action", "disable")
                .put("stage", "done")
            )
        );
    context.assertEquals(jobExpected, job);
    // mid disabled first
    context.assertTrue(tModule.getEndTime(mid.getString("id")).isBefore(tModule.getStartTime(moda.getString("id"))));
    context.assertTrue(tModule.getEndTime(mid.getString("id")).isBefore(tModule.getStartTime(modb.getString("id"))));
    // moda , modb running in parallel
    context.assertTrue(tModule.getStartTime(moda.getString("id")).isBefore(tModule.getEndTime(modb.getString("id"))));
    context.assertTrue(tModule.getStartTime(modb.getString("id")).isBefore(tModule.getEndTime(moda.getString("id"))));

    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void installParallelPermissions(TestContext context) {
    JsonObject moda = createAsyncInitModule("mod-a-1.0.0");
    moda.getJsonArray("provides")
        .add(new JsonObject()
            .put("id", "a")
            .put("version", "1.0")
        );
    given()
        .header("Content-Type", "application/json")
        .body(moda.encode()).post("/_/proxy/modules").then().statusCode(201);

    JsonObject modb = createAsyncInitModule("mod-b-1.0.0");
    modb.getJsonArray("provides")
        .add(new JsonObject()
            .put("id", "b")
            .put("version", "1.0")
        );
    given()
        .header("Content-Type", "application/json")
        .body(modb.encode()).post("/_/proxy/modules").then().statusCode(201);

    JsonObject modp = createAsyncInitModule("mod-p-1.0.0");
    modp.getJsonArray("provides")
        .add(new JsonObject()
            .put("id", "_tenantPermissions")
            .put("interfaceType", "system")
            .put("version", "1.0")
            .put("handlers", new JsonArray()
                .add(new JsonObject()
                    .put("methods", new JsonArray().add("POST"))
                    .put("pathPattern", "/permissions")
                    .put("permissionsRequired", new JsonArray())
                )
            )
        );
    given()
        .header("Content-Type", "application/json")
        .body(modp.encode()).post("/_/proxy/modules").then().statusCode(201);

    JsonObject mid = createAsyncInitModule("mod-m-1.0.0");
    mid.getJsonArray("requires")
        .add(new JsonObject()
            .put("id", "a")
            .put("version", "1.0")
        )
        .add(new JsonObject()
            .put("id", "b")
            .put("version", "1.0")
        );
    given()
        .header("Content-Type", "application/json")
        .body(mid.encode()).post("/_/proxy/modules").then().statusCode(201);

    final String tenant = "roskilde";
    createTenant(context, tenant);
    deployAsyncInitModule(context, moda.getString("id"), portModule);
    deployAsyncInitModule(context, modb.getString("id"), portModule);
    deployAsyncInitModule(context, modp.getString("id"), portModule);
    deployAsyncInitModule(context, mid.getString("id"), portModule);
    ModuleTenantInitAsync tModule = new ModuleTenantInitAsync(vertx, portModule);
    tModule.start().onComplete(context.asyncAssertSuccess());

    JsonArray installOp = new JsonArray()
        .add(new JsonObject()
            .put("id", moda.getString("id"))
            .put("action", "enable")
        )
        .add(new JsonObject()
            .put("id", modb.getString("id"))
            .put("action", "enable")
        )
        .add(new JsonObject()
            .put("id", modp.getString("id"))
            .put("action", "enable")
        )
        .add(new JsonObject()
            .put("id", mid.getString("id"))
            .put("action", "enable")
        );

    given()
        .header("Content-Type", "application/json")
        .body(installOp.encode())
        .post("/_/proxy/tenants/" + tenant + "/install?async=true&parallel=0")
        .then().statusCode(400)
        .body(containsString("parallel must be 1 or higher"));

    RestAssuredClient c = api.createRestAssured3();
    String location = c.given()
        .header("Content-Type", "application/json")
        .body(installOp.encode())
        .post("/_/proxy/tenants/" + tenant + "/install?async=true&parallel=5")
        .then().statusCode(201)
        .extract().header("Location");
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    JsonObject job = pollCompleteStrip(context, location);
    JsonObject jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", moda.getString("id"))
                .put("action", "enable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", modb.getString("id"))
                .put("action", "enable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", modp.getString("id"))
                .put("action", "enable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", mid.getString("id"))
                .put("action", "enable")
                .put("stage", "done")
            )
        );
    context.assertEquals(jobExpected, job);
    // moda , modb running in parallel
    context.assertTrue(tModule.getStartTime(moda.getString("id")).isBefore(tModule.getEndTime(modb.getString("id"))));
    context.assertTrue(tModule.getStartTime(modb.getString("id")).isBefore(tModule.getEndTime(moda.getString("id"))));
    // modp comes after
    context.assertTrue(tModule.getEndTime(moda.getString("id")).isBefore(tModule.getStartTime(modp.getString("id"))));
    context.assertTrue(tModule.getEndTime(modb.getString("id")).isBefore(tModule.getStartTime(modp.getString("id"))));
    // mid comes after
    context.assertTrue(tModule.getEndTime(modp.getString("id")).isBefore(tModule.getStartTime(mid.getString("id"))));

    installOp = new JsonArray()
        .add(new JsonObject()
            .put("id", moda.getString("id"))
            .put("action", "disable")
        )
        .add(new JsonObject()
            .put("id", modb.getString("id"))
            .put("action", "disable")
        )
        .add(new JsonObject()
            .put("id", modp.getString("id"))
            .put("action", "disable")
        )
        .add(new JsonObject()
            .put("id", mid.getString("id"))
            .put("action", "disable")
        );
    location = given()
        .header("Content-Type", "application/json")
        .body(installOp.encode())
        .post("/_/proxy/tenants/" + tenant + "/install?async=true&parallel=5")
        .then().statusCode(201)
        .extract().header("Location");
    job = pollCompleteStrip(context, location);
    jobExpected = new JsonObject()
        .put("complete", true)
        .put("modules", new JsonArray()
            .add(new JsonObject()
                .put("id", modp.getString("id"))
                .put("action", "disable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", mid.getString("id"))
                .put("action", "disable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", moda.getString("id"))
                .put("action", "disable")
                .put("stage", "done")
            )
            .add(new JsonObject()
                .put("id", modb.getString("id"))
                .put("action", "disable")
                .put("stage", "done")
            )
        );
    context.assertEquals(jobExpected, job);
    // modp disabled first
    context.assertTrue(tModule.getEndTime(modp.getString("id")).isBefore(tModule.getStartTime(mid.getString("id"))));
    // mid disabled next
    context.assertTrue(tModule.getEndTime(mid.getString("id")).isBefore(tModule.getStartTime(moda.getString("id"))));
    context.assertTrue(tModule.getEndTime(mid.getString("id")).isBefore(tModule.getStartTime(modb.getString("id"))));
    // moda , modb running in parallel
    context.assertTrue(tModule.getStartTime(moda.getString("id")).isBefore(tModule.getEndTime(modb.getString("id"))));
    context.assertTrue(tModule.getStartTime(modb.getString("id")).isBefore(tModule.getEndTime(moda.getString("id"))));

    tModule.stop().onComplete(context.asyncAssertSuccess());
  }

}
