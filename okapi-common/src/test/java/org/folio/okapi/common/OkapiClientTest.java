package org.folio.okapi.common;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
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
  private static final int PORT = 9130;
  private static final String URL = "http://localhost:" + Integer.toString(PORT);
  private final Logger logger = LoggerFactory.getLogger("okapi");

  public OkapiClientTest() {
    System.setProperty("vertx.logger-delegate-factory-class-name",
      "io.vertx.core.logging.SLF4JLogDelegateFactory");

  }

  private void myStreamHandle1(RoutingContext ctx) {
    ctx.response().setChunked(true);
    ctx.response().setStatusCode(200);
    StringBuilder xmlMsg = new StringBuilder();
    String hv = ctx.request().getHeader("X-my-header");
    if (hv != null) {
      xmlMsg.append(hv);
    }
    ctx.response().putHeader("Content-Type", "text/plain");

    // Report all headers back (in headers and in the body) if requested
    String allh = ctx.request().getHeader("X-all-headers");
    if (allh != null) {
      String qry = ctx.request().query();
      if (qry != null) {
        ctx.request().headers().add("X-Url-Params", qry);
      }
      for (String hdr : ctx.request().headers().names()) {
        String tenantReqs = ctx.request().getHeader(hdr);
        if (tenantReqs != null) {
          if (allh.contains("H")) {
            ctx.response().putHeader(hdr, tenantReqs);
          }
          if (allh.contains("B")) {
            xmlMsg.append(" " + hdr + ":" + tenantReqs + "\n");
          }
        }
      }
    }
    ctx.request().handler(x -> xmlMsg.append(x));
    ctx.request().endHandler(x -> {
      if (xmlMsg.length() > 0) {
        ctx.response().write(xmlMsg.toString());
      } else {
        ctx.response().write("hello");
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
      ctx.request().resume();
      if (res.failed()) {
        HttpResponse.responseError(ctx, res.getType(), res.cause());
      } else {
        HttpResponse.responseText(ctx, 200);
        ctx.request().endHandler(x -> {
          ctx.response().write(res.result());
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
    router.post("/test1").handler(this::myStreamHandle1);
    router.get("/test2").handler(this::myStreamHandle2);
    final int port = Integer.parseInt(System.getProperty("port", "9130"));

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
      .requestHandler(router::accept)
      .listen(
        port,
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
  public void test1(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.disableInfoLog();
    cli.enableInfoLog();

    cli.setOkapiToken("919");
    assertEquals("919", cli.getOkapiToken());

    cli.newReqId("920");

    cli.get("/test1", res -> {
      assertTrue(res.succeeded());
      assertEquals("hello", res.result());
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
      assertEquals("hello", res.result());
      test5(cli, async);
    });
  }

  private void test5(OkapiClient cli, Async async) {
    cli.get("/test2?p=%2Fbad", res -> {
      assertTrue(res.failed());
      async.complete();
    });
  }

}
