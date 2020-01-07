package org.folio.okapi.common;

import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class HttpClientCachedTest {

  private Vertx vertx;
  private static final int PORT = 9230;
  private static final String HOST = "localhost";
  private static final String ABS_URI = "http://" + HOST + ":" + Integer.toString(PORT) + "/test1";
  private static final String ABS_URI_BAD_PORT = "http://" + HOST + ":" + Integer.toString(PORT + 1) + "/test1";
  private static final String CACHE_URI = "http://host";

  private final Logger logger = OkapiLogger.get();
  private HttpServer server;

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

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    Router router = Router.router(vertx);
    router.get("/test1").handler(this::myStreamHandle1);
    router.head("/test1").handler(this::myStreamHandle1);
    router.post("/test1").handler(this::myStreamHandle1);
    router.delete("/test1").handler(this::myStreamHandle1);

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    server = vertx.createHttpServer(so)
      .requestHandler(router)
      .listen(
        PORT,
        context.asyncAssertSuccess()
      );
  }

  @After
  public void tearDown(TestContext context) {
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
      HttpClientRequest req = client.requestAbs(HttpMethod.HEAD, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("MISS", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      context.assertFalse(req.isComplete());
      context.assertFalse(req.writeQueueFull());
      req.end();
      async.await(1000);
      context.assertTrue(req.isComplete());
      context.assertTrue(req.succeeded());
      context.assertFalse(req.failed());
      context.assertNull(req.cause());
      context.assertNotNull(req.result());
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.HEAD, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("HIT", res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end();
      async.await(1000);
    }

    client.close();
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
          context.assertEquals("HIT", res.getHeader("X-Cache"));
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
          context.assertEquals("HIT", res.getHeader("X-Cache"));
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
      req.end("x");
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.requestAbs(HttpMethod.GET, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals("HIT", res.getHeader("X-Cache"));
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
      req.sendHead(x -> {
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
      HttpClientRequest req = client.requestAbs(HttpMethod.POST, ABS_URI, res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
        }
        async.complete();
      });
      req.end("b");
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
        context.assertEquals("MISS", res.getHeader("X-Cache"));
        context.assertEquals("text/plain", res.headers().get("Content-type"));
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
        context.assertEquals("HIT", res.getHeader("X-Cache"));
        context.assertEquals("text/plain", res.headers().get("Content-type"));
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
        context.assertEquals("HIT", res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
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
        context.assertEquals("HIT", res.getHeader("X-Cache"));
        res.endHandler(x -> async.complete());
      });
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
}
