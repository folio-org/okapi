package org.folio.okapi.header;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.XOkapiHeaders;


/**
 * Test module that works with headers-only. Also implements a few other test
 * facilities, like supporting a _tenantPermissions interface.
 *
 */
public class MainVerticle extends AbstractVerticle {

  private final Logger logger = OkapiLogger.get();
  private JsonArray savedPermissions = new JsonArray();

  private void myHeaderHandle(RoutingContext ctx) {
    String h = ctx.request().getHeader("X-my-header");
    if (h == null) {
      h = "foo";
    } else {
      h += ",foo";
    }
    final String hv = h;
    ctx.response().putHeader("X-my-header", hv);
    ctx.request().endHandler(x -> ctx.response().end());
  }

  /**
   * Simple test to fake a _tenantPermission interface.
   * Saves permissions so they can be inspected later with /permsResult
   *
   * @param ctx routing context
   */
  private void myPermissionHandle(RoutingContext ctx) {
    ReadStream<Buffer> content = ctx.request();

    Buffer buf = Buffer.buffer();
    content.handler(buf::appendBuffer);
    content.endHandler(x -> {
      try {
        savedPermissions.add(new JsonObject(buf));
      } catch (Exception e) {
        ctx.response().setStatusCode(400);
        ctx.response().end(e.getMessage());
        return;
      }
      ctx.response().putHeader(XOkapiHeaders.TRACE, "GET test-header-module "
          + ctx.request().path() + " 200 -");
      ctx.response().end();
    });
  }

  private void myPermResult(RoutingContext ctx) {
    ctx.response().setStatusCode(200);
    ctx.response().putHeader("Content-Type", "application/json");
    ctx.response().end(savedPermissions.encodePrettily());
    savedPermissions.clear();
  }

  @Override
  public void start(Promise<Void> promise) throws IOException {
    Router router = Router.router(vertx);

    final int port = Integer.parseInt(System.getProperty("port", "8080"));
    logger.info("Starting okapi-test-header-module {} on port {}",
        ManagementFactory.getRuntimeMXBean().getName(), port);

    router.get("/testb").handler(this::myHeaderHandle);
    router.post("/testb").handler(this::myHeaderHandle);
    router.get("/permResult").handler(this::myPermResult);
    router.post("/_/tenantPermissions").handler(this::myPermissionHandle);

    vertx.createHttpServer()
        .requestHandler(router)
        .listen(port, result -> promise.handle(result.mapEmpty()));
  }
}
