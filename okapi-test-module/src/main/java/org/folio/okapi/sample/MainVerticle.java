package org.folio.okapi.sample;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class MainVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger("okapi-test-module");

  public void my_stream_handle(RoutingContext ctx) {
    ctx.response().setStatusCode(200);
    final String ctype = ctx.request().headers().get("Content-Type");
    String xmlMsg = "";
    if (ctype != null && ctype.toLowerCase().contains("xml")) {
      xmlMsg = " (XML) ";
    }
    String hv = ctx.request().getHeader("X-my-header");
    if (hv != null) {
      xmlMsg += hv;
    }
    ctx.response().putHeader("Content-Type", "text/plain");

    // Report all headers back (in headers and in the body) if requested
    String allh = ctx.request().getHeader("X-all-headers");
    if (allh != null) {
      for (String hdr : ctx.request().headers().names()) {
        hv = ctx.request().getHeader(hdr);
        if (hv != null) {
          if (allh.contains("H")) {
            ctx.response().putHeader(hdr, hv);
          }
          if (allh.contains("B")) {
            xmlMsg += " " + hdr + ":" + hv;
          }
        }
      }
    }
    String stopper = ctx.request().getHeader("X-stop-here");
    if (stopper != null ) {
      ctx.response().putHeader("X-Okapi-Stop", stopper);
    }

    final String xmlMsg2 = xmlMsg; // it needs to be final, in the callbacks

    if (ctx.request().method().equals(HttpMethod.GET)) {
      ctx.request().endHandler(x -> {
        ctx.response().end("It works" + xmlMsg2);
      });
    } else {
      ctx.response().setChunked(true);
      ctx.response().write("Hello " + xmlMsg2);
      ctx.request().handler(x -> {
        ctx.response().write(x);
      });
      ctx.request().endHandler(x -> {
        ctx.response().end();
      });
    }
  }

  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);

    final int port = Integer.parseInt(System.getProperty("port", "8080"));
    logger.info("Starting okapi-test-module " + ManagementFactory.getRuntimeMXBean().getName() + " on port " + port);
    //enable reading body to string

    router.get("/testb").handler(this::my_stream_handle);
    router.post("/testb").handler(this::my_stream_handle);

    HttpServerOptions so = new HttpServerOptions()
            .setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
            .requestHandler(router::accept)
            .listen(
                    port,
                    result -> {
                      if (result.succeeded()) {
                        fut.complete();
                      } else {
                        fut.fail(result.cause());
                        logger.error("okapi-test-module failed: " + result.cause());
                      }
                    }
            );
  }
}
