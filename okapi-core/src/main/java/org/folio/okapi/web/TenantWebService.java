package org.folio.okapi.web;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
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
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.discovery.DiscoveryManager;
import org.folio.okapi.service.ModuleManager;
import org.folio.okapi.util.ProxyContext;

public class TenantWebService {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  final private Vertx vertx;
  TenantManager tenants;
  private DiscoveryManager discoveryManager;


  public TenantWebService(Vertx vertx, TenantManager tenantManager,
                          DiscoveryManager discoveryManager ) {
    this.vertx = vertx;
    this.tenants = tenantManager;
    this.discoveryManager = discoveryManager;
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
        logger.debug("XXXX Creating tenant " + id);
        Tenant t = new Tenant(td);
        tenants.insert(t, res -> {
          if (res.failed()) {
            pc.responseError(res.getType(), res.cause());
            return;
          }
          logger.debug("XXXX Created tenant " + id);
          final String uri = ctx.request().uri() + "/" + id;
          final String s = Json.encodePrettily(t.getDescriptor());
          pc.responseJson(201, s, uri);
        });
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
      tenants.updateDescriptor(td, res -> {
        if (res.succeeded()) {
          final String s = Json.encodePrettily(t.getDescriptor());
          pc.responseJson(200, s);
        } else {
          pc.responseError(404, res.cause());
        }
      });
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void list(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.list");
    tenants.list(res -> {
      if (res.succeeded()) {
        List<TenantDescriptor> tdl = res.result();
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
    tenants.get(id, res -> {
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
    tenants.delete(id, res -> {
      if (res.succeeded()) {
        pc.responseText(204, "");
      } else {
        pc.responseError(res.getType(), res.cause());
      }
    });
  }

  public void enableModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.enablemodule");
    pc.debug("enableModule XXX");
    enableTenantInt(pc, null);
  }

  public void updateModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.updatemodule");
    final String module_from = ctx.request().getParam("mod");
    pc.debug("enableModule XXX fr=" + module_from);
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
      pc.debug("enableTenantInt: id=" + id + " to=" + module_to + " fr=" + module_from);
      tenants.get(id, res -> {
        if (res.failed()) {
          pc.responseError(res.getType(), res.cause());
          return;
        }
        Tenant tenant = res.result();
        tenants.updateModuleDepCheck(tenant, module_from, module_to, tres -> {
          if (tres.failed()) {
            pc.responseError(tres.getType(), tres.cause());
            return;
          }
          logger.debug("enableTenantInt: depcheck ok");
          tenants.getTenantInterface(module_to, ires -> {
            if (ires.failed()) {
              if (ires.getType() == NOT_FOUND) {
                pc.debug("enableModule: " + module_to + " has no support for tenant init");
                enablePermissions(pc, td, id, module_from, module_to);
                return;
              } else {
                pc.responseError(ires.getType(), ires.cause());
                return;
              }
            }
            String tenInt = ires.result();
            logger.debug("enableTenantInt: tenint=" + tenInt);
            discoveryManager.get(module_to, gres -> {
              if (gres.failed()) {
                pc.responseError(gres.getType(), gres.cause());
                return;
              } else {
                List<DeploymentDescriptor> instances = gres.result();
                if (instances.isEmpty()) {
                  pc.responseError(400, "No running instances for module " + module_to
                    + ". Can not invoke tenant init");
                  return;
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

          });
        });

      });
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  /**
   * Enable tenant, part 2: Pass the module permission(set)s to perms. Part 1 of
   * the permission stuff, decide which module to invoke. Usually the one that
   * provides tenantPermissions, unless the module to be enabled itself provides
   * it, in which case we go straight to it.
   */
  private void enablePermissions(ProxyContext pc, TenantModuleDescriptor td,
    String id, String module_from, String module_to) {
    logger.debug("enablePermissions: t=" + id + " f=" + module_from + " t=" + module_to);
    RoutingContext ctx = pc.getCtx();
    ModuleManager modMan = tenants.getModuleManager();
    if (modMan == null) { // Should never happen
      pc.responseError(500, "enablePermissions: No moduleManager found. "
        + "Can not make _tenantPermissions request");
      return;
    }
    // TODO - check if we have no permissions, skip the rest
    modMan.get(module_to, mres -> {
      if (mres.failed() && mres.getType() != NOT_FOUND) { // something really wrong
        pc.responseError(mres.getType(), mres.cause());
        return;
      }
      ModuleDescriptor md = mres.result();
      if (md != null && md.getSystemInterface("_tenantPermissions") != null) {
        pc.debug("Using the tenantPermissions of this module itself");
        enablePermissionsPart2(pc, td, id, module_from, module_to, md, md, modMan);
        return;
      } else {
        tenants.findSystemInterface(id, "_tenantPermissions", res -> {
          if (res.failed()) {
            if (res.getType() == NOT_FOUND) { // no perms interface
              // just continue with the process. Should probably trigger an error
              logger.debug("enablePermissions: No tenantPermissions interface found. "
                + "Carrying on without it.");
              enableTenantManager(pc, td, id, module_from, module_to);
              return;
            }
            pc.responseError(res.getType(), res.cause());
            return;
          }
          ModuleDescriptor permsMod = res.result();
          enablePermissionsPart2(pc, td, id, module_from, module_to, md, permsMod, modMan);
          return;
        });
      }
    });
  }

  /**
   * Part 2 of enablePermissions. Needs to be a separate function, because of
   * vert.x callback hell.
   */
  private void enablePermissionsPart2(ProxyContext pc, TenantModuleDescriptor td,
    String id, String module_from, String module_to,
    ModuleDescriptor md,
    ModuleDescriptor permsModule, ModuleManager modMan) {
    RoutingContext ctx = pc.getCtx();

    pc.debug("enablePermissionsPart2: Perms interface found in " + permsModule.getNameOrId());
    PermissionList pl = new PermissionList(module_to, md.getPermissionSets());
    discoveryManager.get(permsModule.getId(), gres -> {
      if (gres.failed()) {
        pc.responseError(gres.getType(), gres.cause());
        return;
      } else {
        List<DeploymentDescriptor> instances = gres.result();
        if (instances.isEmpty()) {
          pc.responseError(400,
            "No running instances for module " + permsModule.getId()
            + ". Can not invoke _tenantPermissions");
          return;
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
          String pljson = Json.encodePrettily(pl);
          pc.debug("enablePermissions Req: " + pljson);
          Map<String, String> headers = reqHeaders(ctx, id);
          OkapiClient cli = new OkapiClient(baseurl, vertx, headers);
          cli.newReqId("tenantPermissions");
          cli.enableInfoLog();
          cli.request(HttpMethod.POST, permPath, pljson, cres -> {
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
                    pc.debug("enablePermissions: response header "
                      + hdr + " " + respHeaders.get(hdr));
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

  /**
   * Enable tenant, part 3: enable in the tenant manager.
   */
  private void enableTenantManager(ProxyContext pc, TenantModuleDescriptor td,
    String id, String module_from, String module_to) {
    RoutingContext ctx = pc.getCtx();
    logger.debug("enableTenantManager: " + module_from + " " + module_to);
    tenants.updateModuleCommit(id, module_from, module_to, ures -> {
      if (ures.failed()) {
        pc.responseError(ures.getType(), ures.cause());
        return;
      }
      final String uri = ctx.request().uri() + "/" + module_to;
      pc.responseJson(201, Json.encodePrettily(td), uri);
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
    tenants.getTenantInterface(module, ires -> {
      if (ires.failed()) {
        if (ires.getType() == NOT_FOUND) {
          pc.debug("enableModule: " + module + " has no support for tenant init");
          pc.responseText(204, "");
          return;
        } else {
          pc.responseError(ires.getType(), ires.cause());
          return;
        }
      }
      // We have a tenant interface, invoke DELETE on it
      String tenInt = ires.result();
      discoveryManager.get(module, gres -> {
        if (gres.failed()) {
          pc.responseError(gres.getType(), gres.cause());
        } else {
          List<DeploymentDescriptor> instances = gres.result();
          if (instances.isEmpty()) {
            pc.responseError(400, "No running instances for module " + module
              + ". Can not invoke tenant destroy");
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
                pc.warn("Tenant destroy request for " + module
                  + " failed with " + cres.cause().getMessage());
                pc.responseError(500, "DELETE to " + tenInt
                  + " on " + module + " failed with "
                  + cres.cause().getMessage());
              } else { // All well, we can finally enable it
                pc.debug("disableModule: destroy request to " + module + " succeeded");
                pc.responseText(204, "");  // finally we are done
              }
            }); // cli.request
          }
        } // got module
      });
    }); // tenantInterface
  } //destroyTenant

  public void disableModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.disable");
    try {
      final String id = ctx.request().getParam("id");
      final String module = ctx.request().getParam("mod");
      pc.debug("disablemodule t=" + id + " m=" + module);
      tenants.disableModule(id, module, res -> {
        if (res.failed()) {
          pc.responseError(res.getType(), res.cause());
        } else {
          pc.responseText(204, "");
        }
      });
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void listModules(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.listmodules");
    final String id = ctx.request().getParam("id");
    tenants.listModules(id, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        List<String> ml = res.result();
        Iterator<String> mli = ml.iterator();  // into a list of objects
        ArrayList<TenantModuleDescriptor> ta = new ArrayList<>();
        while (mli.hasNext()) {
          TenantModuleDescriptor tmd = new TenantModuleDescriptor();
          tmd.setId(mli.next());
          ta.add(tmd);
        }
        String s = Json.encodePrettily(ta);
        pc.responseJson(200, s);
      }
    });
  }

  public void getModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.getmodule");
    final String id = ctx.request().getParam("id");
    final String mod = ctx.request().getParam("mod");
    tenants.get(id, res -> {
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

  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.init(vertx, fut);
  }

} // class
