/*
 * Copyright (C) 2015 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;
import okapi.bean.Tenant;
import okapi.bean.TenantDescriptor;
import okapi.bean.TenantModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import static java.lang.Long.max;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import okapi.service.TenantManager;
import okapi.service.TenantStore;
import static okapi.common.ErrorType.*;
import static okapi.common.HttpResponse.*;
import okapi.common.ExtendedAsyncResult;
import okapi.common.Failure;
import okapi.common.Success;

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
        responseError(ctx, 400, "No Id in tenant");
      } else if (!td.getId().matches("^[a-z0-9._-]+$")) {
        responseError(ctx, 400, "Invalid id");
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
          responseError(ctx, 400, "Duplicate id " + id);
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
      if (tenants.updateDescriptor(id, td, ts)) {
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
        responseError(ctx, 400, "Failed to update descriptor " + id);
      }
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void list(RoutingContext ctx) {
    tenantStore.listTenants(res -> {
      if (res.succeeded()) {
        List<Tenant> tl = res.result();
        List<TenantDescriptor> tdl = new ArrayList<>();
        for (Tenant t : tl) {
          tdl.add(t.getDescriptor());
        }
        String s = Json.encodePrettily(tdl);
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
      } else {
        responseError(ctx, res.getType(), res.cause());
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
          responseError(ctx, res.getType(), res.cause());
        }
      });
    } else {
      responseError(ctx, 404, id);
    }
  }

  public void enableModule(RoutingContext ctx) {
    try {
      final String id = ctx.request().getParam("id");
      final TenantModuleDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantModuleDescriptor.class);
      final String module = td.getId();
      // TODO - Validate we know about that module!
      final long ts = getTimestamp();
      String err = tenants.enableModule(id, module);
      if (err.isEmpty()) {
        tenantStore.enableModule(id, module, ts, res -> {
          if (res.succeeded()) {
            sendReloadSignal(id, ts);
            responseJson(ctx, 200).end(Json.encodePrettily(td));
          } else {
            responseError(ctx, res.getType(), res.cause());
          }
        });

      } else if (err.contains("not found")) {
        responseError(ctx, 404, err);
      } else { // TODO - handle this right
        responseError(ctx, 400, err);
      } // Missing dependencies are bad requests...
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void disableModule(RoutingContext ctx) {
    try {
      final String id = ctx.request().getParam("id");
      final String module = ctx.request().getParam("mod");
      final long ts = getTimestamp();
      logger.debug("disablemodule t=" + id + " m=" + module);
      String err = tenants.disableModule(id, module);
      if (err.isEmpty()) {
        tenantStore.disableModule(id, module, ts, res -> {
          if (res.succeeded()) {
            sendReloadSignal(id, ts);
            responseText(ctx, 204).end();
          } else if (res.getType() == NOT_FOUND) { // Oops, things are not in sync any more!
            logger.debug("disablemodule: storage NOTFOUND: " + res.cause().getMessage());
            responseError(ctx, 404, res.cause());
          } else {
            responseError(ctx, res.getType(), res.cause());
          }
        });
      } else if (err.contains("not found")) {
        logger.error("disableModule: " + err);
        responseError(ctx, 404, err);
      } else {
        logger.error("disableModule: " + err);
        responseError(ctx, 400, err);
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
        Set<String> ml = t.listModules();  // Convert the list of module names
        Iterator<String> mli = ml.iterator();  // into a list of objects
        ArrayList<TenantModuleDescriptor> ta = new ArrayList<>();
        while (mli.hasNext()) {
          TenantModuleDescriptor tmd = new TenantModuleDescriptor();
          tmd.setId(mli.next());
          ta.add(tmd);
        }
        String s = Json.encodePrettily(ta);
        responseJson(ctx, 200).end(s);
      } else {
        responseError(ctx, res.getType(), res.cause());
      }
    });
  }

  public void getModule(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    final String mod = ctx.request().getParam("mod");
    tenantStore.get(id, res -> {
      if (res.succeeded()) {
        Tenant t = res.result();
        Set<String> ml = t.listModules();  // Convert the list of module names
        if (ml.contains(mod)) {
          TenantModuleDescriptor tmd = new TenantModuleDescriptor();
          tmd.setId(mod);
          String s = Json.encodePrettily(tmd);
          responseJson(ctx, 200).end(s);
        } else {
          responseError(ctx, 404, res.cause());
        }
      } else {
        responseError(ctx, res.getType(), res.cause());
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

  private void loadR(Iterator<Tenant> it,
          Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) {
      logger.info("All tenants deployed");
      fut.handle(new Success<>());
    } else {
      Tenant t = it.next();
      tenants.insert(t);
      loadR(it, fut);
    }
  }

  public void loadTenants(Handler<ExtendedAsyncResult<Void>> fut) {
    tenantStore.listTenants(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        Iterator<Tenant> it = res.result().iterator();
        loadR(it, fut);
      }
    });
  }
} // class
