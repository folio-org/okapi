package org.folio.okapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import com.google.common.base.Charsets;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.MetricsUtil;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ProxyTest {

  private final Logger logger = OkapiLogger.get();
  private Vertx vertx;
  private HttpClient httpClient;
  private static final String LS = System.lineSeparator();
  private static final String CORS_TEST_HEADER = "CORS_TEST_HEADER";
  private final int portTimer = 9235;
  private final int portPre = 9236;
  private final int portPost = 9237;
  private final int portEdge = 9238;
  private final int portHealth = 9239;
  private final int port = 9230;
  private Buffer preBuffer;
  private Buffer postBuffer;
  private MultiMap postHandlerHeaders;
  private static RamlDefinition api;
  private int timerDelaySum = 0;
  private int timerTenantInitStatus = 200;
  private int timerTenantPermissionsStatus = 200;
  private HttpServer listenTimer;
  private JsonObject timerPermissions = new JsonObject();
  private JsonArray edgePermissionsAtInit = null;

  @Rule
  public TestWatcher watchman = new TestWatcher() {
    @Override
    public void starting(final Description method) {
      logger.info("being run..." + method.getMethodName());
    }
  };

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  private void myPreHandle(RoutingContext ctx) {
    logger.info("myPreHandle!");
    preBuffer = Buffer.buffer();
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> HttpResponse.responseText(ctx, 204).end());
    } else {
      ctx.response().setStatusCode(200);
      ctx.response().putHeader("Content-Type", ctx.request().getHeader("Content-Type"));
      ctx.response().putHeader("Content-Encoding", "gzip");
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
    httpClient.request(HttpMethod.GET, port, "localhost", "/testb/1").onComplete(req1 -> {
      if (req1.failed()) {
        ctx.response().setStatusCode(500);
        ctx.response().end();
        return;
      }
      HttpClientRequest req = req1.result();
      req.putHeader("X-Okapi-Token", token);
      req.end();
      req.response(res1 -> {
        if (res1.failed()) {
          ctx.response().setStatusCode(500);
          ctx.response().end();
          return;
        }
        HttpClientResponse res = res1.result();
        Buffer resBuf = Buffer.buffer();
        res.handler(resBuf::appendBuffer);
        res.endHandler(res2 -> {
          ctx.response().setStatusCode(res.statusCode());
          ctx.response().end(resBuf);
        });
      });
    });
  }

  private void myEdgeHandle(RoutingContext ctx) {
    logger.info("myEdgeHandle");
    String p = ctx.request().path();
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> HttpResponse.responseText(ctx, 204).end());
    } else if (HttpMethod.POST.equals(ctx.request().method()) && p.equals("/_/tenant")) {
      edgePermissionsAtInit = timerPermissions.getJsonArray("edge-module-1.0.0");
      ctx.request().endHandler(x -> HttpResponse.responseText(ctx, 200).end());
    } else if (HttpMethod.GET.equals(ctx.request().method())) {
      Buffer buf = Buffer.buffer();
      ctx.request().handler(buf::appendBuffer);
      ctx.request().endHandler(res -> {
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
        httpClient.request(HttpMethod.POST, port, "localhost", "/authn/login").onComplete(req1 -> {
          if (req1.failed()) {
            ctx.response().setStatusCode(500);
            ctx.response().end();
            return;
          }
          HttpClientRequest request = req1.result();
          request.putHeader("Content-Type", "application/json")
              .putHeader("Accept", "application/json")
              .putHeader("X-Okapi-Tenant", tenant);
          request.end(docLogin);
          request.response(res1 -> {
            if (res1.failed()) {
              ctx.response().setStatusCode(500);
              ctx.response().end();
              return;
            }
            HttpClientResponse response = res1.result();
            Buffer loginBuf = Buffer.buffer();
            response.handler(loginBuf::appendBuffer);
            response.endHandler(x -> {
              if (response.statusCode() != 200) {
                ctx.response().setStatusCode(response.statusCode());
                ctx.response().end(loginBuf);
              } else {
                myEdgeCallTest(ctx, response.getHeader("X-Okapi-Token"));
              }
            });
          });
        });
      });
    } else {
      ctx.response().setStatusCode(404);
      ctx.response().end("Unsupported method");
    }
  }

  private void myTimerHandle(RoutingContext ctx) {
    HttpServerRequest request = ctx.request();
    HttpServerResponse response = ctx.response();
    final String p = request.path();
    if (HttpMethod.DELETE.equals(request.method())) {
      request.endHandler(x -> HttpResponse.responseText(ctx, 204).end());
    } else if (HttpMethod.POST.equals(request.method())) {
      if (p.startsWith("/echo")) {
        response.setStatusCode(200);
        response.putHeader("Content-Type", request.getHeader("Content-Type"));
        String contentEncoding = request.getHeader("Content-Encoding");
        if (contentEncoding != null) {
          response.putHeader("Content-Encoding", contentEncoding);
        }
        response.setChunked(true);
        Pump pump = Pump.pump(request, response);
        pump.start();
        request.endHandler(e -> response.end());
        request.pause();
        vertx.setTimer(100, x -> request.resume()); // pause to provoke writeQueueFull()
        return;
      }
      Buffer buf = Buffer.buffer();
      request.handler(buf::appendBuffer);
      request.endHandler(res -> {
        try {
          if (p.startsWith("/_/tenantpermissions")) {
            logger.info("returning 200 in myTimerHandle");
            response.setStatusCode(200);
            response.end();
          } else if (p.startsWith("/_/tenant")) {
            response.setStatusCode(timerTenantInitStatus);
            response.end("timer response");
          } else if (p.startsWith("/permissionscall")) {
            JsonObject permObject = new JsonObject(buf);
            if (timerTenantPermissionsStatus == 200) {
              timerPermissions.put(permObject.getString("moduleId"), permObject.getJsonArray("perms"));
            }
            response.setStatusCode(timerTenantPermissionsStatus);
            response.end("timer permissions response");
          } else if (p.startsWith("/timercall/")) {
            long delay = Long.parseLong(p.substring(11)); // assume /timercall/[0-9]+
            timerDelaySum += delay;
            vertx.setTimer(delay, x -> {
              response.setStatusCode(200);
              response.end();
            });
          } else if (p.startsWith("/regularcall")) {
            response.end(extractSubFromToken(ctx));
          } else if (p.startsWith("/corscall")) {
            response.putHeader(CORS_TEST_HEADER, request.query());
            response.end("Response from CORS test");
          } else {
            response.setStatusCode(200);
            response.end(p);
          }
        } catch (Exception ex) {
          logger.info("400 in timerHandle... method={} p={} Buffer={}",
              request.method().name(), p, buf.toString(Charsets.UTF_8));
          response.setStatusCode(400);
          response.end(ex.getMessage());
        }
      });
    } else if (HttpMethod.GET.equals(request.method())) {
      response.setStatusCode(200);
      response.end("");
    } else {
      response.setStatusCode(404);
      response.end("Unsupported method");
    }
  }

  Future<Void> startPreServer() {
    Router router = Router.router(vertx);

    router.routeWithRegex("/.*").handler(this::myPreHandle);

    Promise<Void> promise = Promise.promise();
    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(portPre, x -> promise.handle(x.mapEmpty()));
    return promise.future();
  }

  Future<Void> startPostServer() {
    Router router = Router.router(vertx);

    router.routeWithRegex("/.*").handler(this::myPostHandle);

    Promise<Void> promise = Promise.promise();
    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(portPost, x -> promise.handle(x.mapEmpty()));
    return promise.future();
  }

  Future<Void> startTimerServer() {
    Router router = Router.router(vertx);

    router.routeWithRegex("/.*").handler(this::myTimerHandle);

    Promise<Void> promise = Promise.promise();
    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    listenTimer = vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(portTimer, x -> promise.handle(x.mapEmpty()));
    return promise.future();
  }

  Future<Void> startEdgeServer() {
    Router router = Router.router(vertx);

    router.routeWithRegex("/.*").handler(this::myEdgeHandle);

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    Promise<Void> promise = Promise.promise();
    vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(portEdge, x -> promise.handle(x.mapEmpty()));
    return promise.future();
  }

  Future<Void> startOkapi() {
    DeploymentOptions opt = new DeploymentOptions()
        .setConfig(new JsonObject()
            .put("loglevel", "info")
            .put("port", Integer.toString(port))
            .put("healthPort", Integer.toString(portHealth))
            .put("httpCache", true));
    Promise<Void> promise = Promise.promise();
    vertx.deployVerticle(MainVerticle.class.getName(), opt, x -> promise.handle(x.mapEmpty()));
    return promise.future();
  }

  @Before
  public void setUp(TestContext context) {
    VertxOptions vopt = new VertxOptions();
    MetricsUtil.init(vopt);
    vertx = Vertx.vertx(vopt);
    httpClient = vertx.createHttpClient();

    timerTenantInitStatus = 200;
    RestAssured.port = port;

    Future<Void> future = Future.succeededFuture()
        .compose(x -> startOkapi())
        .compose(x -> startEdgeServer())
        .compose(x -> startTimerServer())
        .compose(x -> startPreServer())
        .compose(x -> startPostServer());
    future.onComplete(context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    Async async = context.async();
    httpClient.request(HttpMethod.DELETE, port, "localhost", "/_/discovery/modules",
        context.asyncAssertSuccess(request -> {
          request.end();
          request.response(context.asyncAssertSuccess(response -> {
            context.assertEquals(204, response.statusCode());
            response.endHandler(x -> {
              httpClient.close();
              async.complete();
            });
          }));
        }));
    async.await();
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testHealth(TestContext context) {
    given().port(portHealth).get("/readiness").then().statusCode(204);
    given().port(portHealth).get("/liveness").then().statusCode(204);
  }

  @Test
  public void testBadToken(TestContext context) {

    given()
        .header("X-Okapi-Token", "a")
        .header("Content-Type", "application/json")
        .get("/_/proxy/modules")
        .then().statusCode(400)
        .body(containsString("Invalid Token: "));

    given()
        .header("X-Okapi-Token", "a.b.c")
        .header("Content-Type", "application/json")
        .get("/_/proxy/modules")
        .then().statusCode(400)
        .body(equalTo("Invalid Token: Input byte[] should at least have 2 bytes for base64 bytes"));

    given()
        .header("X-Okapi-Token", "a.ewo=.d")
        .header("Content-Type", "application/json")
        .get("/_/proxy/modules")
        .then().statusCode(400)
        .body(containsString("Unexpected end-of-input"));
  }

  @Test
  public void testUpload(TestContext context) {
    final String tenant = "roskilde";

    setupBasicTenant(tenant);

    final String docTimer_1_0_0 = "{" + LS
        + "  \"id\" : \"timer-module-1.0.0\"," + LS
        + "  \"name\" : \"timer module\"," + LS
        + "  \"provides\" : [ {" + LS
        + "    \"id\" : \"echo\"," + LS
        + "    \"version\" : \"1.0\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"POST\" ]," + LS
        + "      \"pathPattern\" : \"/echo\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  } ]," + LS
        + "  \"requires\" : [ ]" + LS
        + "}";
    given()
        .header("Content-Type", "application/json")
        .body(docTimer_1_0_0).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();

    final String nodeDoc1 = "{" + LS
        + "  \"instId\" : \"localhost-1\"," + LS
        + "  \"srvcId\" : \"timer-module-1.0.0\"," + LS
        + "  \"url\" : \"http://localhost:" + portTimer + "\"" + LS
        + "}";

    given().header("Content-Type", "application/json")
        .body(nodeDoc1).post("/_/discovery/modules")
        .then().statusCode(201);

    final String docRequestPre = "{" + LS
        + "  \"id\" : \"request-pre-1.0.0\"," + LS
        + "  \"name\" : \"request-pre\"," + LS
        + "  \"filters\" : [ {" + LS
        + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
        + "    \"path\" : \"/echo\"," + LS
        + "    \"phase\" : \"pre\"," + LS
        + "    \"type\" : \"request-log\"," + LS
        + "    \"permissionsRequired\" : [ ]" + LS
        + "  } ]" + LS
        + "}";
    given()
        .header("Content-Type", "application/json")
        .body(docRequestPre).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();

    final String nodeDoc2 = "{" + LS
        + "  \"instId\" : \"localhost-2\"," + LS
        + "  \"srvcId\" : \"request-pre-1.0.0\"," + LS
        + "  \"url\" : \"http://localhost:" + portTimer + "\"" + LS
        + "}";

    given().header("Content-Type", "application/json")
        .body(nodeDoc2).post("/_/discovery/modules")
        .then().statusCode(201);

    given()
        .header("Content-Type", "application/json")
        .body(new JsonArray().add(new JsonObject().put("id", "timer-module-1.0.0").put("action", "enable"))
            .add(new JsonObject().put("id", "request-pre-1.0.0").put("action", "enable")).encode())
        .post("/_/proxy/tenants/" + tenant + "/install?deploy=true&invoke=true")
        .then().statusCode(200);

    upload(context, tenant, "/echo", 0);

    given().delete("/_/proxy/tenants/" + tenant + "/modules").then().statusCode(204);
    given().delete("/_/discovery/modules").then().statusCode(204);
    given().delete("/_/proxy/modules/timer-module-1.0.0").then().statusCode(204);
    given().delete("/_/proxy/modules/request-pre-1.0.0").then().statusCode(204);
    given().delete("/_/proxy/tenants/" + tenant).then().statusCode(204);
  }

  private void upload(TestContext context, String tenant, String uri, int offset) {
    Async async = context.async();
    int bufSz = 10000;
    long bufCnt = 1000;
    long total = bufSz * bufCnt;
    logger.info("Sending {} GB", total / 1e9);

    httpClient.request(HttpMethod.POST, port, "localhost", uri, context.asyncAssertSuccess(request -> {
      request.response(context.asyncAssertSuccess(res -> {
        context.assertEquals(200, res.statusCode());
        AtomicLong cnt = new AtomicLong();
        res.handler(h -> cnt.addAndGet(h.length()));
        res.exceptionHandler(ex -> {
          context.fail(ex.getCause());
          async.complete();
        });
        res.endHandler(end -> {
          context.assertEquals(total + offset, cnt.get());
          async.complete();
        });
      }));
      request.putHeader("X-Okapi-Tenant", tenant);
      request.putHeader("Content-Type", "text/plain");
      request.putHeader("Accept", "text/plain");
      request.setChunked(true);
      Buffer buffer = Buffer.buffer();
      for (int j = 0; j < bufSz; j++) {
        buffer.appendString("X");
      }
      endRequest(request, buffer, 0, bufCnt);
    }));
    async.await(30000);
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
    c.given()
      .header("Content-Type", "application/json")
      .body(docTenantRoskilde).post("/_/proxy/tenants")
      .then().statusCode(201)
      .body(equalTo(docTenantRoskilde));
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
      + "      \"pathPattern\" : \"/_/tenant/disable\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myxfirst\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/testb/client_id\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"mysecond\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\"]," + LS
      + "      \"pathPattern\" : \"/testb/{id}\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"optional\" : [ { \"id\" : \"optional-foo\", \"version\": \"1.0\"} ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"exec\" : "
      + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
      + "  }" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docBasic_1_0_0).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    /* missing action so this will fail */
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"basic-module-1.0.0\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(400)
      .body(equalTo("Missing action for id basic-module-1.0.0"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"basic-module-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200)
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
      .then().statusCode(200)
      .extract().response();
    Assert.assertTrue(r.body().asString().contains("X-Okapi-Match-Path-Pattern:/testb/{id}"));

    c = api.createRestAssured3();
    r = c.given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-all-headers", "B")
      .get("/testb/client_id")
      .then().statusCode(200)
      .extract().response();
    Assert.assertTrue(r.body().asString().contains("X-Okapi-Match-Path-Pattern:/testb/client_id"));

    c = api.createRestAssured3();
    r = c.given()
        .header("X-Okapi-Tenant", okapiTenant)
        .header("X-all-headers", "B")
        .get("/testb/client_id%2Fx")
        .then().statusCode(200)
        .extract().response();
    Assert.assertTrue(r.body().asString().contains("X-Okapi-Match-Path-Pattern:/testb/{id}"));

    upload(context, okapiTenant, "/testb/client_id", 6);

    c = api.createRestAssured3();
    c.given()
        .header("X-Okapi-Tenant", okapiTenant)
        .get("/testb/client_id/x")
        .then().statusCode(404);

    given().delete("/_/proxy/tenants/" + okapiTenant + "/modules").then().statusCode(204);
    given().delete("/_/discovery/modules").then().statusCode(204);
  }

  private void endRequest(HttpClientRequest req, Buffer buffer, long i, long cnt) {
    if (i == cnt) {
      req.end();
    } else {
      req.write(buffer, res -> endRequest(req, buffer, i + 1, cnt));
    }
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
    c.given()
      .header("Content-Type", "application/json")
      .body(docTenantRoskilde).post("/_/proxy/tenants")
      .then().statusCode(201)
      .body(equalTo(docTenantRoskilde));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docAuthModule).post("/_/proxy/modules")
        .then().statusCode(201);
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
      + "      \"pathPattern\" : \"/_/tenant/disable\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\"]," + LS
      + "      \"pathPattern\" : \"/testb/{id}\"," + LS
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
    c.given()
      .header("Content-Type", "application/json")
      .body(docBasic_1_0_0).post("/_/proxy/modules").then().statusCode(201);
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
      .then().statusCode(200)
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
      .then().statusCode(200)
      .extract().response();
    Assert.assertEquals("It works", r.getBody().asString());

    // token with implied tenant
    c = api.createRestAssured3();
    r = c.given()
      .header("X-Okapi-Token", okapiToken)
      .get("/testb/hugo")
      .then().statusCode(200)
      .extract().response();
    Assert.assertEquals("It works", r.getBody().asString());

    c = api.createRestAssured3();
    r = c.given()
      .header("X-all-headers", "B")
      .header("X-Okapi-Token", okapiToken)
      .header("X-Okapi-Additional-Token", "dummyJwt")
      .get("/testb/hugo")
      .then().statusCode(200)
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
      .then().statusCode(400);
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
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"*\" ]," + LS
      + "    \"path\" : \"/\"," + LS
      + "    \"phase\" : \"auth\"," + LS
      + "    \"type\" : \"request-response\"," + LS
      + "    \"permissionsRequired\" : [ ]," + LS
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

    // PUT not defined for RAML
    given()
      .header("Content-Type", "application/json")
      .body("{ \"bad Json\" ").put(locationAuthModule).then().statusCode(404);

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
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"*\" ]," + LS
      + "    \"path\" : \"/\"," + LS
      + "    \"level\" : \"10\"," + LS
      + "    \"type\" : \"request-response\"," + LS
      + "    \"permissionsRequired\" : [ ]," + LS
      + "    \"permissionsDesired\" : [ \"auth.extra\" ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]" + LS
      + "}";

    c = api.createRestAssured3();
    final String locationAuthModule2 = c.given()
      .header("Content-Type", "application/json")
      .body(docAuthModule2).post("/_/proxy/modules")
      .then().statusCode(201)
      .extract().header("Location");
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
      .then()
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
      .then().statusCode(404).body(equalTo("No suitable module found for path /testb for tenant supertenant"));

    // Request with a header, to unknown path
    // (note, should fail without invoking the auth module)
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .get("/something.we.do.not.have")
      .then().statusCode(404)
      .body(equalTo("No suitable module found for path /something.we.do.not.have for tenant roskilde"));

    // Request without an auth token
    // In theory, this is acceptable, we should get back a token that certifies
    // that we have no logged-in username. We can use this for modulePermissions
    // still. A real auth module would be likely to refuse the request because
    // we do not have the necessary ModulePermissions.
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-all-headers", "B") // ask sample to report all headers
      .get("/testb")
      .then()
      .statusCode(401);

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
      .header("X-Okapi-Url", "http://localhost:9230") // no trailing slash!
      .header("X-Okapi-User-Id", "peter")
      .header("X-Url-Params", "query=foo&limit=10")
      .header("X-Okapi-Permissions", containsString("sample.extra"))
      .header("X-Okapi-Permissions", containsString("auth.extra"))
      .header("X-Auth-Permissions-Desired", containsString("auth.extra"))
      .header("X-Auth-Permissions-Desired", containsString("sample.extra"))
      .header("X-Auth-Permissions-Required", "sample.needed")
      .body(containsString("It works"));

    //CAM
    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("X-all-headers", "HBL") // ask sample to report all headers
      .header("X-Okapi-User-Id", "peter")
      .get("/testb?query=foo&limit=10")
      .then().statusCode(200)
      .header("X-Okapi-Url", "http://localhost:9230") // no trailing slash!
      .header("X-Okapi-User-Id", "peter")
      .header("X-Url-Params", "query=foo&limit=10")
      .header("X-Okapi-Permissions", containsString("sample.extra"))
      .header("X-Okapi-Permissions", containsString("auth.extra"))
      .body(containsString("It works"));

    // Check the CORS headers.
    // The presence of the Origin header should provoke the two extra headers.
    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Origin", "http://foobar.com")
      .get("/testb")
      .then().statusCode(200)
      .header("Access-Control-Allow-Origin", "*")
      .header("Access-Control-Expose-Headers", startsWithIgnoringCase(
        "Location,X-Okapi-Trace,X-Okapi-Token,Authorization,X-Okapi-Request-Id"))
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
      .body(equalTo("It works Tenant requests: POST-roskilde-auth "));

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
      .then()
      .header("X-Okapi-Tenant", okapiTenant)
      .statusCode(200);
    // Note that we can not check the token, the module sees a different token,
    // created by the auth module, when it saw a ModulePermission for the sample
    // module. This is all right, since we explicitly ask sample to pass its
    // request headers into its response. See Okapi-266.

    // Check that we accept Authorization without lead  "Bearer" <token>
    // instead of X-Okapi-Token, and that we can extract the tenant from it.
    given()
      .header("X-all-headers", "H") // ask sample to report all headers
      .header("Authorization", okapiToken)
      .get("/testb")
      .then()
      .header("X-Okapi-Tenant", okapiTenant)
      .statusCode(200);

    // Check that we fail on conflicting X-Okapi-Token and Auth tokens
    given().header("X-all-headers", "H") // ask sample to report all headers
      .header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Authorization", "Bearer " + okapiToken + "WRONG")
      .get("/testb")
      .then()
      .statusCode(400);

    // Check that we fail on invalid Token/Authorization
    given().header("X-all-headers", "H") // ask sample to report all headers
      .header("X-Okapi-Token", "xx")
      .header("Authorization", "Bearer xx")
      .get("/testb")
      .then()
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
      + "    \"type\" : \"request-response\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
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
      .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // and its instance
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/sample-module2-1/sample2-inst")
      .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Get with unknown instanceId AND serviceId
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/foo/xyz")
      .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Get with unknown instanceId
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/sample-module2-1/xyz")
      .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Get with unknown serviceId
    c = api.createRestAssured3();
    c.given().get("/_/discovery/modules/foo/sample2-inst")
      .then().statusCode(404);
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
      .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // health with unknown serviceId
    c = api.createRestAssured3();
    c.given().get("/_/discovery/health/foo/sample2-inst")
      .then().statusCode(404);
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
      + "    \"type\" : \"headers\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"level\" : \"45\"," + LS
      + "    \"type\" : \"headers\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"level\" : \"33\"," + LS
      + "    \"type\" : \"request-only\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
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
    final String docSample3Deployment = "{" + LS
      + "  \"instId\" : \"sample3-instance\"," + LS
      + "  \"srvcId\" : \"sample-module3-1\"," + LS
      + "  \"url\" : \"http://localhost:9232\"" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json")
      .body(docSample3Deployment).post("/_/discovery/modules")
      .then()
      .statusCode(201).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    final String locationSample3Inst = r.getHeader("Location");
    logger.debug("Deployed: locationSample3Inst " + locationSample3Inst);

    // same instId but different module .. must result in error
    final String docSample3DeploymentError = "{" + LS
      + "  \"instId\" : \"sample3-instance\"," + LS
      + "  \"srvcId\" : \"sample-module2-1\"," + LS
      + "  \"url\" : \"http://localhost:9232\"" + LS
      + "}";
    c.given()
      .header("Content-Type", "application/json")
      .body(docSample3DeploymentError).post("/_/discovery/modules")
      .then()
      .statusCode(400).body(equalTo("Duplicate instId sample3-instance"));

    final String docEnableSample3 = "{" + LS
      + "  \"id\" : \"sample-module3-1\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEnableSample3).post("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(201)
      .header("Location", equalTo("/_/proxy/tenants/" + okapiTenant + "/modules/sample-module3-1"))
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
      .body(containsString("POST-roskilde-auth POST-roskilde-auth"));

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
      .then().statusCode(200);
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
      .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "text/xml")
      .get("/testb")
      .then().statusCode(404); // because sample2 was removed

    // Disable the sample module. No tenant-destroy for sample
    c = api.createRestAssured3();
    c.given()
      .delete("/_/proxy/tenants/" + okapiTenant + "/modules/sample-module-1?purge=true")
      .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // Disable the sample2 module + auth-1. It has a tenant request handler which is
    // no longer invoked, so it does not matter we don't have a running instance

    c = api.createRestAssured3();
    c.given()
      .delete("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    given()
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).body(equalTo("[ ]"));

    c = api.createRestAssured3();
    c.given()
      .delete("/_/proxy/tenants/unknown-tenant/modules")
      .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().delete(locationTenantRoskilde)
      .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // Clean up, so the next test starts with a clean slate
    given().delete(locationSample3Inst).then().statusCode(204);
    given().delete(locationSample3Module).then().statusCode(204);
    given().delete("/_/proxy/modules/sample-module-1").then().statusCode(204);
    given().delete("/_/proxy/modules/sample-module2-1").then().statusCode(204);
    given().delete("/_/proxy/modules/auth-1").then().statusCode(204);
    given().delete(locationAuthDeployment).then().statusCode(204);
    locationAuthDeployment = null;
    given().delete(locationSampleDeployment).then().statusCode(204);
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
      + "      \"path\" : \"/testb\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testr\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/loop2\"," + LS
      + "    \"level\" : \"22\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/loop1\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  }, {" + LS
      + "    \"modulePermissions\" : [ \"sample.modperm\" ]," + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/chain3\"," + LS
      + "    \"level\" : \"23\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/testr\"," + LS
      + "    \"permissionsRequired\" : [ ]," + LS
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
      + "    \"redirectPath\" : \"/testr\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/badredirect\"," + LS
      + "    \"level\" : \"22\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/nonexisting\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/simpleloop\"," + LS
      + "    \"level\" : \"23\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/simpleloop\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/loop1\"," + LS
      + "    \"level\" : \"24\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/loop2\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  }, {" + LS
      + "    \"modulePermissions\" : [ \"hdr.modperm\" ]," + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/chain1\"," + LS
      + "    \"level\" : \"25\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/chain2\"," + LS
      + "    \"permissionsRequired\" : [ ]," + LS
      + "    \"permissionsDesired\" : [ \"hdr.chain1\" ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"GET\" ]," + LS
      + "    \"path\" : \"/chain2\"," + LS
      + "    \"level\" : \"26\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/chain3\"," + LS
      + "    \"permissionsRequired\" : [ ]," + LS
      + "    \"permissionsDesired\" : [ \"hdr.chain2\" ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"POST\" ]," + LS
      + "    \"path\" : \"/multiple\"," + LS
      + "    \"level\" : \"27\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/testr\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  }, {" + LS
      + "    \"methods\" : [ \"POST\" ]," + LS
      + "    \"path\" : \"/multiple\"," + LS
      + "    \"level\" : \"28\"," + LS
      + "    \"type\" : \"redirect\"," + LS
      + "    \"redirectPath\" : \"/testr\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
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

    // redirect to multiple modules, but only one is executed
    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("Content-Type", "application/json")
      .body("{}")
      .post("/multiple")
      .then().statusCode(200)
      .body(containsString("Hello {}")) // test-module run once
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
        + "    \"type\" : \"request-log\"," + LS
        + "    \"permissionsRequired\" : [ ]" + LS
        + "  } ]" + LS
        + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docRequestPre).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docRequestPost = "{" + LS
      + "  \"id\" : \"request-post-1.0.0\"," + LS
      + "  \"name\" : \"request-post\"," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "    \"path\" : \"/testb\"," + LS
      + "    \"phase\" : \"post\"," + LS
      + "    \"type\" : \"request-log\"," + LS
      + "    \"permissionsRequired\" : [ ]" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docRequestPost).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String docRequestOnly = "{" + LS
        + "  \"id\" : \"request-only-1.0.0\"," + LS
        + "  \"name\" : \"request-only\"," + LS
        + "  \"filters\" : [ {" + LS
        + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
        + "    \"path\" : \"/testb\"," + LS
        + "    \"level\" : \"30\"," + LS
        + "    \"type\" : \"request-only\"," + LS
        + "    \"permissionsRequired\" : [ ]" + LS
        + "  } ]," + LS
        + "  \"launchDescriptor\" : {" + LS
        + "    \"exec\" : "
        + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
        + "  }" + LS
        + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docRequestOnly).post("/_/proxy/modules").then().statusCode(201);
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
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docAuthModule).post("/_/proxy/modules").then().statusCode(201);
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
        + "      \"path\" : \"/testb\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  } ]," + LS
        + "  \"launchDescriptor\" : {" + LS
        + "    \"exec\" : "
        + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
        + "  }" + LS
        + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docSample).post("/_/proxy/modules").then().statusCode(201);
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

    Async async = context.async();
    vertx.setTimer(300, res -> {
      context.assertEquals("Okapi", preBuffer.toString());
      context.assertEquals("<test>Hello Okapi</test>", postBuffer.toString());
      context.assertNotNull(postHandlerHeaders);
      context.assertEquals("200", postHandlerHeaders.get(XOkapiHeaders.HANDLER_RESULT));
      async.complete();
    });
    async.await();

    // same as sample-1.0.0 but with request-response-1.0
    final String docSample2 = "{" + LS
        + "  \"id\" : \"sample-module-1.0.1\"," + LS
        + "  \"name\" : \"sample-module\"," + LS
        + "  \"provides\" : [ {" + LS
        + "    \"id\" : \"_tenant\"," + LS
        + "    \"version\" : \"1.0\"" + LS
        + "  }, {" + LS
        + "    \"id\" : \"myfirst\"," + LS
        + "    \"version\" : \"1.0\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"GET\", \"POST\", \"DELETE\"]," + LS
        + "      \"path\" : \"/testb\"," + LS
        + "      \"type\" : \"request-response-1.0\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  } ]," + LS
        + "  \"launchDescriptor\" : {" + LS
        + "    \"exec\" : "
        + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
        + "  }" + LS
        + "}";
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(docSample2).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"sample-module-1.0.1\", \"action\" : \"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
        .then().statusCode(200).log().ifValidationFails()
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"sample-module-1.0.1\"," + LS
            + "  \"from\" : \"sample-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
        .header("X-Okapi-Token", okapiToken)
        .header("Content-Type", "text/plain")
        .header("Accept", "text/xml")
        .body("Okapi").post("/testb")
        .then().statusCode(200).log().ifValidationFails()
        .header("Content-Type", "text/xml")
        .body(equalTo("<test>Hello Okapi</test>"));

    Async async2 = context.async();
    vertx.setTimer(300, res -> {
      context.assertEquals("Okapi", preBuffer.toString());
      context.assertEquals("<test>Hello Okapi</test>", postBuffer.toString());
      context.assertNotNull(postHandlerHeaders);
      context.assertEquals("200", postHandlerHeaders.get(XOkapiHeaders.HANDLER_RESULT));
      async2.complete();
    });
    async2.await();
  }

  @Test
  public void testEdgeCase(TestContext context) {
    RestAssuredClient c;
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

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docAuthModule).post("/_/proxy/modules").then().statusCode(201);
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
      + "      \"pathPattern\" : \"/_/tenant/disable\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/_/tenant\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/testb/{id}\"," + LS
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
    c.given()
      .header("Content-Type", "application/json")
      .body(docBasic_1_0_0).post("/_/proxy/modules").then().statusCode(201);
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
      + "      \"pathPattern\" : \"/edge/{id}\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEdge_1_0_0).post("/_/proxy/modules").then().statusCode(201);
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

    final String docLogin = "{" + LS
      + "  \"tenant\" : \"" + "supertenant" + "\"," + LS
      + "  \"username\" : \"peter\"," + LS
      + "  \"password\" : \"peter-password\"" + LS
      + "}";
    final String okapiToken = c.given().header("Content-Type", "application/json").body(docLogin)
      .header("X-Okapi-Tenant", "supertenant").post("/authn/login")
      .then().statusCode(200).extract().header("X-Okapi-Token");

    c = api.createRestAssured3();
    given()
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "application/json")
      .get("/_/proxy/tenants")
      .then().statusCode(200);

    final String okapiTenant = "roskilde";
    // add tenant
    final String docTenantRoskilde = "{" + LS
      + "  \"id\" : \"" + okapiTenant + "\"," + LS
      + "  \"name\" : \"" + okapiTenant + "\"," + LS
      + "  \"description\" : \"Roskilde bibliotek\"" + LS
      + "}";
    c = api.createRestAssured3();
    given()
      .header("X-Okapi-Token", okapiToken)
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
      .header("X-Okapi-Token", okapiToken)
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"basic-module-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"auth-module-1.0.0\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails();

    c.given()
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .body("Okapi").get("/edge/roskilde")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("It works"));

    c.given()
      .header("Content-Type", "text/plain")
      .header("Accept", "text/xml")
      .body("Okapi").get("/edge/unknown")
      .then().statusCode(400).log().ifValidationFails()
      .body(equalTo("No such Tenant unknown"));

    c.given()
        .header("X-Okapi-Token", okapiToken)
        .delete("/_/proxy/tenants/supertenant/modules/edge-module-1.0.0").then().statusCode(204);

    c.given()
        .header("X-Okapi-Token", okapiToken)
        .delete("/_/proxy/tenants/supertenant/modules/basic-module-1.0.0").then().statusCode(204);

    c.given()
        .header("X-Okapi-Token", okapiToken)
        .delete("/_/proxy/tenants/supertenant/modules/auth-module-1.0.0").then().statusCode(204);

    c.given()
      .delete("/_/discovery/modules")
      .then().statusCode(204).log().ifValidationFails();
  }

  @Test
  public void testTimer(TestContext context) {
    RestAssuredClient c;

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
      + "   }, {" + LS
      + "      \"methods\" : [ \"GET\" ]," + LS
      + "      \"path\" : \"/timercall/5\"," + LS
      + "      \"schedule\" : {" + LS
      + "         \"cron\" : \"1 1 1 1 *\"" + LS
      + "      }" + LS
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
      .body(docTimer_1_0_0).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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

    final String okapiTenant = "roskilde";

    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/" + okapiTenant + "/timers")
        .then().statusCode(200)
        .body("$", hasSize(0));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // add tenant
    final String docTenantRoskilde = "{" + LS
      + "  \"id\" : \"" + okapiTenant + "\"," + LS
      + "  \"name\" : \"" + okapiTenant + "\"," + LS
      + "  \"description\" : \"Roskilde bibliotek\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docTenantRoskilde).post("/_/proxy/tenants")
      .then().statusCode(201)
      .body(equalTo(docTenantRoskilde));

    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/" + okapiTenant + "/timers")
        .then().statusCode(200)
        .body("$", hasSize(0));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    timerDelaySum = 0;
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    try {
      TimeUnit.MILLISECONDS.sleep(100);
    } catch (InterruptedException ex) {
    }

    // n in timerDelaySum. 10 ms wait in between
    logger.info("timerDelaySum=" + timerDelaySum);
    context.assertTrue(timerDelaySum >= 3 && timerDelaySum <= 30, "Got " + timerDelaySum);

    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/" + okapiTenant + "/timers/foo")
        .then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // not even part of RAML / MD
    given()
        .get("/_/proxy/tenants/" + okapiTenant + "/timers/a/b/c")
        .then().statusCode(404);

    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/" + okapiTenant + "/timers")
        .then().statusCode(200)
        .body("$", hasSize(3));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/" + okapiTenant + "/timers/timer-module_0")
        .then().statusCode(200)
        .body("id", is("timer-module_0"))
        .body("modified", is(false));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    given()
        .header("Content-Type", "application/json")
        .body("{ bad")
        .patch("/_/proxy/tenants/" + okapiTenant + "/timers")
        .then().statusCode(400);

    given()
        .header("Content-Type", "application/json")
        .body("{ bad")
        .patch("/_/proxy/tenants/" + okapiTenant + "/timers/extra")
        .then().statusCode(400);

    JsonObject routingEntry = new JsonObject()
                .put("unit", "millisecond")
                .put("delay", "2");

    JsonObject patchObj = new JsonObject()
        .put("id", "timer-module_0")
        .put("routingEntry", routingEntry);

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(patchObj.encode())
        .patch("/_/proxy/tenants/" + okapiTenant + "/timers")
            .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // patch same data again
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(routingEntry.encode())
        .patch("/_/proxy/tenants/" + okapiTenant + "/timers/timer-module_0")
        .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/" + okapiTenant + "/timers/timer-module_0")
        .then().statusCode(200)
        .body("routingEntry.delay", is("2"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    given()
        .header("X-Okapi-Tenant", okapiTenant)
        .header("Content-Type", "text/plain")
        .header("Accept", "text/plain")
        .body("Okapi").post("/timercall/10")
        .then().statusCode(200);

    // disable the timer
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(new JsonObject()
            .put("unit", "millisecond")
            .put("delay", "0").encode())
        .patch("/_/proxy/tenants/" + okapiTenant + "/timers/timer-module_0")
        .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    timerDelaySum = 0;
    try {
      TimeUnit.MILLISECONDS.sleep(20);
    } catch (InterruptedException ex) {
    }
    logger.info("timerDelaySum=" + timerDelaySum);
    context.assertEquals(0, timerDelaySum);

    // enable it again
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(new JsonObject()
            .put("id", "timer-module_0")
            .put("routingEntry", new JsonObject()
                .put("unit", "millisecond")
                .put("delay", "2")).encode())
        .patch("/_/proxy/tenants/" + okapiTenant + "/timers")
        .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // disable and enable (quickly)
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"timer-module-1.0.0\", \"action\" : \"disable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // reset timer
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body("{}")
        .patch("/_/proxy/tenants/" + okapiTenant + "/timers/timer-module_0")
        .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // see that values are back to that of module
    c = api.createRestAssured3();
    c.given()
        .get("/_/proxy/tenants/" + okapiTenant + "/timers/timer-module_0")
        .then().statusCode(200)
        .body("id", is("timer-module_0"))
        .body("routingEntry.unit", is("millisecond"))
        .body("routingEntry.delay", is("10"))
        .body("modified", is(false));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // disable for some time...
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"timer-module-1.0.0\", \"action\" : \"disable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails();

    try {
      TimeUnit.MILLISECONDS.sleep(100);
    } catch (InterruptedException ex) {
    }

    // enable again
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    try {
      TimeUnit.MILLISECONDS.sleep(100);
    } catch (InterruptedException ex) {
    }

    // disable and remove tenant as well
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"timer-module-1.0.0\", \"action\" : \"disable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    given()
      .header("Content-Type", "application/json")
      .delete("/_/proxy/tenants/roskilde")
      .then().statusCode(204);

    try {
      TimeUnit.MILLISECONDS.sleep(40);
    } catch (InterruptedException ex) {
    }

    timerDelaySum = 0;
    // add tenant 2nd time
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(docTenantRoskilde).post("/_/proxy/tenants")
        .then().statusCode(201)
        .body(equalTo(docTenantRoskilde));
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body("["
            + " {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"}"
            + "]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
        .then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    try {
      TimeUnit.MILLISECONDS.sleep(50);
    } catch (InterruptedException ex) {
    }
    logger.info("timerDelaySum=" + timerDelaySum);
    context.assertTrue(timerDelaySum > 0, timerDelaySum + " > 0");

    // disable and remove tenant as well
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body("["
            + " {\"id\" : \"timer-module-1.0.0\", \"action\" : \"disable\"}"
            + "]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
        .then().statusCode(200).log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    given()
        .header("Content-Type", "application/json")
        .delete("/_/proxy/tenants/roskilde")
        .then().statusCode(204);
  }

  @Test
  public void testTenantInit(TestContext context) {
    RestAssuredClient c;
    Response r;

    timerPermissions.clear();

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
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
      + "      \"permissionsRequired\" : [ \"timercall.post.id\" ]" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
      + "      \"permissionsRequired\" : [ \"timercall.delete.id\" ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"permissionSets\": [ {" + LS
      + "    \"permissionName\": \"timercall.post.id\"" + LS
      + "  } ]" + LS
      + "}";

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docTimer_1_0_0).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

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

    c = api.createRestAssured3();
    String body = c.given().header("Content-Type", "application/json")
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).extract().body().asString();
    JsonArray ar = new JsonArray(body);
    Assert.assertEquals(0, ar.size());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(200).log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    Assert.assertEquals(0, timerPermissions.size());
    c = api.createRestAssured3();
    body = c.given().header("Content-Type", "application/json")
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).extract().body().asString();
    ar = new JsonArray(body);
    Assert.assertEquals(1, ar.size());
    Assert.assertEquals("timer-module-1.0.0", ar.getJsonObject(0).getString("id"));

    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("Content-Type", "text/plain")
      .header("Accept", "text/plain")
      .body("Okapi").post("/timercall/1")
      .then().statusCode(200).log().ifValidationFails();

    final String docTimer_1_0_1 = "{" + LS
      + "  \"id\" : \"timer-module-1.0.1\"," + LS
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
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
      + "      \"permissionsRequired\" : [ \"timercall.post.id\" ]" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
      + "      \"permissionsRequired\" : [ \"timercall.delete.id\" ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"permissionSets\": [ {" + LS
      + "    \"permissionName\": \"timercall.post.id\"," + LS
      + "    \"displayName\": \"d\"" + LS
      + "  } ]" + LS
      + "}";

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docTimer_1_0_1).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    Assert.assertEquals(0, timerPermissions.size());

    final String docEdge_1_0_0 = "{" + LS
        + "  \"id\" : \"edge-module-1.0.0\"," + LS
        + "  \"name\" : \"edge module\"," + LS
        + "  \"provides\" : [ {" + LS
        + "    \"id\" : \"_tenant\"," + LS
        + "    \"version\" : \"1.1\"," + LS
        + "    \"interfaceType\" : \"system\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"POST\" ]," + LS
        + "      \"pathPattern\" : \"/_/tenant\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  }, {" + LS
        + "    \"id\" : \"edge\"," + LS
        + "    \"version\" : \"1.0\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
        + "      \"pathPattern\" : \"/edge/{id}\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  } ]," + LS
        + "  \"requires\" : [ ]," + LS
        + "  \"permissionSets\": [ {" + LS
        + "    \"permissionName\": \"edge.post.id\"," + LS
        + "    \"displayName\": \"e\"" + LS
        + "  } ]" + LS
        + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docEdge_1_0_0).post("/_/proxy/modules").then().statusCode(201);
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

    Assert.assertEquals(0, timerPermissions.size());

    // deploy, but with no running instance of timer-module-1.0.1
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"edge-module-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"timer-module-1.0.1\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(400).log().ifValidationFails()
      .body(containsString("No running instances for module timer-module-1.0.1. Can not invoke /_/tenant"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    Assert.assertEquals(0, timerPermissions.size());

    final String nodeDoc2 = "{" + LS
      + "  \"instId\" : \"localhost1-" + Integer.toString(portTimer) + "\"," + LS
      + "  \"srvcId\" : \"timer-module-1.0.1\"," + LS
      + "  \"url\" : \"http://localhost:" + Integer.toString(portTimer) + "\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(nodeDoc2).post("/_/discovery/modules")
      .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).body(containsString("timer-module-1.0.0"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    timerTenantInitStatus = 400;
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"edge-module-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"timer-module-1.0.1\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(400).log().ifValidationFails()
      .body(containsString("POST request for timer-module-1.0.1 /_/tenant failed with 400: timer response"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    Assert.assertEquals(0, timerPermissions.size());

    c = api.createRestAssured3();
    body = c.given().header("Content-Type", "application/json")
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).extract().body().asString();
    ar = new JsonArray(body);
    Assert.assertEquals(2, ar.size());
    Assert.assertEquals("edge-module-1.0.0", ar.getJsonObject(0).getString("id"));
    Assert.assertEquals("timer-module-1.0.0", ar.getJsonObject(1).getString("id"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    timerTenantInitStatus = 200;
    timerTenantPermissionsStatus = 400;
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"edge-module-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"timer-module-1.0.1\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(400).log().ifValidationFails()
      .body(containsString("POST request for timer-module-1.0.1 "
        +"/permissionscall failed with 400: timer permissions response"));
     Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    Assert.assertEquals(0, timerPermissions.size());

    c = api.createRestAssured3();
    body = c.given().header("Content-Type", "application/json")
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).extract().body().asString();
    ar = new JsonArray(body);
    Assert.assertEquals(2, ar.size());
    Assert.assertEquals("edge-module-1.0.0", ar.getJsonObject(0).getString("id"));
    Assert.assertEquals("timer-module-1.0.0", ar.getJsonObject(1).getString("id"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    timerTenantPermissionsStatus = 200;
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"edge-module-1.0.0\", \"action\" : \"enable\"},"
        + " {\"id\" : \"timer-module-1.0.1\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(200).log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    Assert.assertEquals(timerPermissions.encodePrettily(), 2, timerPermissions.size());
    Assert.assertTrue(timerPermissions.containsKey("timer-module-1.0.1"));
    Assert.assertTrue(timerPermissions.containsKey("timer-module-1.0.1"));

    // re-enable edge-module and check that permissions for it are available at tenant-init
    timerPermissions.clear();
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body("["
            + " {\"id\" : \"edge-module-1.0.0\", \"action\" : \"disable\"}"
            + "]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(200).log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body("["
            + " {\"id\" : \"edge-module-1.0.0\", \"action\" : \"enable\"}"
            + "]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install")
        .then().statusCode(200).log().ifValidationFails();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
    Assert.assertNotNull(edgePermissionsAtInit);

    given()
      .header("X-Okapi-Tenant", okapiTenant)
      .header("Content-Type", "text/plain")
      .header("Accept", "text/plain")
      .body("Okapi").post("/timercall/1")
      .then().statusCode(200).log().ifValidationFails();


    final String docTimer_1_0_2 = "{" + LS
      + "  \"id\" : \"timer-module-1.0.2\"," + LS
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
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
      + "      \"permissionsRequired\" : [ \"timercall.post.id\" ]" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
      + "      \"permissionsRequired\" : [ \"timercall.delete.id\" ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"permissionSets\": [ {" + LS
      + "    \"permissionName\": \"timercall.post.id\"," + LS
      + "    \"displayName\": \"d\"" + LS
      + "  } ]" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docTimer_1_0_2).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String nodeDoc3 = "{" + LS
      + "  \"instId\" : \"localhost2-" + Integer.toString(portTimer) + "\"," + LS
      + "  \"srvcId\" : \"timer-module-1.0.2\"," + LS
      + "  \"url\" : \"http://localhost:" + Integer.toString(portTimer) + "\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(nodeDoc3).post("/_/discovery/modules")
      .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    timerTenantPermissionsStatus = 400;

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("["
        + " {\"id\" : \"timer-module\", \"action\" : \"enable\"}"
        + "]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(400).log().ifValidationFails()
      .body(containsString("POST request for timer-module-1.0.2 "
        +"/permissionscall failed with 400: timer permissions response"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

  }

  @Test
  public void testTenantFailedUpgrade(TestContext context) {
    RestAssuredClient c;

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

    final String docTimer_1_0_0 = "{" + LS
      + "  \"id\" : \"timer-module-1.0.0\"," + LS
      + "  \"name\" : \"timer module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
      + "      \"permissionsRequired\" : [ \"timercall.post.id\" ]" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
      + "      \"permissionsRequired\" : [ \"timercall.delete.id\" ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"permissionSets\": [ {" + LS
      + "    \"permissionName\": \"timercall.post.id\"" + LS
      + "  } ]" + LS
      + "}";

    final String docBusiness_1_0_0 = "{" + LS
      + "  \"id\" : \"business-module-1.0.0\"," + LS
      + "  \"name\" : \"business module\"," + LS
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
      + "  \"requires\" : [ { \"id\" : \"myint\", \"version\" : \"1.0\" } ]," + LS
      + "  \"permissionSets\": [ {" + LS
      + "    \"permissionName\": \"timercall.post.id\"" + LS
      + "  } ]" + LS
      + "}";

    final String docTimer_2_0_0 = "{" + LS
      + "  \"id\" : \"timer-module-2.0.0\"," + LS
      + "  \"name\" : \"timer module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"myint\"," + LS
      + "    \"version\" : \"2.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
      + "      \"permissionsRequired\" : [ \"timercall.post.id\" ]" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"DELETE\" ]," + LS
      + "      \"pathPattern\" : \"/timercall/{id}\"," + LS
      + "      \"permissionsRequired\" : [ \"timercall.delete.id\" ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"requires\" : [ ]," + LS
      + "  \"permissionSets\": [ {" + LS
      + "    \"permissionName\": \"timercall.post.id\"" + LS
      + "  } ]" + LS
      + "}";

    final String docBusiness_2_0_0 = "{" + LS
      + "  \"id\" : \"business-module-2.0.0\"," + LS
      + "  \"name\" : \"business module\"," + LS
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
      + "  \"requires\" : [ { \"id\" : \"myint\", \"version\" : \"2.0\" } ]," + LS
      + "  \"permissionSets\": [ {" + LS
      + "    \"permissionName\": \"timercall.post.id\"" + LS
      + "  } ]" + LS
      + "}";

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docTimer_1_0_0).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String deployDocTimer_1_0_0 = "{" + LS
      + "  \"instId\" : \"localhost-1-" + Integer.toString(portTimer) + "\"," + LS
      + "  \"srvcId\" : \"timer-module-1.0.0\"," + LS
      + "  \"url\" : \"http://localhost:" + Integer.toString(portTimer) + "\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(deployDocTimer_1_0_0).post("/_/discovery/modules")
      .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docBusiness_1_0_0).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String deployDocBusiness_1_0_0 = "{" + LS
      + "  \"instId\" : \"localhost-2-" + Integer.toString(portTimer) + "\"," + LS
      + "  \"srvcId\" : \"business-module-1.0.0\"," + LS
      + "  \"url\" : \"http://localhost:" + Integer.toString(portTimer) + "\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(deployDocBusiness_1_0_0).post("/_/discovery/modules")
      .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docTimer_2_0_0).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String deployDocTimer_2_0_0 = "{" + LS
      + "  \"instId\" : \"localhost-3-" + Integer.toString(portTimer) + "\"," + LS
      + "  \"srvcId\" : \"timer-module-2.0.0\"," + LS
      + "  \"url\" : \"http://localhost:" + Integer.toString(portTimer) + "\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(deployDocTimer_2_0_0).post("/_/discovery/modules")
      .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docBusiness_2_0_0).post("/_/proxy/modules").then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    final String deployDocBusiness_2_0_0 = "{" + LS
      + "  \"instId\" : \"localhost-4-" + Integer.toString(portTimer) + "\"," + LS
      + "  \"srvcId\" : \"business-module-2.0.0\"," + LS
      + "  \"url\" : \"http://localhost:" + Integer.toString(portTimer) + "\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(deployDocBusiness_2_0_0).post("/_/discovery/modules")
      .then().statusCode(201);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"business-module-1.0.0\", \"action\" : \"enable\"}, {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"timer-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"business-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade?simulate=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"timer-module-2.0.0\"," + LS
        + "  \"from\" : \"timer-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"business-module-2.0.0\"," + LS
        + "  \"from\" : \"business-module-1.0.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    /* this will try to upgrade both, but business-module-2.0.0 tenant init fails,
     so business-module-1.0.0 stays but timer-module is upgrade (no _tenant interface) */
    timerTenantInitStatus = 400; // make business-module tenant init fail
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .post("/_/proxy/tenants/" + okapiTenant + "/upgrade")
      .then().statusCode(400).log().ifValidationFails()
      .body(equalTo("POST request for business-module-2.0.0 /_/tenant failed with 400: timer response"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"business-module-1.0.0\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"timer-module-2.0.0\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"timer-module-2.0.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + okapiTenant + "/install?simulate=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"business-module-1.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "}, {" + LS
        + "  \"id\" : \"timer-module-2.0.0\"," + LS
        + "  \"action\" : \"disable\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // works, because timer-module-2.0.0 does not have tenant interface
    c = api.createRestAssured3();
    c.given()
      .delete("/_/proxy/tenants/" + okapiTenant + "/modules/timer-module-2.0.0")
      .then().statusCode(204).log().ifValidationFails();
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/tenants/" + okapiTenant + "/modules")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"business-module-1.0.0\"" + LS
        + "} ]"));
    Assert.assertTrue(
      "raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // disable also fails with 400
    Assert.assertEquals(400, timerTenantInitStatus);
    c = api.createRestAssured3();
    c.given()
        .delete("/_/proxy/tenants/" + okapiTenant + "/modules")
        .then().statusCode(400).log().ifValidationFails()
        .body(equalTo("POST request for business-module-1.0.0 /_/tenant/disable failed with 400: timer response"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // however, purge does not
    c = api.createRestAssured3();
    c.given()
        .delete("/_/proxy/tenants/" + okapiTenant + "/modules?purge=true")
        .then().statusCode(204).log().ifValidationFails();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // tenant init fails, but is not called because in next tests invoke=false
    Assert.assertEquals(400, timerTenantInitStatus);
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body("[ {\"id\" : \"business-module-1.0.0\", \"action\" : \"enable\"}, {\"id\" : \"timer-module-1.0.0\", \"action\" : \"enable\"} ]")
        .post("/_/proxy/tenants/" + okapiTenant + "/install?invoke=false")
        .then().statusCode(200).log().ifValidationFails()
        .body(equalTo("[ {" + LS
            + "  \"id\" : \"timer-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"business-module-1.0.0\"," + LS
            + "  \"action\" : \"enable\"" + LS
            + "} ]"));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
        .delete("/_/proxy/tenants/" + okapiTenant + "/modules?invoke=false")
        .then().statusCode(204).log().ifValidationFails();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
  }

  @Test
  public void testProxyClientFailure(TestContext context) {
    String tenant = "test-tenant-permissions-tenant";
    setupBasicTenant(tenant);

    String moduleId = "module-1.0.0";
    setupBasicModule(tenant, moduleId, "1.1", false, false, false);
    RestAssuredClient c = api.createRestAssured3();

    String body = new JsonObject().put("id", "test").encode();
    c.given()
        .header("Content-Type", "application/json")
        .header("X-Okapi-Tenant", tenant)
        .body(body)
        .post("/regularcall")
        .then()
        .statusCode(200)
        .log().ifValidationFails();

    // shut down listener for module so proxy client fails
    Async async = context.async();
    listenTimer.close().onComplete(x -> async.complete());
    async.await();

    c.given()
        .header("Content-Type", "application/json")
        .header("X-Okapi-Tenant", tenant)
        .body(body)
        .post("/regularcall")
        .then()
        .statusCode(500)
        .log().ifValidationFails().assertThat().body(containsString("proxyClient failure"));

    given().delete("/_/proxy/tenants/" + tenant + "/modules").then().statusCode(400);
    given().delete("/_/discovery/modules").then().statusCode(204);
    given().delete("/_/proxy/modules/" + moduleId).then().statusCode(400);
    given().delete("/_/proxy/tenants/" + tenant).then().statusCode(204);
  }

  @Test
  public void testTenantPermissionsUpgrade() {
    String tenant = "test-tenant-permissions-tenant";
    setupBasicTenant(tenant);

    String moduleA0 = "moduleA-1.0.0";
    timerPermissions.clear();
    setupBasicOther(tenant, moduleA0, "ainterface");
    Assert.assertEquals(0, timerPermissions.size());

    String moduleId0 = "perm-1.0.0";
    setupBasicModule(tenant, moduleId0, "1.0", false, true, false);
    Assert.assertEquals(2, timerPermissions.size());
    Assert.assertTrue(timerPermissions.containsKey(moduleId0));
    Assert.assertTrue(timerPermissions.containsKey(moduleA0));
    Assert.assertEquals("ainterface.post",
        timerPermissions.getJsonArray(moduleA0).getJsonObject(0).getString("permissionName"));

    String moduleId1 = "perm-1.0.1";
    setupBasicModule(tenant, moduleId1, "1.0", false, true, true);
    Assert.assertEquals(3, timerPermissions.size());
    Assert.assertTrue(timerPermissions.containsKey(moduleId0));
    Assert.assertTrue(timerPermissions.containsKey(moduleA0));
    Assert.assertTrue(timerPermissions.containsKey(moduleId1));

    String moduleA1 = "moduleA-1.0.1";
    setupBasicOther(tenant, moduleA1, "binterface");
    Assert.assertEquals(4, timerPermissions.size());
    Assert.assertTrue(timerPermissions.containsKey(moduleA1));
    Assert.assertEquals("binterface.post",
        timerPermissions.getJsonArray(moduleA1).getJsonObject(0).getString("permissionName"));

    given().delete("/_/proxy/tenants/" + tenant + "/modules").then().statusCode(204);
    given().delete("/_/discovery/modules").then().statusCode(204);
    given().delete("/_/proxy/modules/" + moduleA0).then().statusCode(204);
    given().delete("/_/proxy/modules/" + moduleA1).then().statusCode(204);
    given().delete("/_/proxy/modules/" + moduleId0).then().statusCode(204);
    given().delete("/_/proxy/modules/" + moduleId1).then().statusCode(204);
    given().delete("/_/proxy/tenants/" + tenant).then().statusCode(204);
  }

  private boolean hasPermReplaces(JsonArray permissions) {
    for (int i=0; i<permissions.size(); i++) {
      if (permissions.getJsonObject(i).getJsonArray("replaces") != null) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void testTenantPermissionsVersion() {
    String tenant = "test-tenant-permissions-tenant";
    String moduleId = "test-tenant-permissions-basic-module-1.0.0";
    String authModuleId = "test-tenant-permissions-auth-module-1.0.0";
    String body = new JsonObject().put("id", "test").encode();

    setupBasicTenant(tenant);

    // test _tenantpermissions 1.0 vs 1.1 vs 2.0
    for (String tenantPermissionsVersion : Arrays.asList("1.0", "1.1", "2.0")) {
      timerPermissions.clear();
      setupBasicModule(tenant, moduleId, tenantPermissionsVersion, true, true, true);
      setupBasicAuth(tenant, authModuleId);

      JsonArray permissions = timerPermissions.getJsonArray(moduleId);
      // system generates permission sets for 1.1 version
      if (tenantPermissionsVersion.equals("1.0")) {
        Assert.assertEquals(2, permissions.size());
        Assert.assertFalse(hasPermReplaces(permissions));
      } else if (tenantPermissionsVersion.equals("1.1")) {
        Assert.assertEquals(5, permissions.size());
        Assert.assertFalse(hasPermReplaces(permissions));
      } else {
        Assert.assertEquals(5, permissions.size());
        Assert.assertTrue(hasPermReplaces(permissions));
      }
      // proxy calls
      RestAssuredClient c = api.createRestAssured3();
      Response r = c.given()
        .header("Content-Type", "application/json")
        .header("X-Okapi-Token", getOkapiToken(tenant))
        .body(body).post("/regularcall")
        .then().statusCode(200).log().ifValidationFails()
        .extract().response();
      if (tenantPermissionsVersion.equals("1.0")) {
        Assert.assertFalse(r.getBody().asString().contains("SYS#"));
      } else {
        Assert.assertTrue(r.getBody().asString().contains("SYS#"));
      }
      // system calls
      given()
        .header("X-Okapi-Tenant", tenant)
        .header("Content-Type", "text/plain")
        .header("Accept", "text/plain")
        .body("Okapi").post("/timercall/10")
        .then().statusCode(200).log().ifValidationFails();
      // clean up
      given().delete("/_/proxy/tenants/" + tenant + "/modules").then().statusCode(204);
      given().delete("/_/discovery/modules").then().statusCode(204);
      given().delete("/_/proxy/modules/" + moduleId).then().statusCode(204);
    }

    // remove testing tenant
    given().delete("/_/proxy/tenants/" + tenant).then().statusCode(204);
  }

  @Test
  public void testDelegateCORS() {
    String tenant = "test-tenant-delegate-cors";
    String moduleId = "test-tenant-delegate-cors-module-1.0.0";
    String authModuleId = "test-tenant-delegate-cors-auth-module-1.0.0";
    String body = new JsonObject().put("id", "test").encode();

    setupBasicTenant(tenant);
    setupBasicModule(tenant, moduleId, "1.1", false, false, false);
    setupBasicAuth(tenant, authModuleId);

    RestAssuredClient c = api.createRestAssured3();

    // no CORS delegate
    c.given()
      .header("Content-Type", "application/json")
      .header("X-Okapi-Token", getOkapiToken(tenant))
      .header("Origin", "http://localhost")
      .body(body)
      .post("/_/invoke/tenant/" + tenant + "/regularcall")
      .then()
      .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), notNullValue())
      .statusCode(200)
      .log().ifValidationFails();

    // with CORS delegate
    c.given()
      .header("Content-Type", "application/json")
      .header("X-Okapi-Token", getOkapiToken(tenant))
      .header("Origin", "http://localhost")
      .body(body)
      .post("/_/invoke/tenant/" + tenant + "/corscall")
      .then()
      .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), nullValue())
      .statusCode(200)
      .log().ifValidationFails();

    // with CORS delegate and query parameter
    c.given()
      .header("Content-Type", "application/json")
      .header("X-Okapi-Token", getOkapiToken(tenant))
      .header("Origin", "http://localhost")
      .body(body)
      .post("/_/invoke/tenant/" + tenant + "/corscall?x=y")
      .then()
      .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), nullValue())
      .header(CORS_TEST_HEADER, "x=y")
      .statusCode(200)
      .log().ifValidationFails();

    given().delete("/_/proxy/tenants/" + tenant + "/modules").then().statusCode(204);
    given().delete("/_/discovery/modules").then().statusCode(204);
    given().delete("/_/proxy/modules/" + moduleId).then().statusCode(204);
    given().delete("/_/proxy/tenants/" + tenant).then().statusCode(204);
  }

  // add basic tenant
  private void setupBasicTenant(String tenant) {
    String tenantJson = new JsonObject().put("id", tenant).encode();
    RestAssuredClient c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(tenantJson).post("/_/proxy/tenants")
      .then().statusCode(201);
  }

  // get X-Okapi-Token
  private String getOkapiToken(String tenant) {
    String loginJson = new JsonObject()
      .put("tenant",  tenant)
      .put("username", "peter")
      .put("password", "peter-password")
      .encode();
    RestAssuredClient c = api.createRestAssured3();
    return c.given()
      .header("Content-Type", "application/json")
      .header("X-Okapi-Tenant", tenant)
      .body(loginJson).post("/authn/login")
      .then().statusCode(200).extract().header("X-Okapi-Token");
  }

  // decode X-Okapi-Token
  private JsonObject decodeOkapiToken(String token) {
    String encodedJson = token.substring(token.indexOf(".") + 1, token.lastIndexOf("."));
    return new JsonObject(new String(Base64.getDecoder().decode(encodedJson)));
  }

  // extract sub from token
  private String extractSubFromToken(RoutingContext ctx) {
    String token = ctx.request().getHeader("X-Okapi-Token");
    if (token != null) {
      return decodeOkapiToken(token).getString("sub");
    }
    return "";
  }

  // add auth module
  private void setupBasicAuth(String tenant, String authModuleId) {
    String testAuthJar = "../okapi-test-auth-module/target/okapi-test-auth-module-fat.jar";
    String mdJson = new JsonObject()
      .put("id", authModuleId)
      .put("name", "auth")
      .put("provides", new JsonArray()
        .add(new JsonObject()
          .put("id", "auth")
          .put("version", "1.2")
          .put("handlers", new JsonArray()
            .add(new JsonObject()
              .put("methods", new JsonArray().add("POST"))
              .put("path", "/authn/login")
              .put("level", "20")
              .put("type", "request-response")
              .put("permissionsRequired", new JsonArray())))))
      .put("filters", new JsonArray()
        .add(new JsonObject()
          .put("methods", new JsonArray().add("*"))
          .put("path", "/")
          .put("phase", "auth")
          .put("type", "headers")
          .put("phase", "auth")
          .put("permissionsRequired", new JsonArray())))
      .put("requires", new JsonArray())
      .put("launchDescriptor", new JsonObject()
        .put("exec", "java -Dport=%p -jar " + testAuthJar))
      .encodePrettily();

    // registration
    RestAssuredClient c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(mdJson).post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();

    // deploy and enable
    String installJson = new JsonArray().add(new JsonObject()
      .put("id", authModuleId)
      .put("action", "enable"))
      .encode();
    c.given()
      .header("Content-Type", "application/json")
      .body(installJson)
      .post("/_/proxy/tenants/" + tenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails();
  }

  private void setupBasicOther(String tenant, String moduleId, String providedInterface) {
    String requiredPermission = providedInterface + ".post";
    String mdJson = new JsonObject()
        .put("id", moduleId)
        .put("provides", new JsonArray()
            .add(new JsonObject()
                .put("id", "_tenant")
                .put("version", "1.1")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/_/tenant/disable")
                        .put("permissionsRequired", new JsonArray()))
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST").add("DELETE"))
                        .put("pathPattern", "/_/tenant")
                        .put("permissionsRequired", new JsonArray()))))
            .add(new JsonObject()
                .put("id", providedInterface)
                .put("version", "1.0")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/regularcall/" + providedInterface)
                        .put("permissionsRequired", new JsonArray().add(requiredPermission))))))
        .put("requires", new JsonArray())
        .put("permissionSets", new JsonArray()
            .add(new JsonObject()
                .put("permissionName", requiredPermission)
                .put("displayName", "d")))
        .encodePrettily();

    // registration
    RestAssuredClient c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(mdJson).post("/_/proxy/modules")
        .then().statusCode(201).log().ifValidationFails();

    // discovery
    String discoveryJson = new JsonObject()
        .put("instId", "localhost-" + Integer.toString(portTimer) + moduleId)
        .put("srvcId", moduleId)
        .put("url", "http://localhost:" + Integer.toString(portTimer))
        .encode();
    c.given()
        .header("Content-Type", "application/json")
        .body(discoveryJson).post("/_/discovery/modules")
        .then().statusCode(201).log().ifValidationFails();

    // install
    String installJson = new JsonArray()
        .add(new JsonObject().put("id", moduleId).put("action", "enable"))
        .encode();
    c.given()
        .header("Content-Type", "application/json")
        .body(installJson)
        .post("/_/proxy/tenants/" + tenant + "/install")
        .then().statusCode(200).log().ifValidationFails();
  }

  // add basic module
  private void setupBasicModule(String tenant, String moduleId, String tenantPermissionsVersion,
      boolean provideTimer, boolean provideTenantPermissions, boolean doPermReplace) {
    JsonArray providesAr = new JsonArray();
    if (provideTimer) {
        providesAr.add(new JsonObject()
          .put("id", "_timer")
          .put("version", "1.0")
          .put("interfaceType", "system")
          .put("handlers", new JsonArray()
              .add(new JsonObject()
                  .put("methods", new JsonArray().add("POST"))
                  .put("pathPattern", "/timercall/1")
                  .put("unit", "millisecond")
                  .put("delay", "10")
                  .put("permissionsRequired", new JsonArray().add("timercall.post.id"))
                  .put("modulePermissions", new JsonArray().add("timercall.test.post")))
              .add(new JsonObject()
                  .put("methods", new JsonArray().add("DELETE"))
                  .put("pathPattern", "/timercall/{id}")
                  .put("permissionsRequired", new JsonArray().add("timercall.delete.id")))));
    }
    if (provideTenantPermissions) {
      providesAr.add(new JsonObject()
          .put("id", "_tenantPermissions")
          .put("version", tenantPermissionsVersion)
          .put("interfaceType", "system")
          .put("handlers", new JsonArray()
              .add(new JsonObject()
                  .put("methods", new JsonArray().add("POST"))
                  .put("pathPattern", "/permissionscall")
                  .put("permissionsRequired", new JsonArray()))));

    }
    JsonObject mdJsonObj = new JsonObject()
        .put("id", moduleId)
        .put("provides", providesAr
            .add(new JsonObject()
                .put("id", "_tenant")
                .put("version", "1.1")
                .put("interfaceType", "system")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/_/tenant/disable")
                        .put("permissionsRequired", new JsonArray()))
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST").add("DELETE"))
                        .put("pathPattern", "/_/tenant")
                        .put("permissionsRequired", new JsonArray()))))
            .add(new JsonObject()
                .put("id", "myint")
                .put("version", "1.0")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/timercall/{id}")
                        .put("permissionsRequired", new JsonArray())
                        .put("modulePermissions", new JsonArray().add("timercall.post.id").add("timercall.delete.id")))
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("DELETE"))
                        .put("pathPattern", "/timercall/{id}")
                        .put("permissionsRequired", new JsonArray().add("timercall.delete.id")))))
            .add(new JsonObject()
                .put("id", "mytest")
                .put("version", "1.0")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/regularcall")
                        .put("permissionsRequired", new JsonArray())
                        .put("modulePermissions", new JsonArray().add("regularcall.test.post")))))
            .add(new JsonObject()
                .put("id", "CORS-TEST")
                .put("version", "1.0")
                .put("handlers", new JsonArray()
                    .add(new JsonObject()
                        .put("methods", new JsonArray().add("POST"))
                        .put("pathPattern", "/corscall")
                        .put("permissionsRequired", new JsonArray())
                        .put("delegateCORS", "true")))))
        .put("requires", new JsonArray());

    JsonArray permSets = new JsonArray()
        .add(new JsonObject()
            .put("permissionName", "timercall.post.id")
            .put("displayName", "d"));

    if (doPermReplace) {
      permSets.add(new JsonObject()
          .put("permissionName", "regularcall.everything")
          .put("displayName", "All regularcall permissions")
          .put("replaces", new JsonArray()
              .add("regularcall.all"))
          .put("subPermissions", new JsonArray()
              .add("regularcall.test.post")));
    } else {
      permSets.add(new JsonObject()
          .put("permissionName", "regularcall.all")
          .put("displayName", "All regularcall permissions")
          .put("subPermissions", new JsonArray()
              .add("regularcall.test.post")));
    }
    String mdJson = mdJsonObj.put("permissionSets", permSets).encodePrettily();

    // registration
    RestAssuredClient c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(mdJson).post("/_/proxy/modules")
        .then().statusCode(201).log().ifValidationFails();

    // discovery
    String discoveryJson = new JsonObject()
        .put("instId", "localhost-" + Integer.toString(portTimer) + moduleId)
        .put("srvcId", moduleId)
        .put("url", "http://localhost:" + Integer.toString(portTimer))
        .encode();
    c.given()
        .header("Content-Type", "application/json")
        .body(discoveryJson).post("/_/discovery/modules")
        .then().statusCode(201).log().ifValidationFails();

    // install
    String installJson = new JsonArray()
        .add(new JsonObject().put("id", moduleId).put("action", "enable"))
        .encode();
    c.given()
        .header("Content-Type", "application/json")
        .body(installJson)
        .post("/_/proxy/tenants/" + tenant + "/install")
        .then().statusCode(200).log().ifValidationFails();
  }

  @Test
  public void testImportModules(TestContext context) {
    RestAssuredClient c;

    given()
        .header("Content-Type", "application/json")
        .body("{\"id\":").post("/_/proxy/import/modules")
        .then().statusCode(400).body(containsString("Cannot deserialize instance"));

    given()
        .header("Content-Type", "application/json")
        .body("[]").post("/_/proxy/import/modules?check=foo")
        .then().statusCode(400).body(equalTo("Bad boolean for parameter check: foo"));

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body("[]").post("/_/proxy/import/modules")
        .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    ModuleDescriptor mdA = new ModuleDescriptor();
    mdA.setId("moduleA-1.0.0");
    mdA.setProvidedHandler("intA", "1.0", new RoutingEntry("/a", "GET"));

    ModuleDescriptor mdB = new ModuleDescriptor();
    mdB.setId("moduleB-1.0.0");
    mdB.setRequires("intA", "1.0");
    List<ModuleDescriptor> modules = new LinkedList<>();
    modules.add(mdB);
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(Json.encodePrettily(modules)).post("/_/proxy/import/modules")
        .then().statusCode(400).body(equalTo("Missing dependency: moduleB-1.0.0 requires intA: 1.0"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // try again, but without checking .. therefore it should succeed
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(Json.encodePrettily(modules)).post("/_/proxy/import/modules?check=false&preRelease=false&npmSnapshot=false")
        .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // remove again..
    c = api.createRestAssured3();
    c.given().delete("/_/proxy/modules/" + mdB.getId()).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    modules.add(mdA);
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(Json.encodePrettily(modules)).post("/_/proxy/import/modules")
        .then().statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    ModuleDescriptor mdC = new ModuleDescriptor();
    mdC.setId("moduleC-1.0.0");
    {
      InterfaceDescriptor[] interfaceDescriptors = new InterfaceDescriptor[1];
      InterfaceDescriptor interfaceDescriptor = interfaceDescriptors[0] = new InterfaceDescriptor();
      interfaceDescriptor.setId("intA");
      // note no version set
      mdC.setRequires(interfaceDescriptors);
    }
    modules = new LinkedList<>();
    modules.add(mdC);

    given()
        .header("Content-Type", "application/json")
        .body(Json.encodePrettily(modules)).post("/_/proxy/import/modules")
        .then().statusCode(400).body(equalTo("version is missing for module moduleC-1.0.0"));
  }

  @Test
  public void testManyModules(TestContext context) throws IOException {
    given()
        .body("{\"id\":\"testlib\"}").post("/_/proxy/tenants")
        .then().statusCode(201);

    String modulesJson = new String(getClass().getClassLoader().getResourceAsStream("modules2.json").readAllBytes());
    JsonArray modulesList = new JsonArray(modulesJson);
    context.assertEquals(104, modulesList.size());

    String installJson = new String(getClass().getClassLoader().getResourceAsStream("install2.json").readAllBytes());
    JsonArray installList = new JsonArray(installJson);
    context.assertEquals(104, installList.size());

    // save all normal GET provided paths
    List<String> paths = new LinkedList<>();
    for (int i = 0; i < modulesList.size(); i++) {
      ModuleDescriptor md = Json.decodeValue(modulesList.getJsonObject(i).encode(), ModuleDescriptor.class);
      for (InterfaceDescriptor interfaceDescriptor : md.getProvidesList()) {
        String interfaceType = interfaceDescriptor.getInterfaceType();
        if (interfaceType == null || "proxy".equals(interfaceType)) {
          for (RoutingEntry routingEntry : interfaceDescriptor.getHandlers()) {
            String[] methods = routingEntry.getMethods();
            String type = routingEntry.getType();
            String pathPattern = routingEntry.getPathPattern();
            if (methods.length > 0 && "GET".equals(methods[0])
                && (type == null || "request-response".equals(type))
                && pathPattern != null) {

              pathPattern = pathPattern.replace('{', 'x');
              pathPattern = pathPattern.replace('}', 'x');

              paths.add(pathPattern);
            }
          }
        }
      }
    }

    given()
        .body(modulesJson).post("/_/proxy/import/modules")
        .then().statusCode(204);
    for (int i = 0; i < modulesList.size(); i++) {
      JsonObject md = modulesList.getJsonObject(i);
      JsonObject deployObject = new JsonObject()
          .put("instId", "localhost-" + md.getString("id"))
          .put("srvcId", md.getString("id"))
          .put("url", "http://localhost:" + Integer.toString(portTimer));
      given().body(deployObject.encode()).post("/_/discovery/modules")
          .then().statusCode(201).log().ifValidationFails();
    }

    given()
        .body(installJson).post("/_/proxy/tenants/testlib/install?invoke=.*")
        .then().statusCode(200);

    long startTime = System.nanoTime();
    for (int i = 0; i < 2; i++) {
      // run all paths that have GET
      for (String path : paths) {
        given().header("X-Okapi-Tenant", "testlib").get(path).then().statusCode(200);
      }
    }
    long endTime = System.nanoTime();
    logger.info("Elapsed {} ms", (endTime - startTime) / 1000000);
  }


  @Test
  public void testRequestResponse(TestContext context) {
    final String okapiTenant = "roskilde";
    RestAssuredClient c;
    Response r;

    // add tenant
    c = api.createRestAssured3();
    r = c.given()
        .header("Content-Type", "application/json")
        .body(new JsonObject().put("id", okapiTenant).encode()).post("/_/proxy/tenants")
        .then().statusCode(201)
        .extract().response();
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    final String docRequestPre = "{" + LS
        + "  \"id\" : \"module-pre-1.0.0\"," + LS
        + "  \"filters\" : [ {" + LS
        + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
        + "    \"path\" : \"/\"," + LS
        + "    \"phase\" : \"pre\"," + LS
        + "    \"type\" : \"request-response\"," + LS
        + "    \"permissionsRequired\" : [ ]" + LS
        + "  } ]" + LS
        + "}";
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(docRequestPre).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    String nodeDoc1 = "{" + LS
        + "  \"instId\" : \"localhost-1\"," + LS
        + "  \"srvcId\" : \"module-pre-1.0.0\"," + LS
        + "  \"url\" : \"http://localhost:" + Integer.toString(portPre) + "\"" + LS
        + "}";
    given().header("Content-Type", "application/json")
        .body(nodeDoc1).post("/_/discovery/modules")
        .then().statusCode(201);

    final String docRequestPost = "{" + LS
        + "  \"id\" : \"module-post-1.0.0\"," + LS
        + "  \"filters\" : [ {" + LS
        + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
        + "    \"path\" : \"/\"," + LS
        + "    \"phase\" : \"post\"," + LS
        + "    \"type\" : \"request-response\"," + LS
        + "    \"permissionsRequired\" : [ ]" + LS
        + "  } ]" + LS
        + "}";
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(docRequestPost).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    nodeDoc1 = "{" + LS
        + "  \"instId\" : \"localhost-2\"," + LS
        + "  \"srvcId\" : \"module-post-1.0.0\"," + LS
        + "  \"url\" : \"http://localhost:" + Integer.toString(portPost) + "\"" + LS
        + "}";
    given().header("Content-Type", "application/json")
        .body(nodeDoc1).post("/_/discovery/modules")
        .then().statusCode(201);

    final String docTimer_1_0_0 = "{" + LS
        + "  \"id\" : \"timer-module-1.0.0\"," + LS
        + "  \"name\" : \"timer module\"," + LS
        + "  \"provides\" : [ {" + LS
        + "    \"id\" : \"echo\"," + LS
        + "    \"version\" : \"1.0\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"POST\" ]," + LS
        + "      \"pathPattern\" : \"/echo\"," + LS
        + "      \"permissionsRequired\" : [ ]" + LS
        + "    } ]" + LS
        + "  } ]," + LS
        + "  \"requires\" : [ ]" + LS
        + "}";
    given()
        .header("Content-Type", "application/json")
        .body(docTimer_1_0_0).post("/_/proxy/modules").then().statusCode(201)
        .extract().response();

    nodeDoc1 = "{" + LS
        + "  \"instId\" : \"localhost-3\"," + LS
        + "  \"srvcId\" : \"timer-module-1.0.0\"," + LS
        + "  \"url\" : \"http://localhost:" + Integer.toString(portTimer) + "\"" + LS
        + "}";
    given().header("Content-Type", "application/json")
        .body(nodeDoc1).post("/_/discovery/modules")
        .then().statusCode(201);

    JsonArray installReq = new JsonArray().add(new JsonObject().put("id",  "timer-module-1.0.0").put("action", "enable"));
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(installReq.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
        .then().statusCode(200).log().ifValidationFails()
        .body(equalTo(installReq.encodePrettily()));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
        .header("Content-Type", "text/plain")
        .body("Okapi").post("/echo")
        .then().statusCode(200)
        .header("Content-Type", "text/plain; charset=ISO-8859-1")
        .header("Content-Encoding", nullValue())
        .body(equalTo("Okapi"));

    installReq = new JsonArray().add(new JsonObject().put("id",  "module-pre-1.0.0").put("action", "enable"));
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(installReq.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
        .then().statusCode(200).log().ifValidationFails()
        .body(equalTo(installReq.encodePrettily()));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
        .header("Content-Type", "text/plain; charset=UTF-8")
        .body("Okapi").post("/echo")
        .then().statusCode(200)
        .header("Content-Type", "text/plain; charset=UTF-8")
        .header("Content-Encoding", "gzip");

    given().header("X-Okapi-Tenant", okapiTenant)
        .body("Okapi").post("/echo")
        .then().statusCode(200)
        .header("Content-Type", "text/plain; charset=ISO-8859-1")
        .header("Content-Encoding", "gzip");

    installReq = new JsonArray().add(new JsonObject().put("id",  "module-post-1.0.0").put("action", "enable"));
    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .body(installReq.encode())
        .post("/_/proxy/tenants/" + okapiTenant + "/install?deploy=true")
        .then().statusCode(200).log().ifValidationFails()
        .body(equalTo(installReq.encodePrettily()));
    Assert.assertTrue(
        "raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
        .header("Content-Type", "text/plain; charset=UTF-8")
        .body("Okapi").post("/echo")
        .then().statusCode(200)
        .header("Content-Type", "text/plain; charset=UTF-8")
        .header("Content-Encoding", "gzip");
  }

}
