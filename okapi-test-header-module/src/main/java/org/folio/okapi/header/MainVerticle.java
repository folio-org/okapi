package org.folio.okapi.header;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.IOException;
import java.lang.management.ManagementFactory;

/**
 * Test module that works with headers-only. Also implements a few other test
 * facilities, like supporting a _tenantPermissions interface.
 *
 */
public class MainVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger("okapi-test-header-module");

  public void my_header_handle(RoutingContext ctx) {
    String h = ctx.request().getHeader("X-my-header");
    if (h == null) {
      h = "foo";
    } else {
      h += ",foo";
    }
    final String hv = h;
    ctx.response().putHeader("X-my-header", hv);
    ctx.request().endHandler(x -> {
      ctx.response().end();
    });
  }

  /**
   * Simple test to fake a _tenantPermission interface.
   * Captures the body, and reports it in a header.
   *
   * @param ctx
   */
  public void my_tenantPermissions_handle(RoutingContext ctx) {
    ReadStream<Buffer> content = ctx.request();
    final Buffer incoming = Buffer.buffer();
    content.handler(data -> {
      incoming.appendBuffer(data);
    });
    ctx.request().endHandler(x -> {
      String body = incoming.toString();
      body = body.replaceAll("\\s+", " "); // remove newlines etc
      ctx.response().putHeader("X-Tenant-Perms-Result", body);
      if (body.length() > 80) {
        body = body.substring(0, 80) + "...";
      }
      logger.info("tenantPermissions: " + body);
      ctx.response().end();
    });
  }

  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);

    final int port = Integer.parseInt(System.getProperty("port", "8080"));
    logger.info("Starting okapi-test-header-module "
      + ManagementFactory.getRuntimeMXBean().getName()
      + " on port " + port);

    router.get("/testb").handler(this::my_header_handle);
    router.post("/testb").handler(this::my_header_handle);
    router.post("/_/tenantPermissions")
      .handler(this::my_tenantPermissions_handle);

    vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(
                    port,
                    result -> {
                      if (result.succeeded()) {
                        fut.complete();
                      } else {
                        fut.fail(result.cause());
                        logger.error("okapi-test-header-module failed: " + result.cause());
                      }
                    }
            );
  }
}
