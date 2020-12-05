package org.folio.okapi.testutil;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.ModuleHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModuleTenantInitAsync implements ModuleHandle {
  private static final Logger logger = OkapiLogger.get();

  private Vertx vertx;
  private String id;
  private int port;
  private HttpServer server;
  private Map<String,JsonObject> jobs = new HashMap<>();

  public ModuleTenantInitAsync(Vertx vertx, String id, int port) {
    this.vertx = vertx;
    this.id = id;
    this.port = port;
  }

  public void tenantGet(RoutingContext ctx) {
    HttpMethod method = ctx.request().method();
    String path = ctx.request().path();
    String id = path.substring(path.lastIndexOf('/') + 1);
    JsonObject obj = jobs.get(id);
    if (obj == null) {
      ctx.response().setStatusCode(404);
      ctx.response().putHeader("Content-Type", "text/plain");
      ctx.response().end("Not found " + id);
      return;
    }
    obj.put("complete", Boolean.TRUE);
    ctx.response().setStatusCode(200);
    ctx.response().putHeader("Content-Type", "application/json");
    ctx.response().putHeader("Location", "/_/tenant/" + id);
    ctx.end(obj.encodePrettily());
  }

  public void tenantPost(RoutingContext ctx) {
    HttpMethod method = ctx.request().method();
    String path = ctx.request().path();
    if (HttpMethod.POST.equals(method) && path.equals("/_/tenant")) {
      JsonObject obj = ctx.getBodyAsJson();
      obj.put("complete", Boolean.FALSE);
      String id = UUID.randomUUID().toString();
      obj.put("id", id);
      jobs.put(id, obj);
      ctx.response().setStatusCode(201);
      ctx.response().putHeader("Content-Type", "application/json");
      ctx.response().putHeader("Location", "/_/tenant/" + id);
      ctx.end(obj.encodePrettily());
    } else {
      ctx.response().setStatusCode(404);
      ctx.response().putHeader("Content-Type", "text/plain");
      ctx.response().end("Not found");
    }
  }

  @Override
  public Future<Void> start() {
    Router router = Router.router(vertx);

    router.post("/_/tenant").handler(BodyHandler.create());
    router.post("/_/tenant").handler(this::tenantPost);
    router.getWithRegex("/_/tenant/.*").handler(this::tenantGet);

    return vertx.createHttpServer()
        .requestHandler(router)
        .listen(port)
        .compose(x -> { server = x; return Future.succeededFuture(); });
  }

  @Override
  public Future<Void> stop() {
    return server == null ? Future.succeededFuture() : server.close();
  }
}
