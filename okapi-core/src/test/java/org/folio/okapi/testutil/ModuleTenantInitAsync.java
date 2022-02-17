package org.folio.okapi.testutil;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.ModuleHandle;

public class ModuleTenantInitAsync implements ModuleHandle {
  private static final Logger logger = OkapiLogger.get();

  private Vertx vertx;
  private int port;
  private HttpServer server;
  private boolean omitIdInResponse = false;
  private boolean omitLocationInResponse = false;
  private boolean badJsonResponse = false;
  private int getStatusCode = 200;
  private boolean purgeFail = false;
  private String errorMessage;
  private JsonArray additionalMessages;
  private Map<String,JsonObject> jobs = new HashMap<>();
  private List<JsonObject> operations = new LinkedList<>();
  private Map<String, Instant> startTime = new HashMap<>();
  private Map<String, Instant> endTime = new HashMap<>();

  public ModuleTenantInitAsync(Vertx vertx, int port) {
    this.vertx = vertx;
    this.port = port;
  }

  public Instant getStartTime(String module) {
    return startTime.get(module);
  }

  public Instant getEndTime(String module) {
    return endTime.get(module);
  }

  public List<JsonObject> getOperations() {
    return operations;
  }

  public void setOmitIdInResponse(boolean omitIdInResponse) {
    this.omitIdInResponse = omitIdInResponse;
  }

  public void setOmitLocationInResponse(boolean omitLocationInResponse) {
    this.omitLocationInResponse = omitLocationInResponse;
  }

  public void setPurgeFail(boolean purgeFail) {
    this.purgeFail = purgeFail;
  }

  public void setBadJsonResponse(boolean badJsonResponse) {
    this.badJsonResponse = badJsonResponse;
  }

  public void setGetStatusResponse(int statusCode) {
    this.getStatusCode = statusCode;
  }

  public void setErrorMessage(String errorMessage, JsonArray additionalMessages) {
    this.errorMessage = errorMessage;
    this.additionalMessages = additionalMessages;
  }

  void tenantPost(RoutingContext ctx) {
    String path = ctx.request().path();
    if (path.equals("/_/tenant")) {
      JsonObject tenantSchema = ctx.getBodyAsJson();
      operations.add(tenantSchema);
      if (tenantSchema.getBoolean("purge", false))
        if (purgeFail) {
          ctx.response().putHeader("Content-Type", "text/plain");
          ctx.response().setStatusCode(400);
          ctx.response().end("purge failed");
        } else {
          ctx.response().setStatusCode(204);
          ctx.response().end();
        return;
      }
      JsonObject obj = ctx.getBodyAsJson();
      String module = obj.getString("module_to", obj.getString("module_from"));
      if (module != null) {
        startTime.put(module, Instant.now());
      }
      obj.put("count", 2);
      obj.put("complete", Boolean.FALSE);
      String id = UUID.randomUUID().toString();
      if (!omitIdInResponse) {
        obj.put("id", id);
      }
      jobs.put(id, obj);
      ctx.response().setStatusCode(201);
      ctx.response().putHeader("Content-Type", "application/json");
      if (!omitLocationInResponse) {
        ctx.response().putHeader("Location", "/_/tenant/" + id);
      }
      ctx.end(badJsonResponse ? "}" : obj.encodePrettily());
    } else {
      ctx.response().setStatusCode(404);
      ctx.response().putHeader("Content-Type", "text/plain");
      ctx.response().end("Not found");
    }
  }

  void tenantGet(RoutingContext ctx) {
    String path = ctx.request().path();
    String id = path.substring(path.lastIndexOf('/') + 1);
    JsonObject obj = jobs.get(id);
    if (obj == null) {
      ctx.response().setStatusCode(404);
      ctx.response().putHeader("Content-Type", "text/plain");
      ctx.response().end("Not found " + id);
      return;
    }
    int count = obj.getInteger("count");
    obj.put("count", --count);
    obj.put("complete", count <= 0);

    String module = obj.getString("module_to", obj.getString("module_from"));
    if (module != null) {
      startTime.put(module, Instant.now());
    }
    if (module != null && count <= 0) {
      endTime.put(module, Instant.now());
    }
    if (errorMessage != null) {
      obj.put("error", errorMessage);
    }
    if (additionalMessages != null) {
      obj.put("messages", additionalMessages);
    }
    ctx.response().setStatusCode(getStatusCode);
    ctx.response().putHeader("Content-Type", "application/json");
    ctx.response().putHeader("Location", "/_/tenant/" + id);
    ctx.end(obj.encodePrettily());
  }

  void tenantDelete(RoutingContext ctx) {
    String path = ctx.request().path();
    String id = path.substring(path.lastIndexOf('/') + 1);
    if (jobs.remove(id) == null) {
      ctx.response().setStatusCode(404);
      ctx.response().putHeader("Content-Type", "text/plain");
      ctx.response().end("Not found " + id);
      return;
    }
    ctx.response().setStatusCode(204);
    ctx.response().end();
  }

  @Override
  public Future<Void> start() {
    Router router = Router.router(vertx);

    router.post("/_/tenant").handler(BodyHandler.create());
    router.post("/_/tenant").handler(this::tenantPost);
    router.getWithRegex("/_/tenant/.*").handler(this::tenantGet);
    router.deleteWithRegex("/_/tenant/.*").handler(this::tenantDelete);

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
