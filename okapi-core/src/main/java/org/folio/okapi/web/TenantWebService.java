package org.folio.okapi.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
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
import org.folio.okapi.bean.ModuleInterface;
import org.folio.okapi.bean.PermissionList;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.service.TenantManager;
import org.folio.okapi.service.TenantStore;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.discovery.DiscoveryManager;
import org.folio.okapi.service.ModuleManager;
import org.folio.okapi.util.ProxyContext;

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
              + " FAILED. Don't know what to do about that. PANIC!");
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

  /**
   * Dirty hack to create a record in memory and storage. To be used in the
   * boot-up process.
   *
   * TODO - Refactor all this stuff into the tenantManager, maybe using a shared
   * memory map instead of reloading databases.
   *
   * @param td
   * @param fut
   */
  public void createInternally(TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = td.getId();
    Tenant t = new Tenant(td);
    final long ts = getTimestamp();
    if (tenants.insert(t)) {
      tenantStore.insert(t, res -> {
        if (res.succeeded()) {
          fut.handle(new Success<>());
          sendReloadSignal(id, ts);
        } else {
          // This should never happen in a well behaving system. It is
          // possible with some race conditions etc. Hard to test.
          tenants.delete(id); // Take it away from the runtime, since it was no good.
          fut.handle(new Failure<>(INTERNAL, "Failed to create tenant " + id));
        }
      });
    } else {
      fut.handle(new Failure<>(USER, "Duplicate tenant " + id));
    }

  }

  public void create(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.create");
    try {
      final TenantDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantDescriptor.class);
      if (td.getId() == null || td.getId().isEmpty()) {
        td.setId(UUID.randomUUID().toString());
      }
      final String id = td.getId();
      if (!id.matches("^[a-z0-9._-]+$")) {
        pc.responseError(400, "Invalid id");
      } else {
        Tenant t = new Tenant(td);
        final long ts = getTimestamp();
        t.setTimestamp(ts);
        if (tenants.insert(t)) {
          tenantStore.insert(t, res -> {
            if (res.succeeded()) {
              final String uri = ctx.request().uri() + "/" + id;
              final String s = Json.encodePrettily(t.getDescriptor());
              pc.responseJson(201, s, uri);
              sendReloadSignal(id, ts);
            } else {
              // This should never happen in a well behaving system. It is
              // possible with some race conditions etc. Hard to test.
              // TODO - Check what errors the mongo store can return
              pc.error("create: Db layer error " + res.cause().getMessage());
              tenants.delete(id); // Take it away from the runtime, since it was no good.
              pc.responseError(400, res.cause());
            }
          });
        } else {
          pc.responseError(400, "Duplicate id " + id);
        }
      }
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void update(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.update");
    try {
      final TenantDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantDescriptor.class);
      final String id = ctx.request().getParam("id");
      if (!id.equals(td.getId())) {
        pc.responseError(400, "Tenant.id=" + td.getId() + " id=" + id);
        return;
      }
      Tenant t = new Tenant(td);
      final long ts = getTimestamp();
      t.setTimestamp(ts);
      if (tenants.updateDescriptor(td, ts)) {
        tenantStore.updateDescriptor(td, res -> {
          if (res.succeeded()) {
            final String s = Json.encodePrettily(t.getDescriptor());
            pc.responseJson(200, s);
            sendReloadSignal(id, ts);
          } else {
            pc.responseError(404, res.cause());
          }
        });
      } else {
        pc.responseError(400, "Failed to update descriptor " + id);
      }
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void list(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.list");
    tenantStore.listTenants(res -> {
      if (res.succeeded()) {
        List<Tenant> tl = res.result();
        List<TenantDescriptor> tdl = new ArrayList<>();
        for (Tenant t : tl) {
          tdl.add(t.getDescriptor());
        }
        String s = Json.encodePrettily(tdl);
        pc.responseJson(200, s);
      } else {
        pc.responseError(400, res.cause());
      }
    });
  }

  public void get(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.get");
    final String id = ctx.request().getParam("id");
    tenantStore.get(id, res -> {
      if (res.succeeded()) {
        Tenant t = res.result();
        String s = Json.encodePrettily(t.getDescriptor());
        pc.responseJson(200, s);
      } else {
        pc.responseError(res.getType(), res.cause());
      }
    });
  }

  public void delete(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.delete");
    final String id = ctx.request().getParam("id");
    if (XOkapiHeaders.SUPERTENANT_ID.equals(id)) {
      pc.responseError(403, "Can not delete the superTenant " + id);
      return;
    }
    if (tenants.delete(id)) {
      tenantStore.delete(id, res -> {
        if (res.succeeded()) {
          final long ts = getTimestamp();
          sendReloadSignal(id, ts);
          pc.responseText(204, "");
        } else {
          pc.responseError(res.getType(), res.cause());
        }
      });
    } else {
      pc.responseError(404, id);
    }
  }

  public void enableModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.enablemodule");
    enableTenantInt(pc, null);
  }

  public void updateModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.enablemodule");
    final String module_from = ctx.request().getParam("mod");
    enableTenantInt(pc, module_from);
  }

  /**
   * Helper to make request headers for the system requests we make.
   */
  private Map<String, String> reqHeaders(RoutingContext ctx, String tenantId) {
    Map<String, String> headers = new HashMap<>();
    for (String hdr : ctx.request().headers().names()) {
      if (hdr.matches("^X-.*$")) {
        headers.put(hdr, ctx.request().headers().get(hdr));
      }
    }
    if (!headers.containsKey(XOkapiHeaders.TENANT)) {
      headers.put(XOkapiHeaders.TENANT, tenantId);
      logger.debug("Added " + XOkapiHeaders.TENANT + " : " + tenantId);
    }
    headers.put("Accept", "*/*");
    headers.put("Content-Type", "application/json; charset=UTF-8");
    return headers;
  }

  /**
   * Enable tenant, part 1: Dependency check and call the tenant interface. This
   * is done first, as it is the most likely to fail. The tenant interface
   * service should be idempotent, so in case of failures, we can call it again.
   */
  private void enableTenantInt(ProxyContext pc, String module_from) {
    RoutingContext ctx = pc.getCtx();
    try {
      final String id = ctx.request().getParam("id");
      final TenantModuleDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
        TenantModuleDescriptor.class);
      final String module_to = td.getId();
      Tenant tenant = tenants.get(id);
      if (tenant == null) {
        String err = "tenant " + id + " not found";
        pc.responseError(404, err);
        return;
      }
      String err = tenants.updateModuleDepCheck(tenant, module_from, module_to);
      if (!err.isEmpty()) {
        pc.responseError(400, err);
        return;
      }
      String tenInt = tenants.getTenantInterface(module_to);
      if (tenInt == null || tenInt.isEmpty()) {
        pc.debug("enableModule: " + module_to + " has no support for tenant init");
        enablePermissions(pc, td, id, module_from, module_to);
      } else { // We have an init interface, invoke it
        discoveryManager.get(module_to, gres -> {
          if (gres.failed()) {
            pc.responseError(gres.getType(), gres.cause());
          } else {
            List<DeploymentDescriptor> instances = gres.result();
            if (instances.isEmpty()) {
              pc.responseError(400, "No running instances for module " + module_to
                + ". Can not invoke tenant init");
            } else { // TODO - Don't just take the first. Pick one by random.
              String baseurl = instances.get(0).getUrl();
              pc.debug("enableModule Url: " + baseurl + " and " + tenInt);
              Map<String, String> headers = reqHeaders(ctx, id);
              JsonObject jo = new JsonObject();
              jo.put("module_to", module_to);
              if (module_from != null) {
                jo.put("module_from", module_from);
              }
              OkapiClient cli = new OkapiClient(baseurl, vertx, headers);
              cli.newReqId("tenant");
              cli.enableInfoLog();
              cli.request(HttpMethod.POST, tenInt, jo.encodePrettily(), cres -> {
                if (cres.failed()) {
                  pc.warn("Tenant init request for "
                    + module_to + " failed with " + cres.cause().getMessage());
                  pc.responseError(500, "Post to " + tenInt
                    + " on " + module_to + " failed with "
                    + cres.cause().getMessage());
                } else { // All well, we can finally enable it
                  pc.debug("enableModule: Tenant init request to "
                    + module_to + " succeeded");
                  enablePermissions(pc, td, id, module_from, module_to);
                }
              });
            }
          }
        });
      }
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  /**
   * Enable tenant, part 2: Pass the module permission(set)s to perms.
   */
  private void enablePermissions(ProxyContext pc, TenantModuleDescriptor td,
    String id, String module_from, String module_to) {
    RoutingContext ctx = pc.getCtx();
    // TODO - Use the same pc for the whole chain, log repsonses
    ModuleManager modMan = tenants.getModuleManager();
    if (modMan == null) { // Should never happen
      pc.responseError(500, "enablePermissions: No moduleManager found. "
        + "Can not make _tenantPermissions request");
      return;
    }
    ModuleDescriptor permsModule;
    ModuleDescriptor md = modMan.get(module_to);
    if (md != null && md.getSystemInterface("_tenantPermissions") != null) {
      permsModule = md;
      pc.warn("Using the tenantPermissions of this module itself");
    } else {
      permsModule = tenants.findSystemInterface(id, "_tenantPermissions");
    }
    if (permsModule == null) {
      enableTenantManager(pc, td, id, module_from, module_to);
    } else {
      pc.debug("enablePermissions: Perms interface found in " + permsModule.getNameOrId());
      PermissionList pl = new PermissionList(module_to, md.getPermissionSets());
      discoveryManager.get(permsModule.getId(), gres -> {
        if (gres.failed()) {
          pc.responseError(gres.getType(), gres.cause());
        } else {
          List<DeploymentDescriptor> instances = gres.result();
          if (instances.isEmpty()) {
            pc.responseError(400,
              "No running instances for module " + permsModule.getId()
              + ". Can not invoke _tenantPermissions");
          } else { // TODO - Don't just take the first. Pick one by random.
            String baseurl = instances.get(0).getUrl();
            ModuleInterface permInt = permsModule.getSystemInterface("_tenantPermissions");
            String findPermPath = "";
            List<RoutingEntry> routingEntries = permInt.getAllRoutingEntries();
            if (!routingEntries.isEmpty()) {
              for (RoutingEntry re : routingEntries) {
                if (re.match(null, "POST")) {
                  findPermPath = re.getPath();
                  if (findPermPath == null || findPermPath.isEmpty()) {
                    findPermPath = re.getPathPattern();
                  }
                }
              }
            }
            if (findPermPath == null || findPermPath.isEmpty()) {
              pc.responseError(400,
                "Bad _tenantPermissions interface in module " + permsModule.getNameOrId()
                + ". No path to POST to");
              return;
            }
            final String permPath = findPermPath; // needs to be final
            pc.debug("enablePermissions Url: " + baseurl + " and " + permPath);
            Map<String, String> headers = reqHeaders(ctx, id);
            OkapiClient cli = new OkapiClient(baseurl, vertx, headers);
            cli.newReqId("tenantPermissions");
            cli.enableInfoLog();
            cli.request(HttpMethod.POST, permPath, Json.encodePrettily(pl), cres -> {
              if (cres.failed()) {
                pc.warn("_tenantPermissions request for "
                  + module_to + " failed with " + cres.cause().getMessage());
                pc.responseError(500, "Permissions post for " + module_to
                  + " to " + permPath
                  + " on " + permsModule.getNameOrId()
                  + " failed with " + cres.cause().getMessage());
              } else { // All well
                // Pass response headers - needed for unit test, if nothing else
                MultiMap respHeaders = cli.getRespHeaders();
                if (respHeaders != null) {
                  for (String hdr : respHeaders.names()) {
                    if (hdr.matches("^X-.*$")) {
                      ctx.response().headers().add(hdr, respHeaders.get(hdr));
                    }
                  }
                }
                pc.debug("enablePermissions: request to " + permsModule.getNameOrId()
                  + " succeeded for module " + module_to + " and tenant " + id);
                enableTenantManager(pc, td, id, module_from, module_to);
              }
            });
          }
        }
      });
    }
  }

  /**
   * Enable tenant, part 3: enable in the tenant manager. Also, disable the old
   * one in storage.
   */
  private void enableTenantManager(ProxyContext pc, TenantModuleDescriptor td,
    String id, String module_from, String module_to) {
    RoutingContext ctx = pc.getCtx();
    final long ts = getTimestamp();
    String err = tenants.updateModuleCommit(id, module_from, module_to);
    if (err.isEmpty()) {
      if (module_from != null) {
        tenantStore.disableModule(id, module_from, ts, res -> {
          enableTenantStorage(pc, td, id, module_to);
        });
      } else {
        enableTenantStorage(pc, td, id, module_to);
      }
    } else {
      pc.responseError(400, err);
    }
  }

  /**
   * Enable tenant, part 4: update storage.
   */
  private void enableTenantStorage(ProxyContext pc, TenantModuleDescriptor td,
    String id, String module_to) {
    RoutingContext ctx = pc.getCtx();
    final long ts = getTimestamp();
    tenantStore.enableModule(id, module_to, ts, res -> {
      if (res.succeeded()) {
        sendReloadSignal(id, ts);
        final String uri = ctx.request().uri() + "/" + module_to;
        pc.responseJson(201, Json.encodePrettily(td), uri);
      } else {
        pc.responseError(res.getType(), res.cause());
      }
    });
  }

  /**
   * Helper to make a DELETE request to the module's tenant interface.
   * Sets up
   * the response in ctx. NOTE - This is not used at the moment. It used to be
   * called from disableModule, but that was too drastic. We will need a way to
   * invoke this, in some future version.
   *
   * @param ctx
   * @param module
   */
  private void destroyTenant(RoutingContext ctx, String module, String id) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.destroy");
    String tenInt = tenants.getTenantInterface(module);
    if (tenInt == null || tenInt.isEmpty()) {
      pc.debug("disableModule: " + module + " has no support for tenant destroy");
      pc.responseText(204, "");
      return;
    }
    // We have a tenant interface, invoke DELETE on it
    discoveryManager.get(module, gres -> {
      if (gres.failed()) {
        pc.responseError(gres.getType(), gres.cause());
      } else {
        List<DeploymentDescriptor> instances = gres.result();
        if (instances.isEmpty()) {
          pc.responseError(400, "No running instances for module " + module                  + ". Can not invoke tenant destroy");
        } else { // TODO - Don't just take the first. Pick one by random.
          String baseurl = instances.get(0).getUrl();
          pc.debug("disableModule Url: " + baseurl + " and " + tenInt);
          Map<String, String> headers = new HashMap<>();
          for (String hdr : ctx.request().headers().names()) {
            if (hdr.matches("^X-.*$")) {
              headers.put(hdr, ctx.request().headers().get(hdr));
            }
          }
          if (!headers.containsKey(XOkapiHeaders.TENANT)) {
            headers.put(XOkapiHeaders.TENANT, id);
            pc.debug("Added " + XOkapiHeaders.TENANT + " : " + id);
          }
          headers.put("Accept", "*/*");
          //headers.put("Content-Type", "application/json; charset=UTF-8");
          String body = ""; // dummy
          OkapiClient cli = new OkapiClient(baseurl, vertx, headers);
          cli.request(HttpMethod.DELETE, tenInt, body, cres -> {
            if (cres.failed()) {
              pc.warn("Tenant destroy request for "                      + module + " failed with " + cres.cause().getMessage());
              pc.responseError(500, "DELETE to " + tenInt                      + " on " + module + " failed with "
                      + cres.cause().getMessage());
            } else { // All well, we can finally enable it
              pc.debug("disableModule: destroy request to " + module + " succeeded");
              pc.responseText(204, "");  // finally we are done
            }
          });
        }
      }
    });
  }

  public void disableModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.disable");
    try {
      final String id = ctx.request().getParam("id");
      final String module = ctx.request().getParam("mod");
      final long ts = getTimestamp();
      pc.debug("disablemodule t=" + id + " m=" + module);
      String err = tenants.disableModule(id, module);
      if (err.isEmpty()) {
        tenantStore.disableModule(id, module, ts, res -> {
          if (res.succeeded()) {
            sendReloadSignal(id, ts);
            pc.responseText(204, "");
          } else if (res.getType() == NOT_FOUND) { // Oops, things are not in sync any more!
            pc.debug("disablemodule: storage NOTFOUND: " + res.cause().getMessage());
            pc.responseError(404, res.cause());
          } else {
            pc.responseError(res.getType(), res.cause());
          }
        });
      } else if (err.contains("not found")) {
        pc.error("disableModule: " + err);
        pc.responseError(404, err);
      } else {
        pc.error("disableModule: " + err);
        pc.responseError(400, err);
      }
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void listModules(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.listmodules");
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
        pc.responseJson(200, s);
      } else {
        pc.responseError(res.getType(), res.cause());
      }
    });
  }

  public void getModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.getmodule");
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
          pc.responseJson(200, s);
        } else {
          pc.responseError(404, mod);
        }
      } else {
        pc.responseError(res.getType(), res.cause());
      }
    });
  }

  public void reloadTenant(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.reload");
    final String id = ctx.request().getParam("id");
    reloadTenant(id, res -> {
      if (res.succeeded()) {
        pc.responseText(204, "");
      } else {
        pc.responseError(500, res.cause());
      }
    });
  }

  public void reloadTenant(String id,
    Handler<ExtendedAsyncResult<Void>> fut) {
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
