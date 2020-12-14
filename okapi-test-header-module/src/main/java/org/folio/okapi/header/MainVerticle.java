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
import java.util.HashMap;
import java.util.Map;
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
  private Map<String,JsonArray> savedPermissions = new HashMap<>();

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
    String tenant = ctx.request().getHeader(XOkapiHeaders.TENANT);
    ReadStream<Buffer> content = ctx.request();

    Buffer buf = Buffer.buffer();
    content.handler(buf::appendBuffer);
    content.endHandler(x -> {
      savedPermissions.putIfAbsent(tenant, new JsonArray());
      try {
        savedPermissions.get(tenant).add(new JsonObject(buf));
      } catch (Exception e) {
        ctx.response().setStatusCode(400);
        ctx.response().end(e.getMessage());
        return;
      }
      ctx.response().putHeader(XOkapiHeaders.TRACE, ctx.request().method().name()
          + " test-header-module " + ctx.request().path() + " 200 -");
      ctx.response().end();
    });
  }

  private void myPermResult(RoutingContext ctx) {
    String tenant = ctx.request().getHeader(XOkapiHeaders.TENANT);
    ctx.response().setStatusCode(200);
    ctx.response().putHeader("Content-Type", "application/json");
    JsonArray ar = savedPermissions.get(tenant);
    ctx.response().end(ar != null ? savedPermissions.get(tenant).encodePrettily() : "");
    savedPermissions.remove(tenant);
  }

  @Override
  public void start(Promise<Void> promise) throws IOException {
    Router router = Router.router(vertx);

    final int port = Integer.parseInt(
        System.getProperty("http.port", System.getProperty("port", "8080")));
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
