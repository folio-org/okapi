package org.folio.okapi.common;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.Base64;
import java.util.HashMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class OkapiClientTest {

  private Vertx vertx;
  private static final int PORT = 9230;
  private static final String URL = "http://localhost:" + Integer.toString(PORT);
  private final Logger logger = OkapiLogger.get();
  private HttpServer server;

  private void myStreamHandle1(RoutingContext ctx) {
    ctx.response().setChunked(true);
    StringBuilder msg = new StringBuilder();

    String e = ctx.request().params().get("e");
    final int status = e == null ? 200 : Integer.parseInt(e);

    HttpResponse.responseText(ctx, status);
    OkapiToken token = new OkapiToken(ctx);

    ctx.request().handler(x -> msg.append(x));
    ctx.request().endHandler(x -> {
      if (msg.length() > 0) {
        ctx.response().write(msg.toString());
      } else {
        ctx.response().write("hello " + token.getTenant());
      }
      ctx.response().end();
    });
  }

  private void myStreamHandle2(RoutingContext ctx) {
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> {
        HttpResponse.responseText(ctx, 204).end();
      });
      return;
    }
    ctx.response().setChunked(true);
    ctx.request().pause();
    String p = ctx.request().params().get("p");
    if (p == null) {
      p = "/test1";
    }
    OkapiClient cli = new OkapiClient(ctx);
    cli.get(p, res -> {
      ctx.request().resume();
      if (res.failed()) {
        HttpResponse.responseError(ctx, res.getType(), res.cause());
      } else {
        ctx.request().endHandler(x -> {
          HttpResponse.responseJson(ctx, 200);
          ctx.response().write("\"" + res.result() + "\"");
          ctx.response().end();
        });
      }
    });
  }

  @Before
  public void setUp(TestContext context) {
    logger.debug("setUp");
    vertx = Vertx.vertx();
    Async async = context.async();

    Router router = Router.router(vertx);
    router.get("/test1").handler(this::myStreamHandle1);
    router.head("/test1").handler(this::myStreamHandle1);
    router.post("/test1").handler(this::myStreamHandle1);
    router.get("/test2").handler(this::myStreamHandle2);
    router.delete("/test2").handler(this::myStreamHandle2);

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    server = vertx.createHttpServer(so)
      .requestHandler(router::accept)
      .listen(
        PORT,
        result -> {
          if (result.failed()) {
            context.fail(result.cause());
          }
          async.complete();
        }
      );
  }

  @After
  public void tearDown(TestContext context) {
    Async async = context.async();
    vertx.close(x -> async.complete());
  }

  @Test
  public void testBogus(TestContext context) {
    Async async = context.async();
    final String bogusUrl = "http://xxxx.index.gyf:9231";
    OkapiClient cli = new OkapiClient(bogusUrl, vertx, null);
    assertEquals(bogusUrl, cli.getOkapiUrl());

    cli.get("/test1", res -> {
      assertTrue(res.failed());
      assertEquals(ErrorType.INTERNAL, res.getType());
      async.complete();
    });
  }

  @Test
  public void testNoOkapiURl(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    assertEquals(URL, cli.getOkapiUrl());

    cli.get("/test2?p=%2Ftest1", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      cli.close();
      cli.close(); // 2nd close should work (ignored)
      async.complete();
    });
  }

  @Test
  public void test1(TestContext context) {
    Async async = context.async();
    final String tenant = "test-lib";

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.REQUEST_ID, "919");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    assertEquals(URL, cli.getOkapiUrl());
    cli.disableInfoLog();
    cli.enableInfoLog();

    JsonObject o = new JsonObject();
    o.put("tenant", tenant);
    o.put("foo", "bar");
    String s = o.encodePrettily();
    byte[] encodedBytes = Base64.getEncoder().encode(s.getBytes());
    String e = new String(encodedBytes);
    String tokenStr = "method." + e + ".trail";
    OkapiToken t = new OkapiToken();
    t.setToken(tokenStr);
    assertEquals("test-lib", t.getTenant());

    cli.setOkapiToken(tokenStr);
    assertEquals(tokenStr, cli.getOkapiToken());

    cli.newReqId("920");

    cli.get("/test1", (ExtendedAsyncResult<String> res) -> {
      assertTrue(res.succeeded());
      assertEquals("hello test-lib", res.result());
      assertEquals(res.result(), cli.getResponsebody());
      MultiMap respH = cli.getRespHeaders();
      assertNotNull(respH);
      assertEquals("text/plain", respH.get("Content-Type"));

      test2(cli, async);
    });
  }

  private void test2(OkapiClient cli, Async async) {
    cli.request(HttpMethod.GET, "/test_not_found", "", res -> {
      assertTrue(res.failed());
      assertEquals(ErrorType.NOT_FOUND, res.getType());
      test3(cli, async);
    });
  }

  private void test3(OkapiClient cli, Async async) {
    cli.post("/test1", "FOOBAR", res -> {
      assertTrue(res.succeeded());
      assertEquals("FOOBAR", res.result());
      test4(cli, async);
    });
  }

  private void test4(OkapiClient cli, Async async) {
    cli.get("/test2?p=%2Ftest1", res -> {
      assertTrue(res.succeeded());
      assertEquals("\"hello test-lib\"", res.result());
      test5(cli, async);
    });
  }

  private void test5(OkapiClient cli, Async async) {
    cli.get("/test2?p=%2Fbad", res -> {
      assertTrue(res.failed());
      test6(cli, async);
    });
  }

  private void test6(OkapiClient cli, Async async) {
    cli.delete("/test2?p=%2Ftest1", res -> {
      assertTrue(res.succeeded());
      test7(cli, async);
    });
  }

  private void test7(OkapiClient cli, Async async) {
    cli.head("/test1", res -> {
      assertTrue(res.succeeded());
      test8(cli, async);
    });
  }

  private void test8(OkapiClient cli, Async async) {
    Buffer buf = Buffer.buffer();
    buf.appendString("FOOBAR");
    cli.request(HttpMethod.POST, "/test1", buf, res -> {
      assertTrue(res.succeeded());
      assertEquals("FOOBAR", res.result());
      testdone(cli, async);
    });
  }

  private void testdone(OkapiClient cli, Async async) {
    cli.close();
    async.complete();
  }

  @Test
  public void test403(TestContext context) {
    Async async = context.async();

    OkapiClient cli = new OkapiClient(URL, vertx, null);
    assertEquals(URL, cli.getOkapiUrl());
    cli.newReqId("920");

    cli.get("/test1?e=403", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.FORBIDDEN, res.getType());
      cli.close();
      async.complete();
    });
  }

  @Test
  public void test400(TestContext context) {
    Async async = context.async();

    OkapiClient cli = new OkapiClient(URL, vertx, null);
    assertEquals(URL, cli.getOkapiUrl());

    cli.get("/test1?e=400", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.USER, res.getType());
      cli.close();
      async.complete();
    });
  }

  @Test
  public void test500(TestContext context) {
    Async async = context.async();

    OkapiClient cli = new OkapiClient(URL, vertx, null);
    assertEquals(URL, cli.getOkapiUrl());

    cli.get("/test1?e=500", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      cli.close();
      async.complete();
    });
  }

  @Test
  public void testClosed1(TestContext context) {
    Async async = context.async();

    context.assertTrue(server != null);
    server.close(res -> {
      OkapiClient cli = new OkapiClient(URL, vertx, null);
      cli.setClosedRetry(0);
      cli.get("/test1", res2 -> {
        context.assertTrue(res2.failed());
        context.assertEquals(ErrorType.INTERNAL, res2.getType());
        async.complete();
      });
    });
  }

  @Test
  public void testClosed2(TestContext context) {
    Async async = context.async();

    context.assertTrue(server != null);
    server.close(res -> {
      OkapiClient cli = new OkapiClient(URL, vertx, null);
      cli.setClosedRetry(90);
      cli.get("/test1", res2 -> {
        context.assertTrue(res2.failed());
        context.assertEquals(ErrorType.INTERNAL, res2.getType());
        async.complete();
      });
    });
  }

  @Test
  public void testClosed3(TestContext context) {
    Async async = context.async();

    context.assertTrue(server != null);
    server.close(res -> {
      context.assertTrue(res.succeeded());
      vertx.setTimer(40, res1 -> server.listen(PORT));
    });
    OkapiClient cli = new OkapiClient(URL, vertx, null);
    cli.setClosedRetry(90);
    cli.get("/test1", res2 -> {
      context.assertTrue(res2.succeeded());
      async.complete();
    });
  }

}
