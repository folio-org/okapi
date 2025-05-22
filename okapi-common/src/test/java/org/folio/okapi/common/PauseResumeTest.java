package org.folio.okapi.common;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
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
  private static final int PORT = 9230;

  private void myStreamHandle1(RoutingContext ctx) {
    ctx.response().end("OK1");
  }

  private void myStreamHandle2(RoutingContext ctx) {
    ctx.request().pause();
    HttpClient cli = vertx.createHttpClient();

    cli.request(HttpMethod.POST, PORT, "localhost", "/test1").onComplete(req -> {
      if (req.failed()) {
        ctx.response().setStatusCode(500);
        ctx.response().end(req.cause().getMessage());
        return;
      }
      req.result().end();
      req.result().response()
      .onComplete(res -> {
        if (res.failed()) {
          ctx.response().setStatusCode(500);
          ctx.response().end(res.cause().getMessage());
          return;
        }
        if (ctx.request().isEnded()) {
          ctx.response().end("OK2"); // Vert.x 3.6 series
        } else {
          ctx.request().endHandler(y -> {
            ctx.response().end("OK2");
          });
          ctx.request().resume();
        }
      });
    });
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    Router router = Router.router(vertx);
    router.post("/test1").handler(this::myStreamHandle1);
    router.post("/test2").handler(this::myStreamHandle2);

    HttpServer server = vertx.createHttpServer()
      .requestHandler(router);

    server.listen(PORT)
    .onComplete(context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close()
    .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void test1(TestContext context) {
    Async async = context.async();

    HttpClient cli = vertx.createHttpClient();
    cli.request(HttpMethod.POST, PORT,"localhost", "/test1").onComplete(context.asyncAssertSuccess(req -> {
      req.end();
      req.response()
      .onComplete(context.asyncAssertSuccess(res -> {
        Buffer b = Buffer.buffer();
        res.handler(b::appendBuffer);
        res.endHandler(res2 -> {
          context.assertEquals("OK1", b.toString());
          context.assertEquals(200, res.statusCode());
          async.complete();
        });
      }));
    }));
    async.await();
  }

  @Test
  public void test2(TestContext context) {
    Async async = context.async();

    HttpClient cli = vertx.createHttpClient();
    cli.request(HttpMethod.POST, PORT,"localhost", "/test2").onComplete(context.asyncAssertSuccess(req -> {
      req.end();
      req.response()
      .onComplete(context.asyncAssertSuccess(res -> {
        Buffer b = Buffer.buffer();
        res.handler(b::appendBuffer);
        res.endHandler(res2 -> {
          context.assertEquals("OK2", b.toString());
          context.assertEquals(200, res.statusCode());
          async.complete();
        });
      }));
    }));
    async.await();
  }

  @Test
  public void test4(TestContext context) {
    HttpClient cli = vertx.createHttpClient();
    cli.request(HttpMethod.POST, PORT, "localhostxxx", "/test2").onComplete(context.asyncAssertFailure(res -> {
          context.assertTrue(res.getMessage().contains("localhostxxx"), res.getMessage());
            }));
  }

}
