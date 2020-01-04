package org.folio.okapi.common;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
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
  private static final String VHOST = "vhost";

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
    server.close();
    vertx.close();
  }

  @Test
  public void testFailures(TestContext context) {
    logger.info("testGet");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      HttpClientRequest req = client.request(HttpMethod.GET, VHOST, HOST, PORT + 1, "/test1", res1 -> {
        context.assertTrue(res1.failed());
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
  }

  @Test
  public void testHead(TestContext context) {
    logger.info("testHead");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      HttpClientRequest req = client.request(HttpMethod.HEAD, VHOST, HOST, PORT, "/test1", res1 -> {
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
      HttpClientRequest req = client.request(HttpMethod.HEAD, VHOST, HOST, PORT, "/test1", res1 -> {
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
      HttpClientRequest req = client.request(HttpMethod.GET, VHOST, HOST, PORT, "/test1", res1 -> {
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
      HttpClientRequest req = client.request(HttpMethod.GET, "otherhost", HOST, PORT, "/test1", res1 -> {
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
      HttpClientRequest req = client.request(HttpMethod.GET, VHOST, HOST, PORT, "/test1?q=foo", res1 -> {
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
      HttpClientRequest req = client.request(HttpMethod.GET, VHOST, HOST, PORT, "/test1", res1 -> {
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
      HttpClientRequest req = client.request(HttpMethod.GET, VHOST, HOST, PORT, "/test1?q=foo", res1 -> {
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
      HttpClientRequest req = client.request(HttpMethod.HEAD, VHOST, HOST, PORT, "/test1", res1 -> {
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
      HttpClientRequest req = client.request(HttpMethod.GET, VHOST, HOST, PORT, "/test1", res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end("x");
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.request(HttpMethod.GET, VHOST, HOST, PORT, "/test1", res1 -> {
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
      HttpClientRequest req = client.request(HttpMethod.GET, VHOST, HOST, PORT, "/test1", res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.end(Buffer.buffer().appendString("x"));
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.request(HttpMethod.GET, VHOST, HOST, PORT, "/test1", res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
          res.endHandler(x -> async.complete());
        }
      });
      req.setChunked(true);
      req.write("x", res -> req.end());
      async.await(1000);
    }

  }

  @Test
  public void testPost(TestContext context) {
    logger.info("testPost");
    HttpClientCached client = new HttpClientCached(vertx.createHttpClient());

    {
      Async async = context.async();
      HttpClientRequest req = client.request(HttpMethod.POST, VHOST, HOST, PORT, "/test1", res1 -> {
        context.assertTrue(res1.succeeded());
        if (res1.succeeded()) {
          HttpClientResponse res = res1.result();
          context.assertEquals(200, res.statusCode());
          context.assertEquals(null, res.getHeader("X-Cache"));
        }
        async.complete();
      });
      req.end();
      async.await(1000);
    }

    {
      Async async = context.async();
      HttpClientRequest req = client.request(HttpMethod.POST, VHOST, HOST, PORT, "/test1", res1 -> {
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
  }

}
