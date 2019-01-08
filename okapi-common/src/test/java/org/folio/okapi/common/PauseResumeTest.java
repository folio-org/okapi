package org.folio.okapi.common;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class PauseResumeTest {

  private Vertx vertx;
  private HttpServer server;
  private static final int PORT = 9230;

  private void myStreamHandle1(RoutingContext ctx) {
    ctx.response().end("OK1");
  }

  private void myStreamHandle2(RoutingContext ctx) {
    ctx.request().pause();
    HttpClient cli = vertx.createHttpClient();
    HttpClientRequest req = cli.post(PORT, "localhost", "/test1", res -> {
      if (ctx.request().isEnded()) {
        ctx.response().end("OK2"); // Vert.x 3.6 series
      } else {
        ctx.request().endHandler(x -> {
          ctx.response().end("OK2");
        });
      }
      ctx.request().resume();
    });
    req.exceptionHandler(x -> {
      ctx.response().setStatusCode(500);
      ctx.response().end(x.getLocalizedMessage());
    });
    req.end();
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    Router router = Router.router(vertx);
    router.post("/test1").handler(this::myStreamHandle1);
    router.post("/test2").handler(this::myStreamHandle2);

    server = vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(PORT, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void test1(TestContext context) {
    Async async = context.async();

    HttpClient cli = vertx.createHttpClient();
    HttpClientRequest req = cli.post(PORT, "localhost", "/test1", res -> {
      Buffer b = Buffer.buffer();
      res.handler(b::appendBuffer);
      res.endHandler(res2 -> {
        context.assertEquals("OK1", b.toString());
        context.assertEquals(200, res.statusCode());
        async.complete();
      });
    });
    req.end();
  }

  @Test
  public void test2(TestContext context) {
    Async async = context.async();

    HttpClient cli = vertx.createHttpClient();
    HttpClientRequest req = cli.post(PORT, "localhost", "/test2", res -> {
      Buffer b = Buffer.buffer();
      res.handler(b::appendBuffer);
      res.endHandler(res2 -> {
        context.assertEquals("OK2", b.toString());
        context.assertEquals(200, res.statusCode());
        async.complete();
      });
    });
    req.end("foo");
  }

  @Test
  public void test3(TestContext context) {
    Async async = context.async();

    HttpClient cli = vertx.createHttpClient();
    HttpClientRequest req = cli.post(PORT, "localhost", "/test2", res -> {
      Buffer b = Buffer.buffer();
      res.handler(b::appendBuffer);
      res.endHandler(res2 -> {
        context.assertEquals("OK2", b.toString());
        context.assertEquals(200, res.statusCode());
        async.complete();
      });
    });
    req.end();
  }
}
