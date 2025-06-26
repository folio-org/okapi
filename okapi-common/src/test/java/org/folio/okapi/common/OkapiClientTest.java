package org.folio.okapi.common;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.Base64;
import java.util.HashMap;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class OkapiClientTest {

  private Vertx vertx;
  private static final int PORT = 9230;
  private static final String LOCALHOST = "localhost";
  private static final String URL = "http://" + LOCALHOST + ":" + PORT;
  private final Logger logger = OkapiLogger.get();
  private HttpServer server;

  @Rule
  public Timeout timeoutRule = Timeout.seconds(10);

  private void myStreamHandle1(RoutingContext ctx) {
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> {
        HttpResponse.responseError(ctx, 204, "");
      });
      return;
    }
    ctx.response().setChunked(true);
    StringBuilder msg = new StringBuilder();

    String e = ctx.request().params().get("e");
    final int status = e == null ? 200 : Integer.parseInt(e);

    HttpResponse.responseText(ctx, status);
    OkapiToken token = new OkapiToken(ctx.request().getHeader(XOkapiHeaders.TOKEN));

    ctx.request().handler(x -> msg.append(x));
    ctx.request().endHandler(x -> {
      if (msg.length() > 0) {
        ctx.response().write(msg.toString());
      } else {
        ctx.response().write("hello " + token.getTenantWithoutValidation());
      }
      ctx.response().end();
    });
  }

  private void myStreamHandle2(RoutingContext ctx) {
    ctx.response().setChunked(true);
    ctx.request().pause();
    String p = ctx.request().params().get("p");
    if (p == null) {
      p = "/test1";
    }
    OkapiClient cli = new OkapiClient(ctx);
    cli.get(p, res -> {
      if (res.failed()) {
        HttpResponse.responseError(ctx, ErrorTypeException.getType(res), res.cause());
      } else {
        ctx.request().endHandler(x -> {
          HttpResponse.responseJson(ctx, 200);
          ctx.response().write("\"" + res.result() + "\"");
          ctx.response().end();
        });
      }
      ctx.request().resume();
    });
  }

  @Before
  public void setUp(TestContext context) {
    logger.debug("setUp");
    vertx = Vertx.vertx();

    spinUpServer()
    .onComplete(context.asyncAssertSuccess());
  }

  private Future<Void> spinUpServer() {
    Router router = Router.router(vertx);
    router.get("/test1").handler(this::myStreamHandle1);
    router.head("/test1").handler(this::myStreamHandle1);
    router.post("/test1").handler(this::myStreamHandle1);
    router.delete("/test1").handler(this::myStreamHandle1);
    router.get("/test2").handler(this::myStreamHandle2);

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    server = vertx.createHttpServer(so)
      .requestHandler(router);

    return server.listen(PORT).mapEmpty();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close()
    .onComplete(context.asyncAssertSuccess());
  }

  @Test(expected = NullPointerException.class)
  public void testNullUrl() {
    new OkapiClient(URL, vertx, null).setOkapiUrl(null);
  }

  @Test
  public void testBogus(TestContext context) {
    Async async = context.async();
    final String bogusUrl = "http://xxxx.index.gyf:9231";
    OkapiClient cli = new OkapiClient(bogusUrl, vertx, null);
    context.assertEquals(bogusUrl, cli.getOkapiUrl());

    cli.get("/test1", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, ErrorTypeException.getType(res));
      async.complete();
    });
  }

  @Test
  public void testHeaders(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put("a", "1");
    headers.put("b", "");
    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    context.assertEquals("1", cli.getHeaders().get("a"));
    context.assertNull(cli.getHeaders().get("b"));
    HashMap<String, String> headers2 = new HashMap<>();
    headers2.put("c", "2");
    headers2.put("d", "");
    cli.setHeaders(headers2);
    context.assertNull(cli.getHeaders().get("a"));
    context.assertNull(cli.getHeaders().get("b"));
    context.assertEquals("2", cli.getHeaders().get("c"));
    context.assertNull(cli.getHeaders().get("d"));
  }

  @Test
  public void testNoOkapiURl(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    context.assertEquals(URL, cli.getOkapiUrl());

    cli.get("/test2?p=%2Ftest1", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, ErrorTypeException.getType(res));
      async.complete();
    });
  }

  private OkapiToken token(String tenant) {
    JsonObject o = new JsonObject();
    o.put("tenant", tenant);
    o.put("foo", "bar");
    String s = o.encodePrettily();
    byte[] encodedBytes = Base64.getEncoder().encode(s.getBytes());
    String e = new String(encodedBytes);
    String tokenStr = "method." + e + ".trail";
    return new OkapiToken(tokenStr);
  }

  @Test
  public void test1(TestContext context) {
    final String tenant = "test-lib";

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.REQUEST_ID, "919");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    context.assertEquals(URL, cli.getOkapiUrl());
    cli.disableInfoLog();
    cli.enableInfoLog();

    OkapiToken t = token(tenant);
    String tokenStr = t.toString();
    context.assertEquals("test-lib", t.getTenantWithoutValidation());

    cli.setOkapiToken(tokenStr);
    context.assertEquals(tokenStr, cli.getOkapiToken());

    cli.newReqId("920");

    {
      Async async = context.async();
      cli.get("/test1", res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("hello test-lib", res.result());
        context.assertEquals(res.result(), cli.getResponsebody());
        MultiMap respH = cli.getRespHeaders();
        context.assertNotNull(respH);
        context.assertEquals("text/plain", respH.get("Content-Type"));
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      cli.request(HttpMethod.GET, "/test_not_found", "", res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.NOT_FOUND, ErrorTypeException.getType(res));
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      cli.post("/test1", "FOOBAR", res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("FOOBAR", res.result());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      cli.get("/test2?p=%2Ftest1", res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("\"hello test-lib\"", res.result());
        context.assertEquals(200, cli.getStatusCode());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      cli.get("/test2?p=%2Fbad", res -> {
        context.assertTrue(res.failed());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      cli.delete("/test1", res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      cli.head("/test1", res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      Buffer buf = Buffer.buffer();
      buf.appendString("FOOBAR");
      cli.request(HttpMethod.POST, "/test1", buf, res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("FOOBAR", res.result());
        async.complete();
      });
      async.await();
    }
  }

  @Test
  public void testPost(TestContext context) {
    new OkapiClient(URL, vertx, null)
        .post("/test1", "foo")
        .onComplete(context.asyncAssertSuccess(s -> context.assertEquals("foo", s)));
  }

  @Test
  public void testGet(TestContext context) {
    OkapiClient cli = new OkapiClient(URL, vertx, null);
    cli.setOkapiToken(token("ten").toString());
    cli.get("/test1")
        .onComplete(context.asyncAssertSuccess(s -> context.assertEquals("hello ten", s)));
  }

  @Test
  public void testDelete(TestContext context) {
    OkapiClient cli = new OkapiClient(URL, vertx, null);
    cli.delete("/test5")
        .onComplete(context.asyncAssertFailure());
    cli.delete("/test1")
        .onComplete(context.asyncAssertSuccess(s -> context.assertNull(s)));
  }

  @Test
  public void testHead(TestContext context) {
    OkapiClient cli = new OkapiClient(URL, vertx, null);
    cli.head("/test5")
        .onComplete(context.asyncAssertFailure());
    cli.head("/test1")
        .onComplete(context.asyncAssertSuccess(s -> context.assertNull(s)));
  }

  @Test
  public void test403(TestContext context) {
    Async async = context.async();

    OkapiClient cli = new OkapiClient(URL, vertx, null);
    context.assertEquals(URL, cli.getOkapiUrl());
    cli.newReqId("920");

    cli.get("/test1?e=403", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.FORBIDDEN, ErrorTypeException.getType(res));
      async.complete();
    });
  }

  @Test
  public void test400(TestContext context) {
    Async async = context.async();

    OkapiClient cli = new OkapiClient(URL, vertx, null);
    context.assertEquals(URL, cli.getOkapiUrl());

    cli.get("/test1?e=400", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.USER, ErrorTypeException.getType(res));
      async.complete();
    });
  }

  @Test
  public void test500(TestContext context) {
    Async async = context.async();

    OkapiClient cli = new OkapiClient(URL, vertx, null);
    context.assertEquals(URL, cli.getOkapiUrl());

    cli.get("/test1?e=500", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, ErrorTypeException.getType(res));
      async.complete();
    });
  }

  @Test
  public void testClosed1(TestContext context) {
    context.assertTrue(server != null);
    server.close()
    .compose(res -> {
      OkapiClient cli = new OkapiClient(URL, vertx, null);
      cli.setClosedRetry(0);
      return cli.get("/test1");
    })
    .onComplete(context.asyncAssertFailure(res2 -> {
      ErrorTypeException e = (ErrorTypeException) res2;
      context.assertEquals(ErrorType.INTERNAL, e.getErrorType());
    }));
  }

  @Test
  public void testClosed2(TestContext context) {
    context.assertTrue(server != null);
    server.close()
    .compose(res -> {
      OkapiClient cli = new OkapiClient(URL, vertx, null);
      cli.setClosedRetry(90);
      return cli.get("/test1");
    })
    .onComplete(context.asyncAssertFailure(res2 -> {
      context.assertEquals(ErrorType.INTERNAL, ErrorTypeException.getType(res2));
    }));
  }

  @Test
  public void testClosed3(TestContext context) {
    context.assertTrue(server != null);
    server.close()
    .compose(res -> {
      vertx.setTimer(40, res1 -> spinUpServer());
      OkapiClient cli = new OkapiClient(URL, vertx, null);
      cli.setClosedRetry(90);
      return cli.get("/test1");
    })
    .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testLegacyPostOk(TestContext context) {
    context.assertTrue(server != null);
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.POST, PORT, LOCALHOST, URL + "/test1")
    .compose(request -> request.send().expecting(HttpResponseExpectation.SC_OK))
    .onComplete(context.asyncAssertSuccess());
  }

}
