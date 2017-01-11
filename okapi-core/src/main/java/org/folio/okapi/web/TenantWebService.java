package org.folio.okapi.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import static java.lang.Long.max;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.service.TenantManager;
import org.folio.okapi.service.TenantStore;
import static org.folio.okapi.common.ErrorType.*;
import static org.folio.okapi.common.HttpResponse.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.Success;
import org.folio.okapi.discovery.DiscoveryManager;
import org.folio.okapi.service.ModuleManager;

public class TenantWebService {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  final private Vertx vertx;
  TenantManager tenants;
  TenantStore tenantStore;
  EventBus eb;
  private final String eventBusName = "okapi.conf.tenants";
  private long lastTimestamp = 0;
  private DiscoveryManager discoveryManager;


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

  public TenantWebService(Vertx vertx, TenantManager tenantManager, TenantStore tenantStore,
                          DiscoveryManager discoveryManager ) {
    this.vertx = vertx;
    this.tenants = tenantManager;
    this.tenantStore = tenantStore;
    this.discoveryManager = discoveryManager;
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
        td.setId(UUID.randomUUID().toString());
      }
      final String id = td.getId();
      if (!id.matches("^[a-z0-9._-]+$")) {
        responseError(ctx, 400, "Invalid id");
      } else {
        Tenant t = new Tenant(td);
        final long ts = getTimestamp();
        t.setTimestamp(ts);
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
      final String id = ctx.request().getParam("id");
      if (!id.equals(td.getId())) {
        responseError(ctx, 400, "Tenant.id=" + td.getId() + " id=" + id);
        return;
      }
      Tenant t = new Tenant(td);
      final long ts = getTimestamp();
      t.setTimestamp(ts);
      if (tenants.updateDescriptor(td, ts)) {
        tenantStore.updateDescriptor(td, res -> {
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


  /**
   * Helper to actually enable the module. Mostly error handling.
   */
  private void enableModuleHelper(RoutingContext ctx,
               TenantModuleDescriptor td, String id, String module) {
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
  }

  public void enableModule(RoutingContext ctx) {
    try {
      final String id = ctx.request().getParam("id");
      final TenantModuleDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantModuleDescriptor.class);
      final String module = td.getId();
      ModuleManager modMan = tenants.getModuleManager();
      ModuleDescriptor md = modMan.get(module);
      if ( md == null ) {
        responseError(ctx, 400, "Unknown module " + module );
        return;
      }
      String tenInt = md.getTenantInterface();
      if (tenInt == null || tenInt.isEmpty() )
      {
        logger.debug("enableModule: " + module + " has no support for tenant init");
        enableModuleHelper(ctx, td, id, module);
      } else { // We have an init interface, invoke it
        discoveryManager.get(module, gres->{
          if ( gres.failed()) {
            responseError(ctx, gres.getType(), gres.cause());
          } else {
            List<DeploymentDescriptor> instances = gres.result();
            if (instances.isEmpty()) {
              responseError(ctx, 400, "No running instances for module " + module
                + ". Can not invoke tenant init" );
            } else { // TODO - Don't just take the first. Pick one by random.
              String baseurl = instances.get(0).getUrl();
              logger.debug("enableModule Url: " + baseurl + " and "  + tenInt);
              Map<String,String> headers = new HashMap<>();
              for (String hdr : ctx.request().headers().names()) {
                if ( hdr.matches("^X-.*$")) {
                  headers.put(hdr, ctx.request().headers().get(hdr));
                }
              }
              headers.put("Accept", "*/*");
              headers.put("Content-Type", "application/json; charset=UTF-8");
              String body = "{}"; // dummy
              OkapiClient cli = new OkapiClient(baseurl, vertx, headers);
              cli.request(HttpMethod.POST, tenInt, body, cres->{
                if ( cres.failed()) {
                  logger.warn("Tenant init request for "
                    + module + " failed with " + cres.cause().getMessage());
                  responseError(ctx, 500, "Post to /tenant on " + module + " failed with "
                     + cres.cause().getMessage());
                } else { // All well, we can finally enable it
                  enableModuleHelper(ctx, td, id, module);
                  logger.debug("enableModule: Init request to " + module + " succeeded");
                }
              });
            }
          }
        });
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
          responseError(ctx, 404, mod);
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
