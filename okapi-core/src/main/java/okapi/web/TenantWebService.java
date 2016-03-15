/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;
import okapi.bean.Tenant;
import okapi.bean.TenantDescriptor;
import okapi.bean.TenantModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import static java.lang.Long.max;
import okapi.service.TenantManager;
import okapi.service.TenantStore;
import okapi.util.ErrorType;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

public class TenantWebService {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  final private Vertx vertx;
  TenantManager tenants;
  TenantStore tenantStore;
  EventBus eb;
  private final String eventBusName = "okapi.conf.tenants";
  private long lastTimestamp = 0;

  private static class ReloadSignal {

    @JsonProperty
    String id = "";
    @JsonProperty
    long timestamp = 0;

    ReloadSignal(@JsonProperty("id") String id, @JsonProperty("timestamp") long timestamp) {
      this.id = id;
      this.timestamp = timestamp;
    }
  } // reloadSignal

  private void responseError(RoutingContext ctx, int code, Throwable cause) {
    responseText(ctx, code).end(cause.getMessage());
  }

  private HttpServerResponse responseText(RoutingContext ctx, int code) {
    return ctx.response().setStatusCode(code).putHeader("Content-Type", "text/plain");
  }

  private HttpServerResponse responseJson(RoutingContext ctx, int code) {
    return ctx.response().setStatusCode(code).putHeader("Content-Type", "application/json");
  }

  private void sendReloadSignal(String id, long ts) {
    ReloadSignal sig = new ReloadSignal(id, ts);
    String js = Json.encode(sig);
    eb.publish(eventBusName, js);
  }

  public TenantWebService(Vertx vertx, TenantManager tenantManager, TenantStore tenantStore) {
    this.vertx = vertx;
    this.tenants = tenantManager;
    this.tenantStore = tenantStore;
    this.eb = vertx.eventBus();
    eb.consumer(eventBusName, message -> {
      ReloadSignal sig = Json.decodeValue(message.body().toString(), ReloadSignal.class);
      if (this.lastTimestamp < sig.timestamp) {
        reloadTenant(sig.id, res -> {
          if (res.succeeded()) {
            this.lastTimestamp = max(this.lastTimestamp, sig.timestamp);
          } else {
            // TODO - What to do in this case. Nowhere to report any errors.
            logger.fatal("Reloading tenant " + sig.id
                    + "FAILED. Don't know what to do about that. PANIC!");
          }
        });
      }
    });

  }

  /**
   * Get a timestamp value. Checks that it is always increasing, even if the
   * clock goes backwards as it will do with daylight saving time etc.
   *
   * @return
   */
  private long getTimestamp() {
    long ts = System.currentTimeMillis();
    if (ts < lastTimestamp) // the clock jumping backwards, or something
    {
      ts = lastTimestamp + 1;
    }
    lastTimestamp = ts;
    return ts;
  }

  public void create(RoutingContext ctx) {
    try {
      final TenantDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantDescriptor.class);
      if (td.getId() == null || td.getId().isEmpty()) {
        responseText(ctx, 400).end("No Id in tenant");
      } else if (!td.getId().matches("^[a-z0-9._-]+$")) {
        responseText(ctx, 400).end("Invalid id");
      } else {
        Tenant t = new Tenant(td);
        final long ts = getTimestamp();
        t.setTimestamp(ts);
        final String id = td.getId();
        if (tenants.insert(t)) {
          tenantStore.insert(t, res -> {
            if (res.succeeded()) {
              final String uri = ctx.request().uri() + "/" + id;
              final String s = Json.encodePrettily(t.getDescriptor());
              responseJson(ctx, 201).putHeader("Location", uri).end(s);
              sendReloadSignal(id, ts);
            } else {
              // This should never happen in a well behaving system. It is 
              // possible with some race conditions etc. Hard to test...
              // TODO - Check what errors the mongo store can return
              logger.error("create: Db layer error " + res.cause().getMessage());
              tenants.delete(id); // Take it away from the runtime, since it was no good.
              responseError(ctx, 400, res.cause());
            }
          });
        } else {
          responseText(ctx, 400).end("Duplicate id " + id);
        }
      }
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void update(RoutingContext ctx) {
    try {
      final TenantDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantDescriptor.class);
      Tenant t = new Tenant(td);
      final long ts = getTimestamp();
      t.setTimestamp(ts);
      final String id = td.getId();
      if (tenants.updateDescriptor(id,td,ts)) {
        tenantStore.updateDescriptor(id, td, res -> {
          if (res.succeeded()) {
            final String s = Json.encodePrettily(t.getDescriptor());
            responseJson(ctx, 200).end(s);
            sendReloadSignal(id, ts);
          } else {
            responseError(ctx, 404, res.cause());
          }
        });
      } else {
        responseText(ctx, 400).end("Failed to update descriptor " + id);
      }
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void list(RoutingContext ctx) {
    tenantStore.listTenants(res -> {
      if (res.succeeded()) {
        String s = Json.encodePrettily(res.result());
        responseJson(ctx, 200).end(s);
      } else {
        responseError(ctx, 400, res.cause());
      }
    });
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    tenantStore.get(id, res -> {
      if (res.succeeded()) {
        Tenant t = res.result();
        String s = Json.encodePrettily(t.getDescriptor());
        responseJson(ctx, 200).end(s);
      } else if (res.getType() == NOT_FOUND) {
        responseError(ctx, 404, res.cause());
      } else {
        responseError(ctx, 500, res.cause());
      }
    });
  }

  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    if (tenants.delete(id)) {
      tenantStore.delete(id, res -> {
        if (res.succeeded()) {
          final long ts = getTimestamp();
          sendReloadSignal(id, ts);
          responseText(ctx, 204).end();
        } else {
          responseError(ctx, 500, res.cause());
        }
      });
    } else {
      responseText(ctx, 404).end(id);
    }
  }

  public void enableModule(RoutingContext ctx) {
    try {
      final String id = ctx.request().getParam("id");
      final TenantModuleDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantModuleDescriptor.class);
      final String module = td.getModule();
      // TODO - Validate we know about that module!
      final long ts = getTimestamp();
      ErrorType err = tenants.enableModule(id, module);
      if (err == OK) {
        tenantStore.enableModule(id, module, ts, res -> {
          if (res.succeeded()) {
            sendReloadSignal(id, ts);
            responseText(ctx, 200).end(); // 204 - no content??
          } else if (res.getType() == NOT_FOUND) {
            responseError(ctx, 404, res.cause());
          } else {
            responseError(ctx, 500, res.cause());
          }
        });

      } else if (err == NOT_FOUND) {
        responseText(ctx, 404).end("Tenant " + id + " not found (enableModule)");
      } else {
        responseText(ctx, 500).end();
      }
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void disableModule(RoutingContext ctx) {
    try {
      final String id = ctx.request().getParam("id");
      final String module = ctx.request().getParam("mod");
      final long ts = getTimestamp();
      logger.debug("disablemodule t=" + id + " m=" + module );
      ErrorType err = tenants.disableModule(id, module);
      if (err == OK) {
        tenantStore.disableModule(id, module, ts, res -> {
          if (res.succeeded()) {
            sendReloadSignal(id, ts);
            responseText(ctx, 204).end();
          } else if (res.getType() == NOT_FOUND) {
            logger.debug("disablemodule: storage NOTFOUND: " + res.cause().getMessage());
            responseError(ctx, 404, res.cause());
          } else {
            logger.error("disablemodule: storage other " + res.cause().getMessage());
            responseError(ctx, 500, res.cause());
          }
        });

      } else if (err == USER) {
        logger.error("disableModule: tenantManager: USER");
        responseText(ctx, 404).end("Tenant " + id + " not found (disableModule)");
      } else if (err == NOT_FOUND) {
        logger.error("disableModule: tenantManager: NOT_FOUND");
        responseText(ctx, 404).end("Tenant " + id + " has no module " + module + " (disableModule)");
      } else {
        logger.error("disableModule: tenantManager: Other error");
        responseText(ctx, 500).end();
      }
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void listModules(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    tenantStore.get(id, res -> {
      if (res.succeeded()) {
        Tenant t = res.result();
        String s = Json.encodePrettily(t.listModules());
        ctx.response().setStatusCode(200).end(s);
      } else if (res.getType() == NOT_FOUND) {
        responseError(ctx, 404, res.cause());
      } else {
        responseError(ctx, 500, res.cause());
      }
    });
  }

  public void reloadTenant(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    reloadTenant(id, res -> {
      if (res.succeeded()) {
        responseText(ctx, 204).end();
      } else {
        responseError(ctx, 500, res.cause());
      }
    });
  }

  public void reloadTenant(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    tenantStore.get(id, res -> {
      if (res.succeeded()) {
        Tenant t = res.result();
        tenants.delete(id);
        if (tenants.insert(t)) {
          logger.debug("Reloaded tenant " + id);
          fut.handle(new Success<>());
        } else {
          logger.error("Reloading of tenant " + id + " FAILED");
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        }
      } else if (res.getType() == NOT_FOUND) {  // that's OK, it has been deleted
        tenants.delete(id); // ignore result code, ok to delete nonexisting
        logger.debug("reload deleted tenant " + id);
        fut.handle(new Success<>());
      } else {
        logger.error("Reload tenant " + id + "Failed: " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }
} // class
