package org.folio.okapi.common;

import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class HttpClientCachedTest {

  private static Vertx vertx;
  private static final int PORT = 9230;
  private static final String HOST = "localhost";
  private static final String ABS_URI = "http://" + HOST + ":" + PORT + "/test1";
  private static final String ABS_URI_BAD_PORT = "http://" + HOST + ":" + Integer.toString(PORT + 1) + "/test1";
  private static final String CACHE_URI = "http://host";

  private final Logger logger = OkapiLogger.get();
  private static HttpServer server;

  private static void myStreamHandle1(RoutingContext ctx) {
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

    HttpServerResponse response = HttpResponse.responseText(ctx, status);
    String cc = ctx.request().params().get("cc");
    if (cc != null) {
      response.putHeader("Cache-Control", cc);
    }

    String expires = ctx.request().params().get("expires");
    if ("old".equals(expires)) {
      response.putHeader("Expires", "Thu, 25 Aug 2016 08:59:00 GMT");
    } else if ("now".equals(expires)) {
      ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of("GMT"));
      response.putHeader("Expires",
        zdt.format(DateTimeFormatter.RFC_1123_DATE_TIME));
    } else if ("invalid".equals(expires)) {
      response.putHeader("Expires", "1 2 3 4");
    }

    ctx.request().handler(x -> msg.append(x));
    ctx.request().endHandler(x -> {
      if (msg.length() > 0) {
        ctx.response().write(msg.toString());
      } else {
        ctx.response().write("hello " + ctx.request().headers().get(XOkapiHeaders.TENANT));
      }
      ctx.response().end();
    });
  }

  @BeforeClass
  public static void setUp(TestContext context) {
    vertx = Vertx.vertx();

    Router router = Router.router(vertx);
    router.get("/test1").handler(HttpClientCachedTest::myStreamHandle1);
    router.head("/test1").handler(HttpClientCachedTest::myStreamHandle1);
    router.post("/test1").handler(HttpClientCachedTest::myStreamHandle1);
    router.delete("/test1").handler(HttpClientCachedTest::myStreamHandle1);

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    server = vertx.createHttpServer(so)
      .requestHandler(router)
      .listen(
        PORT,
        context.asyncAssertSuccess()
      );
  }

  @AfterClass
  public static void tearDown(TestContext context) {
    server.close(x -> {
      vertx.close(context.asyncAssertSuccess());
    });
  }

  @Test
  public void testFailures(TestContext context) {
    logger.info("testFailure");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI_BAD_PORT, res1 -> {
        context.assertTrue(res1.failed());
        context.assertTrue(res1.cause().getMessage().contains("Connection refused"));
        async.complete();
      });
      context.assertFalse(req.isComplete());
      req.end();
      async.await(1000);
      context.assertTrue(req.isComplete());
      context.assertFalse(req.succeeded());
      context.assertTrue(req.failed());
      context.assertNotNull(req.cause());
      context.assertNull(req.result());
    }

    {
      boolean thrown = false;
      Async async = context.async();
      try {
        HttpClientRequest req = client.requestAbs(HttpMethod.GET, "syz://localhost/test1", res1 -> {
          async.complete();
        });
        context.assertFalse(req.isComplete());
        req.end();
      } catch (VertxException ex) {
        thrown = true;
        async.complete();
      }
      async.await(1000);
      context.assertTrue(thrown);
    }

    {
      Async async = context.async();
      boolean thrown = false;
      try {
        HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
          async.complete();
        });
        context.assertFalse(req.isComplete());
        req.reset(0);
        req.end();
      } catch (VertxException ex) {
        context.assertEquals("HttpClientCached: reset not implemented", ex.getMessage());
        thrown = true;
        async.complete();
      }
      async.await(1000);
    }

    {
      Async async = context.async();
      boolean thrown = false;
      try {
        HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
          async.complete();
        });
        context.assertFalse(req.isComplete());
        req.writeCustomFrame(0, 1, Buffer.buffer());
        req.end();
      } catch (VertxException ex) {
        context.assertEquals("HttpClientCached: writeCustomFrame not implemented", ex.getMessage());
        thrown = true;
        async.complete();
      }
      async.await(1000);
    }

  }

  @Test
  public void testHead(TestContext context) {
    logger.info("testHead");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      StringBuilder b = new StringBuilder();
      HttpClientRequest req = client.requestAbs(HttpMethod.HEAD, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.handler(x -> {
            b.append("[handler]");
          });
          res.endHandler(x -> {
            context.assertNull(x);
            b.append("[endHandler]");
            async.complete();
          });
        }
      });
      context.assertFalse(req.isComplete());
      context.assertFalse(req.writeQueueFull());
      req.end();
      async.await(1000);
      context.assertEquals("[endHandler]", b.toString());
      context.assertTrue(req.isComplete());
      context.assertTrue(req.succeeded());
      context.assertFalse(req.failed());
      context.assertNull(req.cause());
      context.assertNotNull(req.result());
    }

    {
      Async async = context.async();
      StringBuilder b = new StringBuilder();
      HttpClientRequest req = client.requestAbs(HttpMethod.HEAD, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("HIT: 1", res.getHeader("X-Cache"));
          res.handler(x -> {
            b.append("[handler]");
          });
          res.endHandler(x -> {
            context.assertNull(x);
            b.append("[endHandler]");
            async.complete();
          });
        }
      });
      req.end();
      async.await(1000);
      context.assertEquals("[endHandler]", b.toString());
    }
    client.close();
  }

  @Test
  public void testHeadPauseResume(TestContext context) {
    logger.info("testHeadPauseResume");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      StringBuilder b = new StringBuilder();
      HttpClientRequest req = client.requestAbs(HttpMethod.HEAD, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.pause();
          res.handler(x -> {
            b.append("[handler]");
          });
          res.endHandler(x -> {
            context.assertNull(x);
            b.append("[endHandler]");
            async.complete();
          });
          vertx.runOnContext(x -> {
            b.append("[runOnContext]");
            res.resume();
          });
        }
      });
      context.assertFalse(req.isComplete());
      context.assertFalse(req.writeQueueFull());
      req.end();
      async.await(1000);
      context.assertEquals("[runOnContext][endHandler]", b.toString());
      context.assertTrue(req.isComplete());
      context.assertTrue(req.succeeded());
      context.assertFalse(req.failed());
      context.assertNull(req.cause());
      context.assertNotNull(req.result());
    }

    {
      Async async = context.async();
      StringBuilder b = new StringBuilder();
      HttpClientRequest req = client.requestAbs(HttpMethod.HEAD, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("HIT: 1", res.getHeader("X-Cache"));
          res.pause();
          res.handler(x -> {
            b.append("[handler]");
          });
          res.endHandler(x -> {
            context.assertNull(x);
            b.append("[endHandler]");
            async.complete();
          });
          vertx.runOnContext(x -> {
            b.append("[runOnContext]");
            res.resume();
          });
        }
      });
      req.end();
      async.await(1000);
      context.assertEquals("[runOnContext][endHandler]", b.toString());
    }
    client.close();
  }

  @Test
  public void testGetPauseResume(TestContext context) {
    logger.info("testGetPauseResume");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      StringBuilder b = new StringBuilder();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          context.assertNotNull(res.request());
          res.pause();
          res.exceptionHandler(x -> {
            b.append("[exceptionhandler]");
          });
          res.handler(x -> {
            context.assertNotNull(x);
            b.append("[handler]");
          });
          res.endHandler(x -> {
            b.append("[endHandler]");
            async.complete();
          });
          vertx.runOnContext(x -> {
            b.append("[runOnContext]");
            res.resume();
          });
        }
      });
      context.assertFalse(req.isComplete());
      context.assertFalse(req.writeQueueFull());
      req.end();
      async.await(1000);
      context.assertEquals("[runOnContext][handler][endHandler]", b.toString());
    }

    {
      Async async = context.async();
      StringBuilder b = new StringBuilder();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("HIT: 1", res.getHeader("X-Cache"));
          context.assertNotNull(res.request());
          res.pause();
          res.exceptionHandler(x -> {
            b.append("[exceptionhandler]");
          });
          res.resume();
          res.pause();
          res.endHandler(x -> {
            b.append("[endHandler]");
            async.complete();
          });
          res.handler(x -> {
            context.assertNotNull(x);
            b.append("[handler]");
          });
          vertx.runOnContext(x -> {
            b.append("[runOnContext]");
            res.resume();
            res.request();
          });
        }
      });
      req.end();
      async.await(1000);
      context.assertEquals("[runOnContext][handler][endHandler]", b.toString());
    }

    client.close();
  }

  @Test
  public void testBufferSize(TestContext context) {
    logger.info("testBufferSize");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    client.setMaxBodySize(15);
    for (int i = 0; i < 2; i++) {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.putHeader("X-Okapi-Tenant", "1234567890"); // hello_this >= 16 bytes
      req.end();
      async.await(1000);
    }
  }

  @Test
  public void testBufferSizeBodyHandler(TestContext context) {
    logger.info("testBufferSizeBodyHandler");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    client.setMaxBodySize(15);
    for (int i = 0; i < 2; i++) {
      final String expect = i == 0 ? "MISS" : "HIT: 1";
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(expect, res.getHeader("X-Cache"));
          res.bodyHandler(x -> async.complete());
        }
      });
      req.putHeader("X-Okapi-Tenant", "1234567890"); // hello_this >= 16 bytes
      req.end();
      async.await(1000);
    }
  }

  @Test
  public void testBodyHandlerGet(TestContext context) {
    logger.info("testBodyHandlerGet");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    {
      Async async = context.async();
      Buffer b = Buffer.buffer();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.bodyHandler(b::appendBuffer);
          res.endHandler(x -> async.complete());
        }
      });
      req.end();
      async.await(1000);
      context.assertEquals("hello null", b.toString());
    }
    {
      Async async = context.async();
      Buffer b = Buffer.buffer();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("HIT: 1", res.getHeader("X-Cache"));
          res.bodyHandler(b::appendBuffer);
          res.endHandler(x -> async.complete());
        }
      });
      req.end();
      async.await(1000);
      context.assertEquals("hello null", b.toString());
    }
    {
      Async async = context.async();
      Buffer b = Buffer.buffer();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.bodyHandler(b::appendBuffer);
          res.endHandler(x -> async.complete());
        }
      });
      req.end("x");
      async.await(1000);
      context.assertEquals("x", b.toString());
    }
  }

  public void testBodyHandlerHead(TestContext context) {
    logger.info("testBodyHandlerHead");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    {
      Async async = context.async();
      Buffer b = Buffer.buffer();
      HttpClientRequest req = client.requestAbs(HttpMethod.HEAD, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.bodyHandler(x -> b.appendString("[bodyHandler]"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end();
      async.await(1000);
      context.assertEquals("", b.toString());
    }
    {
      Async async = context.async();
      Buffer b = Buffer.buffer();
      HttpClientRequest req = client.requestAbs(HttpMethod.HEAD, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("HIT: 1", res.getHeader("X-Cache"));
          res.bodyHandler(x -> b.appendString("[bodyHandler]"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end();
      async.await(1000);
      context.assertEquals("", b.toString());
    }
  }

  @Test
  public void testHEadEndHandlerOnly(TestContext context) {
    logger.info("testHeadEndHandlerOnly");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    {
      Async async = context.async();
      Buffer b = Buffer.buffer();
      HttpClientRequest req = client.requestAbs(HttpMethod.HEAD, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end();
      async.await(1000);
      context.assertEquals("", b.toString());
    }
  }

  @Test
  public void testGet(TestContext context) {
    logger.info("testGet");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      context.assertEquals("/test1", req.path());
      context.assertEquals("/test1", req.uri());
      context.assertNull(req.query());
      req.end();
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, "otherhost/test1", res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end();
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI + "?q=a", res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      context.assertEquals("/test1", req.path());
      context.assertEquals("/test1?q=a", req.uri());
      context.assertEquals("q=a", req.query());
      req.end();
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("HIT: 1", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end();
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI + "?q=a", res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("HIT: 1", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end();
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.HEAD, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end();
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.sendHead();
      req.end();
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("HIT: 2", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end(Buffer.buffer());
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      context.assertNull(req.connection());
      req.sendHead(x -> req.end());
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end(Buffer.buffer("x"));
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        res.endHandler(x -> {
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          async.complete();
        });
      });
      req.setChunked(true);
      req.sendHead();
      req.write("x", x -> {
      });
      req.end();
      async.await(1000);
    }

  }

  @Test
  public void testPost(TestContext context) {
    logger.info("testPost");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.POST, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
        }
        async.complete();
      });
      context.assertEquals("/test1", req.path());
      context.assertEquals("/test1", req.uri());
      context.assertNull(req.query());
      req.end();
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.POST, ABS_URI + "?q=a", res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
        }
        async.complete();
      });
      context.assertEquals("/test1", req.path());
      context.assertEquals("/test1?q=a", req.uri());
      context.assertEquals("q=a", req.query());
      req.end("b");
      async.await(1000);
    }
  }

  @Test
  public void testIgnoreCacheHeader(TestContext context) {
    logger.info("testIgnoreCacheHeader");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals("MISS", res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
      req.putHeader("Date", "1"); // date header ignored for cache-lookup
      req.end(x -> {
      });
      async.await(1000);
    }
    client.addIgnoreHeader(XOkapiHeaders.REQUEST_ID);
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals("HIT: 1", res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
      req.putHeader("Date", "2");
      req.putHeader(XOkapiHeaders.REQUEST_ID, "2");
      req.end("");
      async.await(1000);
    }
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals("HIT: 2", res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
      req.putHeader("Date", "3");
      req.putHeader(XOkapiHeaders.REQUEST_ID, "3");
      req.end(x -> {
      });
      async.await(1000);
    }

    client.removeIgnoreHeader(XOkapiHeaders.REQUEST_ID);
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals("MISS", res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
      req.putHeader("Date", "2");
      req.putHeader(XOkapiHeaders.REQUEST_ID, "2");
      req.end("");
      async.await(1000);
    }
  }

  @Test
  public void testHttpClientRequest(TestContext context) {
    logger.info("testHttpClientRequest");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals("MISS", res.headers().get("X-Cache"));
        context.assertEquals("text/plain", res.getHeader("Content-type"));
        CharSequence h = new StringBuilder("Content-Type");
        context.assertEquals("text/plain", res.getHeader(h));
        context.assertEquals("OK", res.statusMessage());
        context.assertEquals(HttpVersion.HTTP_1_1, res.version());
        context.assertTrue(res.cookies().isEmpty());
        Buffer b = Buffer.buffer();
        res.handler(b::appendBuffer);
        res.endHandler(x -> {
          context.assertNull(res.getTrailer("foo"));
          context.assertNull(res.trailers().get("foo"));
          context.assertEquals("hello testlib", b.toString());
          async.complete();
        });
      });
      context.assertEquals(HttpMethod.GET, req.method());
      context.assertEquals(ABS_URI, req.absoluteURI());
      context.assertTrue(req.headers().isEmpty());

      req.putHeader("Content-Type", "application/json");
      context.assertTrue(req.headers().contains("content-type"));

      List<String> ctypes = new LinkedList<>();
      ctypes.add("application/json");
      ctypes.add("text/plain");
      req.putHeader("Accept", ctypes);
      context.assertTrue(req.headers().contains("accept"));

      req.putHeader("X-Okapi-Tenant", "testlib");

      req.continueHandler(x -> {
      });
      req.setWriteQueueMaxSize(100);
      req.setMaxRedirects(3);
      req.setFollowRedirects(true);
      context.assertFalse(req.isChunked());
      context.assertFalse(req.isComplete());
      req.setHost("localhost");
      context.assertEquals("localhost", req.getHost());
      req.setRawMethod("GET");
      context.assertEquals("GET", req.getRawMethod());
      req.exceptionHandler(x -> {
      });
      req.drainHandler(x -> {
      });
      req.pushHandler(x -> {
      });
      context.assertFalse(req.writeQueueFull());
      req.end("");

      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals("HIT: 1", res.headers().get("X-Cache"));
        context.assertEquals("text/plain", res.getHeader("Content-type"));
        CharSequence h = new StringBuilder("Content-Type");
        context.assertEquals("text/plain", res.getHeader(h));
        context.assertEquals("OK", res.statusMessage());
        context.assertEquals(HttpVersion.HTTP_1_1, res.version());
        res.trailers();
        context.assertTrue(res.cookies().isEmpty());
        Buffer b = Buffer.buffer();
        res.handler(b::appendBuffer);
        res.endHandler(x -> {
          context.assertNull(res.getTrailer("foo"));
          context.assertNull(res.trailers().get("foo"));
          context.assertEquals("hello testlib", b.toString());
          async.complete();
        });
      });

      CharSequence h = new StringBuilder("Content-Type");
      CharSequence v = new StringBuilder("application/json");
      req.putHeader(h, v);
      context.assertTrue(req.headers().contains("Content-Type"));

      List<CharSequence> ctypes = new LinkedList<>();
      ctypes.add("application/json");
      ctypes.add("text/plain");
      req.putHeader("Accept", ctypes);
      context.assertTrue(req.headers().contains("accept"));

      req.putHeader("X-Okapi-Tenant", "testlib");

      req.end("", x -> {
      });
      async.await(1000);
    }
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals(null, res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
      context.assertFalse(req.isComplete());
      req.setHost("localhost");
      context.assertEquals("localhost", req.getHost());
      req.setRawMethod("GET");
      context.assertEquals("GET", req.getRawMethod());
      req.setTimeout(1000);
      req.exceptionHandler(x -> {
      });
      req.drainHandler(x -> {
      });
      req.pushHandler(x -> {
      });
      req.setChunked(true); // must be called before header is flushed
      context.assertTrue(req.isChunked());
      req.onComplete(x -> {
      }); // flushes header
      context.assertTrue(req.isChunked());
      req.getHandler();
      context.assertFalse(req.writeQueueFull());
      req.write("x");
      req.write("x", "UTF-8");
      req.write("x", "UTF-8", x -> {
      });
      req.continueHandler(x -> {
      });
      req.write(Buffer.buffer("x"));
      req.write(Buffer.buffer("x"), x -> {
      });
      req.getStreamPriority();
      req.end("");

      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals(null, res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
      req.setChunked(true);
      req.write(Buffer.buffer("x"));
      req.connection();
      req.end("", x -> {
      });
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals(null, res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
      req.setChunked(true);
      req.write(Buffer.buffer("x"));
      req.end(Buffer.buffer(), x -> {
      });
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals(null, res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
      req.setChunked(true);
      req.write(Buffer.buffer("x"));
      req.end(x -> {
      });
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals(null, res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
      req.end("x", "UTF-8");
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.failed()) {
          async.complete();
          return;
        }
        HttpClientResponse res = res1.result();
        context.assertEquals(200, res.statusCode());
        context.assertEquals(null, res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
      req.end("x", "UTF-8", x -> {
      });
      async.await(1000);
    }

  }

  @Test
  public void testDefaultMaxAge(TestContext context) {
    logger.info("testDefaultMaxAge");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    client.defaultMaxAge(0);
    for (int i = 0; i < 2; i++) {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> vertx.setTimer(100, y -> async.complete()));
        }
      });
      req.end();
      async.await(1000);
    }
  }

  @Test
  public void testCacheControlMaxAgeServer(TestContext context) {
    logger.info("testCacheControlMaxAgeServer");

    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    client.defaultMaxAge(60);
    for (int i = 0; i < 2; i++) {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI + "?cc=max-age%3D0", res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> vertx.setTimer(100, y -> async.complete()));
        });
      req.end();
      async.await(1000);
    }
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI + "?cc=max-age%3Dinvalid", res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> vertx.setTimer(100, y -> async.complete()));
        });
      req.end();
      async.await(1000);
    }

  }

  @Test
  public void testCacheControlMaxAgeClient(TestContext context) {
    logger.info("testCacheControlMaxAgeClient");

    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    client.defaultMaxAge(60);
    for (int i = 0; i < 2; i++) {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI, res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> vertx.setTimer(100, y -> async.complete()));
        });
      req.putHeader("Cache-Control", "max-age=0");
      req.end();
      async.await(1000);
    }
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI, res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> vertx.setTimer(100, y -> async.complete()));
        });
      req.putHeader("Cache-Control", "max-age=invalid");
      req.end();
      async.await(1000);
    }
  }

  @Test
  public void testGlobalMaxAge(TestContext context) {
    logger.info("testGlobalMaxAge");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    client.globalMaxAge(0);
    for (int i = 0; i < 2; i++) {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI + "?cc=max-age%3D2", res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> vertx.setTimer(100, y -> async.complete()));
        });
      req.end();
      async.await(1000);
    }
  }

  @Test
  public void testExpires(TestContext context) {
    logger.info("testExpires");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    for (int i = 0; i < 2; i++) {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI + "?expires=old", res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> vertx.setTimer(100, y -> async.complete()));
        });
      req.end();
      async.await(1000);
    }
    for (int i = 0; i < 2; i++) {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI + "?expires=now", res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> vertx.setTimer(100, y -> async.complete()));
        });
      req.end();
      async.await(1000);
    }
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI + "?expires=invalid", res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> vertx.setTimer(100, y -> async.complete()));
        });
      req.end();
      async.await(1000);
    }
  }

  @Test
  public void testCacheControlNoStore(TestContext context) {
    logger.info("testCacheControlNoStore");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI, res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        });
      // two headers to see that 2nd one is inspected
      req.putHeader("Cache-Control", "must-revalidate");
      req.putHeader("Cache-Control", "no-store");
      req.end();
      async.await(1000);
    }
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI + "?cc=no-store", res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        });
      req.end();
      async.await(1000);
    }
  }

  @Test
  public void testCacheControlNoCache(TestContext context) {
    logger.info("testCacheControlNoCache");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI, res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        });
      req.putHeader("Cache-Control", "no-cache");
      req.end();
      async.await(1000);
    }
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI + "?cc=no-cache", res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        });
      req.end();
      async.await(1000);
    }
  }

  @Test
  public void testStatusCode(TestContext context) {
    logger.info("testStatusCode");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET,
        ABS_URI + "?e=400", res1 -> {
          context.assertTrue(res1.succeeded());
          if (res1.failed()) {
            async.complete();
            return;
          }
          HttpClientResponse res = res1.result();
          context.assertEquals(400, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        });
      req.end();
      async.await(1000);
    }
  }

  @Test
  public void testLookupCacheControl(TestContext context) {
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());
    context.assertEquals("123", client.lookupCacheControl("x, max-age = 123", "max-age"));
    context.assertEquals("123", client.lookupCacheControl("x, max-age = 123,", "max-age"));
    context.assertEquals("", client.lookupCacheControl("x, max-age", "max-age"));
    context.assertEquals("", client.lookupCacheControl("x, max-age ", "max-age"));
    context.assertEquals("", client.lookupCacheControl(" maX-age=", "max-age"));
    context.assertNull(client.lookupCacheControl(" maX age=", "mAx-age"));

    // should be fixed, but not really a problem because we're only looking for a few values
    context.assertEquals("", client.lookupCacheControl(" max-agee", "max-age"));
  }

}
