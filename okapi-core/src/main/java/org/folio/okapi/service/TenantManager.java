package org.folio.okapi.service;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInterface;
import org.folio.okapi.bean.PermissionList;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.util.TenantInstallOptions;
import org.folio.okapi.bean.TenantModuleDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.ModuleId;
import org.folio.okapi.util.ProxyContext;

/**
 * Manages the tenants in the shared map, and passes updates to the database.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class TenantManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private ModuleManager moduleManager = null;
  ProxyService proxyService = null;
  TenantStore tenantStore = null;
  LockedTypedMap1<Tenant> tenants = new LockedTypedMap1<>(Tenant.class);
  String mapName = "tenants";

  public TenantManager(ModuleManager moduleManager, TenantStore tenantStore) {
    this.moduleManager = moduleManager;
    this.tenantStore = tenantStore;
  }

  /**
   * Force the map to be local. Even in cluster mode, will use a local memory
   * map. This way, the node will not share tenants with the cluster, and can
   * not proxy requests for anyone but the superTenant, to the InternalModule.
   * Which is just enough to act in the deployment mode.
   */
  public void forceLocalMap() {
    mapName = null;
  }

  /**
   * Initialize the TenantManager.
   *
   * @param vertx
   * @param fut
   */
  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.init(vertx, mapName, ires -> {
      if (ires.failed()) {
        fut.handle(new Failure<>(ires.getType(), ires.cause()));
      } else {
        loadTenants(fut);
      }
    });
  }

  /**
   * Set the proxyService. So that we can use it to call the tenant interface,
   * etc.
   *
   * @param px
   */
  public void setProxyService(ProxyService px) {
    this.proxyService = px;
  }

  private void insert2(Tenant t, String id, Handler<ExtendedAsyncResult<String>> fut) {
    tenants.add(id, t, ares -> {
      if (ares.failed()) {
        fut.handle(new Failure<>(ares.getType(), ares.cause()));
      } else {
        fut.handle(new Success<>(id));
      }
    });
  }

  /**
   * Insert a tenant.
   *
   * @param t
   * @param fut
   */
  public void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getId();
    tenants.get(id, gres -> {
      if (gres.succeeded()) {
        fut.handle(new Failure<>(USER, "Duplicate tenant id " + id));
      } else if (gres.getType() == NOT_FOUND) {
        if (tenantStore == null) { // no db, just add it to shared mem
          insert2(t, id, fut);
        } else { // insert into db first
          tenantStore.insert(t, res -> {
            if (res.failed()) {
              logger.warn("TenantManager: Adding " + id + " FAILED: ", res);
              fut.handle(new Failure<>(res.getType(), res.cause()));
            } else {
              insert2(t, id, fut);
            }
          });
        }
      } else {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      }
    });
  }

  public void updateDescriptor(TenantDescriptor td,
    Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = td.getId();
    tenants.get(id, gres -> {
      if (gres.failed() && gres.getType() != NOT_FOUND) {
        logger.warn("TenantManager: UpDesc: getting " + id + " FAILED: ", gres);
        fut.handle(new Failure<>(INTERNAL, ""));
      }
      Tenant t;
      if (gres.succeeded()) {
        t = new Tenant(td, gres.result().getEnabled());
      } else {
        t = new Tenant(td);
      }
      if (tenantStore == null) {
        tenants.add(id, t, fut); // no database. handles success directly
      } else {
        tenantStore.updateDescriptor(td, upres -> {
          if (upres.failed()) {
            logger.warn("TenantManager: Updating database for " + id + " FAILED: ", upres);
            fut.handle(new Failure<>(INTERNAL, ""));
          } else {
            tenants.add(id, t, fut); // handles success
          }
        });
      }
    });
  }

  public void list(Handler<ExtendedAsyncResult<List<TenantDescriptor>>> fut) {
    tenants.getKeys(lres -> {
      if (lres.failed()) {
        logger.warn("TenantManager list: Getting keys FAILED: ", lres);
        fut.handle(new Failure<>(INTERNAL, lres.cause()));
      } else {
        List<String> ids = new ArrayList<>(lres.result());
        ids.sort(null); // to keep test resulsts consistent
        List<TenantDescriptor> tdl = new ArrayList<>();
        logger.debug("TenantManager list: " + Json.encode(ids));
        Iterator<String> it = ids.iterator();
        listR(it, tdl, fut);
      }
    });
  }

  /**
   * Recursive helper to list tenants.
   *
   * @param it iterator to recurse through
   * @param tdl list to build
   * @param fut
   */
  private void listR(Iterator<String> it, List<TenantDescriptor> tdl,
    Handler<ExtendedAsyncResult<List<TenantDescriptor>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(tdl));
    } else {
      String tid = it.next();
      tenants.get(tid, gres -> {
        if (gres.failed()) {
          logger.warn("TenantManager list: Getting " + tid + " FAILED: ", gres);
          fut.handle(new Failure<>(gres.getType(), gres.cause()));
        } else {
          Tenant t = gres.result();
          TenantDescriptor td = t.getDescriptor();
          tdl.add(td);
          logger.debug("TenantManager list: Added " + tid + ":" + Json.encode(td));
          listR(it, tdl, fut);
        }
      });
    }
  }

  /**
   * Get a tenant.
   *
   * @param id
   * @param fut
   */
  public void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut) {
    tenants.get(id, fut);
  }

  /**
   * Delete a tenant.
   *
   * @param id
   * @param fut callback with a boolean, true if actually deleted, false if not
   * there.
   */
  public void delete(String id, Handler<ExtendedAsyncResult<Boolean>> fut) {
    if (tenantStore == null) { // no db, just do it
      tenants.remove(id, fut);
    } else {
      tenantStore.delete(id, dres -> {
        if (dres.failed() && dres.getType() != NOT_FOUND) {
          logger.warn("TenantManager: Deleting " + id + " FAILED: ", dres);
          fut.handle(new Failure<>(INTERNAL, dres.cause()));
        } else {
          tenants.remove(id, fut);
        }
      });
    }
  }

  /**
   * Check module dependencies and conflicts.
   *
   * @param tenant to check for
   * @param modFrom module to be removed. Ignored in the checks
   * @param modTo module to be added
   * @param fut Callback for error messages, or a simple Success
   */
  private void checkDependencies(Tenant tenant,
    ModuleDescriptor modFrom, ModuleDescriptor modTo,
    Handler<ExtendedAsyncResult<Void>> fut) {

    moduleManager.getEnabledModules(tenant, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      List<ModuleDescriptor> modlist = gres.result();
      HashMap<String, ModuleDescriptor> mods = new HashMap<>(modlist.size());
      for (ModuleDescriptor md : modlist) {
        mods.put(md.getId(), md);
      }
      if (modFrom != null) {
        mods.remove(modFrom.getId());
      }
      if (modTo != null) {
        ModuleDescriptor already = mods.get(modTo.getId());
        if (already != null) {
          fut.handle(new Failure<>(USER,
            "Module " + modTo.getId() + " already provided"));
          return;
        }
        mods.put(modTo.getId(), modTo);
      }
      String conflicts = moduleManager.checkAllConflicts(mods);
      String deps = moduleManager.checkAllDependencies(mods);
      if (conflicts.isEmpty() && deps.isEmpty()) {
        fut.handle(new Success<>());
      } else {
        fut.handle(new Failure<>(USER, conflicts + " " + deps));
      }
    });
  }

  /**
   * Actually update the enabled modules. Assumes dependencies etc have been
   * checked.
   *
   * @param id - tenant to update for
   * @param moduleFrom - module to be disabled, may be null if none
   * @param moduleTo - module to be enabled, may be null if none
   * @param fut callback for errors.
   */
  public void updateModuleCommit(String id,
    String moduleFrom, String moduleTo,
    Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.get(id, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      updateModuleCommit(gres.result(), moduleFrom, moduleTo, fut);
    });
  }

  private void updateModuleCommit(Tenant t,
    String moduleFrom, String moduleTo,
    Handler<ExtendedAsyncResult<Void>> fut) {
    String id = t.getId();
    if (moduleFrom != null) {
      t.disableModule(moduleFrom);
    }
    if (moduleTo != null) {
      t.enableModule(moduleTo);
    }
    if (tenantStore == null) {
      updateModuleCommit2(id, t, fut);
    } else {
      tenantStore.updateModules(id, t.getEnabled(), ures -> {
        if (ures.failed()) {
          fut.handle(new Failure<>(ures.getType(), ures.cause()));
        } else {
          updateModuleCommit2(id, t, fut);
        }
      });
    }
  }

  private void updateModuleCommit2(String id, Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.put(id, t, pres -> {
      if (pres.failed()) {
        fut.handle(new Failure<>(INTERNAL, pres.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  /**
   * Enable a module for a tenant and disable another. Checks dependencies,
   * invokes the tenant interface, and the tenantPermissions interface, and
   * finally marks the modules as enabled and disabled.
   *
   * @param tenantId - id of the the tenant in question
   * @param moduleFrom id of the module to be disabled, or null
   * @param moduleTo id of the module to be enabled, or null
   * @param pc proxyContext for proper logging, etc
   * @param fut callback with success, or various errors
   *
   * To avoid too much callback hell, this has been split into several helpers.
   */
  public void enableAndDisableModule(String tenantId,
    String moduleFrom, String moduleTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {

    tenants.get(tenantId, tres -> {
      if (tres.failed()) {
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
      } else {
        Tenant tenant = tres.result();
        enableAndDisableModule(tenant, moduleFrom, moduleTo, pc, fut);
      }
    });
  }

  private void enableAndDisableModule(Tenant tenant,
    String moduleFrom, String moduleTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {

    moduleManager.getLatest(moduleTo, resTo -> {
      if (resTo.failed()) {
        fut.handle(new Failure<>(resTo.getType(), resTo.cause()));
      } else {
        ModuleDescriptor mdTo = resTo.result();
        moduleManager.get(moduleFrom, resFrom -> {
          if (resFrom.failed()) {
            fut.handle(new Failure<>(resFrom.getType(), resFrom.cause()));
          } else {
            ModuleDescriptor mdFrom = resFrom.result();
            enableAndDisableModule2(tenant, mdFrom, mdTo, pc, fut);
          }
        });
      }
    });
  }

  private void enableAndDisableModule2(Tenant tenant,
    ModuleDescriptor mdFrom, ModuleDescriptor mdTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {

    checkDependencies(tenant, mdFrom, mdTo, cres -> {
      if (cres.failed()) {
        pc.debug("enableAndDisableModule: depcheck fail: " + cres.cause().getMessage());
        fut.handle(new Failure<>(cres.getType(), cres.cause()));
      } else {
        pc.debug("enableAndDisableModule: depcheck ok");
        ead1TenantInterface(tenant, mdFrom, mdTo, pc, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            fut.handle(new Success<>(mdTo != null ? mdTo.getId() : ""));
          }
        });
      }
    });
  }

  /**
   * enableAndDisable helper 1: call the tenant interface.
   *
   * @param tenant
   * @param mdFrom
   * @param mdTo
   * @param fut
   */
  private void ead1TenantInterface(Tenant tenant,
    ModuleDescriptor mdFrom, ModuleDescriptor mdTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {

    if (mdTo == null) {
      // disable only
      ead4commit(tenant, mdFrom.getId(), null, pc, fut);
      return;
    }
    getTenantInterface(mdTo, ires -> {
      if (ires.failed()) {
        if (ires.getType() == NOT_FOUND) {
          logger.debug("eadTenantInterface: "
            + mdTo.getId() + " has no support for tenant init");
          ead2PermMod(tenant, mdFrom, mdTo, pc, fut);
        } else {
          fut.handle(new Failure<>(ires.getType(), ires.cause()));
        }
      } else {
        String tenInt = ires.result();
        logger.debug("eadTenantInterface: tenint=" + tenInt);
        JsonObject jo = new JsonObject();
        jo.put("module_to", mdTo.getId());
        if (mdFrom != null) {
          jo.put("module_from", mdFrom.getId());
        }
        String req = jo.encodePrettily();
        proxyService.callSystemInterface(tenant.getId(),
          mdTo.getId(), tenInt, req, pc, cres -> {
          if (cres.failed()) {
            fut.handle(new Failure<>(cres.getType(), cres.cause()));
          } else {
            // TODO - Copy X-headers over to ctx.resp
            ead2PermMod(tenant, mdFrom, mdTo, pc, fut);
          }
        });
      }
    });
  }

  /**
   * enableAndDisable helper 2: Choose which permission module to invoke.
   *
   * @param tenant
   * @param mdFrom
   * @param mdTo
   * @param pc
   * @param fut
   */
  private void ead2PermMod(Tenant tenant,
    ModuleDescriptor mdFrom, ModuleDescriptor mdTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {
    String moduleFrom = mdFrom != null ? mdFrom.getId() : null;
    String moduleTo = mdTo.getId();
    findSystemInterface(tenant, "_tenantPermissions", res -> {
      if (res.failed()) {
        if (res.getType() == NOT_FOUND) { // no perms interface.
          if (mdTo.getSystemInterface("_tenantPermissions") != null) {
            pc.warn("Here we should reload perms of all enabled modules XXX ");
            pc.warn("Carrying on for now XXX");
            Set<String> listModules = tenant.listModules();
            pc.warn("Got a list of already-enabled moduled: " + Json.encode(listModules) + " XXX");
            Iterator<String> modit = listModules.iterator();
            ead3RealoadPerms(tenant, modit, moduleFrom, mdTo, mdTo, pc, fut);
            return;
          }
          pc.debug("enablePermissions: No tenantPermissions interface found. "
            + "Carrying on without it.");
          ead4commit(tenant, moduleFrom, moduleTo, pc, fut);
        } else {
          pc.responseError(res.getType(), res.cause());
        }
      } else {
        ModuleDescriptor permsMod = res.result();
        if (mdTo.getSystemInterface("_tenantPermissions") != null) {
          pc.debug("Using the tenantPermissions of this module itself");
          permsMod = mdTo;
        }
        ead3Permissions(tenant, moduleFrom, mdTo, permsMod, pc, fut);
      }
    });
  }

  /**
   * enableAndDisable helper 3: Reload permissions. When we enable a module that
   * provides the tenantPermissions interface, we may have other modules already
   * enabled, who have not got their permissions pushed. Now that we have a
   * place to push those permissions to, we do it for all enabled modules.
   *
   * @param tenant
   * @param moduleFrom
   * @param mdTo
   * @param permsModule
   * @param pc
   * @param fut
   */
  private void ead3RealoadPerms(Tenant tenant, Iterator<String> modit,
    String moduleFrom, ModuleDescriptor mdTo, ModuleDescriptor permsModule,
    ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!modit.hasNext()) {
      pc.warn("No more modules to reload XXX");
      ead3Permissions(tenant, moduleFrom, mdTo, permsModule, pc, fut);
      return;
    }
    String mdid = modit.next();
    moduleManager.get(mdid, res -> {
      if (res.failed()) { // not likely to happen
        pc.responseError(res.getType(), res.cause());
        return;
      }
      ModuleDescriptor md = res.result();
      pc.warn("XXX Should reload perms for " + md.getName());
      ead3RealoadPerms(tenant, modit, moduleFrom, mdTo, permsModule, pc, fut);
    });
  }

  /**
   * enableAndDisable helper 3: Make the tenantPermissions call.
   *
   * @param tenant
   * @param moduleFrom
   * @param module_to
   * @param mdTo
   * @param permsModule
   * @param pc
   * @param fut
   */
  private void ead3Permissions(Tenant tenant, String moduleFrom,
    ModuleDescriptor mdTo, ModuleDescriptor permsModule,
    ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {

    pc.debug("ead3Permissions: Perms interface found in "
      + permsModule.getId());
    String moduleTo = mdTo.getId();
    PermissionList pl = new PermissionList(moduleTo, mdTo.getPermissionSets());
    String pljson = Json.encodePrettily(pl);
    pc.debug("ead3Permissions Req: " + pljson);

    ModuleInterface permInt = permsModule.getSystemInterface("_tenantPermissions");
    String permPath = "";
    List<RoutingEntry> routingEntries = permInt.getAllRoutingEntries();
    if (!routingEntries.isEmpty()) {
      for (RoutingEntry re : routingEntries) {
        if (re.match(null, "POST")) {
          permPath = re.getPath();
          if (permPath == null || permPath.isEmpty()) {
            permPath = re.getPathPattern();
          }
        }
      }
    }
    if (permPath == null || permPath.isEmpty()) {
      fut.handle(new Failure<>(USER,
        "Bad _tenantPermissions interface in module " + permsModule.getId()
        + ". No path to POST to"));
      return;
    }
    pc.debug("ead3Permissions: " + permsModule.getId() + " and " + permPath);
    proxyService.callSystemInterface(tenant.getId(),
      permsModule.getId(), permPath, pljson, pc, cres -> {
      if (cres.failed()) {
        fut.handle(new Failure<>(cres.getType(), cres.cause()));
      } else {
        pc.debug("enablePermissions: request to " + permsModule.getId()
          + " succeeded for module " + moduleTo + " and tenant " + tenant.getId());
        ead4commit(tenant, moduleFrom, moduleTo, pc, fut);
      }
    });
  }

  private void ead4commit(Tenant tenant,
    String moduleFrom, String moduleTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {

    pc.debug("ead4commit: " + moduleFrom + " " + moduleTo);
    updateModuleCommit(tenant, moduleFrom, moduleTo, ures -> {
      if (ures.failed()) {
        pc.responseError(ures.getType(), ures.cause());
      } else {
        pc.debug("ead4commit done");
        fut.handle(new Success<>());
      }
    });
  }

  //
  /**
   * Find the tenant API interface. Supports several deprecated versions of the
   * tenant interface: the 'tenantInterface' field in MD; if the module provides
   * a '_tenant' interface without RoutingEntries, and finally the proper way,
   * if the module provides a '_tenant' interface that is marked as a system
   * interface, and has a RoutingEntry that supports POST.
   *
   * @param module
   * @param fut callback with the path to the interface, "" if no interface, or
   * a failure
   *
   * TODO - Return a proper failure if no tenantInterface found. Small change in
   * behavior, don't do while refactoring the rest...
   */
  private void getTenantInterface(ModuleDescriptor md,
    Handler<ExtendedAsyncResult<String>> fut) {

    ModuleInterface[] prov = md.getProvidesList();
    logger.debug("findTenantInterface: prov: " + Json.encode(prov));
    for (ModuleInterface pi : prov) {
      logger.debug("findTenantInterface: Looking at " + pi.getId());
      if ("_tenant".equals(pi.getId())) {
        getTenantInterface1(pi, fut, md);
        return;
      }
    }
    fut.handle(new Failure<>(NOT_FOUND, "No _tenant interface found for "
      + md.getId()));
  }

  private void getTenantInterface1(ModuleInterface pi,
    Handler<ExtendedAsyncResult<String>> fut, ModuleDescriptor md) {

    if (!"1.0".equals(pi.getVersion())) {
      fut.handle(new Failure<>(USER, "Interface _tenant must be version 1.0 "));
      return;
    }
    if ("system".equals(pi.getInterfaceType())) {
      // looks like a new type
      List<RoutingEntry> res = pi.getAllRoutingEntries();
      if (!res.isEmpty()) {
        for (RoutingEntry re : res) {
          if (re.match(null, "POST")) {
            if (re.getPath() != null) {
              logger.debug("findTenantInterface: found path " + re.getPath());
              fut.handle(new Success<>(re.getPath()));
              return;
            }
            if (re.getPathPattern() != null) {
              logger.debug("findTenantInterface: found pattern " + re.getPathPattern());
              fut.handle(new Success<>(re.getPathPattern()));
              return;
            }
          }
        }
      }
    }
    logger.warn("Module '" + md.getId() + "' uses old-fashioned tenant "
      + "interface. Define InterfaceType=system, and add a RoutingEntry."
      + " Falling back to calling /_/tenant.");
    fut.handle(new Success<>("/_/tenant"));
  }

  /**
   * Find (the first) module that provides a given system interface. Module must
   * be enabled for the tenant.
   *
   * @param tenant tenant to check for
   * @param interfaceName interface name to look for
   * @param fut callback with a @return ModuleDescriptor for the module
   *
   * TODO - Take a version too, pass it to getSystemInterface, check there
   */
  private void findSystemInterface(Tenant tenant, String interfaceName,
    Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {

    Iterator<String> it = tenant.getEnabled().keySet().iterator();
    findSystemInterfaceR(tenant, interfaceName, it, fut);
  }

  private void findSystemInterfaceR(Tenant tenant, String interfaceName,
    Iterator<String> it,
    Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Failure<>(NOT_FOUND, "No module provides " + interfaceName));
      return;
    }
    String mid = it.next();
    moduleManager.get(mid, gres -> {
      if (gres.failed()) { // should not happen
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      ModuleDescriptor md = gres.result();
      logger.debug("findSystemInterface: looking at " + mid + ": "
        + "en: " + tenant.isEnabled(mid) + " si: " + md.getSystemInterface(interfaceName));
      if (md.getSystemInterface(interfaceName) != null
        && tenant.isEnabled(mid)) {
        logger.debug("findSystemInterface: found " + mid);
        fut.handle(new Success<>(md));
        return;
      }
      findSystemInterfaceR(tenant, interfaceName, it, fut);
    });
  }

  public void listModulesFromInterface(String tenantId,
    String interfaceName, Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    tenants.get(tenantId, tres -> {
      if (tres.failed()) {
        logger.debug("listModulesFromInterface: tenant " + tenantId + " not found");
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
        return;
      }
      Tenant tenant = tres.result();
      ArrayList<ModuleDescriptor> mdList = new ArrayList<>();
      moduleManager.getEnabledModules(tenant, mres -> {
        if (mres.failed()) {
          logger.debug("listModulesFromInterface: enabledModules failed for " + tenantId);
          fut.handle(new Failure<>(mres.getType(), mres.cause()));
          return;
        }
        List<ModuleDescriptor> modlist = mres.result();
        for (ModuleDescriptor md : modlist) {
          for (ModuleInterface provide : md.getProvidesList()) {
            if (interfaceName.equals(provide.getId())) {
              mdList.add(md);
              break;
            }
          }
        }
        fut.handle(new Success<>(mdList));
      }); // modlist
    }); // tenant
  }

  private void installModules(Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    List<TenantModuleDescriptor> tml2 = new LinkedList<>();

    for (TenantModuleDescriptor tm : tml) {
      if (tmAction(tm, modsAvailable, modsEnabled, tml2, fut)) {
        return;
      }
    }
    String s = moduleManager.checkAllDependencies(modsEnabled);
    if (!s.isEmpty()) {
      logger.warn("installModules.checkAllDependencies: " + s);
      fut.handle(new Failure<>(USER, s));
      return;
    }

    tml.clear();
    for (TenantModuleDescriptor tm : tml2) {
      tml.add(tm);
    }
    logger.info("installModules.returning OK");
    fut.handle(new Success<>(Boolean.TRUE));
  }

  private boolean tmAction(TenantModuleDescriptor tm,
    Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    String id = tm.getId();
    String action = tm.getAction();
    if ("enable".equals(action)) {
      return tmEnable(id, modsAvailable, modsEnabled, tml, fut);
    } else if ("uptodate".equals(action)) {
      return tmUpToDate(modsEnabled, id, fut);
    } else if ("disable".equals(action)) {
      return tmDisable(id, modsAvailable, modsEnabled, tml, fut);
    } else {
      fut.handle(new Failure<>(INTERNAL, "Not implemented: action = " + action));
      return true;
    }
  }

  private boolean tmEnable(String id, Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    ModuleId moduleId = new ModuleId(id);
    if (!moduleId.hasSemVer()) {
      id = moduleId.getLatest(modsAvailable.keySet());
    }
    if (!modsAvailable.containsKey(id)) {
      fut.handle(new Failure<>(NOT_FOUND, id));
      return true;
    }
    if (modsEnabled.containsKey(id)) {
      boolean alreadyEnabled = false;
      for (TenantModuleDescriptor tm : tml) {
        if (tm.getId().equals(id)) {
          alreadyEnabled = true;
        }
      }
      if (!alreadyEnabled) {
        TenantModuleDescriptor tmu = new TenantModuleDescriptor();
        tmu.setAction("uptodate");
        tmu.setId(id);
        tml.add(tmu);
      }
    } else {
      moduleManager.addModuleDependencies(modsAvailable.get(id),
        modsAvailable, modsEnabled, tml);
    }
    return false;
  }

  private boolean tmUpToDate(Map<String, ModuleDescriptor> modsEnabled,
    String id, Handler<ExtendedAsyncResult<Boolean>> fut) {

    if (!modsEnabled.containsKey(id)) {
      fut.handle(new Failure<>(NOT_FOUND, id));
      return true;
    }
    return false;
  }

  private boolean tmDisable(String id, Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml2,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    ModuleId moduleId = new ModuleId(id);
    if (!moduleId.hasSemVer()) {
      id = moduleId.getLatest(modsEnabled.keySet());
    }
    if (tmUpToDate(modsEnabled, id, fut)) {
      return true;
    }
    moduleManager.removeModuleDependencies(modsAvailable.get(id),
      modsEnabled, tml2);
    return false;
  }

  private void installCommit(Tenant tenant, ProxyContext pc,
    Map<String, ModuleDescriptor> modsAvailable,
    Iterator<TenantModuleDescriptor> it,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (it.hasNext()) {
      TenantModuleDescriptor tm = it.next();
      ModuleDescriptor mdFrom = null;
      ModuleDescriptor mdTo = null;
      if ("enable".equals(tm.getAction())) {
        if (tm.getFrom() != null) {
          mdFrom = modsAvailable.get(tm.getFrom());
        }
        mdTo = modsAvailable.get(tm.getId());
      } else if ("disable".equals(tm.getAction())) {
        mdFrom = modsAvailable.get(tm.getId());
      }
      if (mdFrom == null && mdTo == null) {
        installCommit(tenant, pc, modsAvailable, it, fut);
      } else {
        ead1TenantInterface(tenant, mdFrom, mdTo, pc, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            installCommit(tenant, pc, modsAvailable, it, fut);
          }
        });
      }
    } else {
      fut.handle(new Success<>());
    }
  }

  public void installUpgradeModules(String tenantId, ProxyContext pc,
    TenantInstallOptions options, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<List<TenantModuleDescriptor>>> fut) {
    tenants.get(tenantId, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      Tenant t = gres.result();
      moduleManager.getModulesWithFilter(null, options.getPreRelease(), mres -> {
        if (mres.failed()) {
          fut.handle(new Failure<>(mres.getType(), mres.cause()));
          return;
        }
        List<ModuleDescriptor> modResult = mres.result();
        HashMap<String, ModuleDescriptor> modsAvailable = new HashMap<>(modResult.size());
        HashMap<String, ModuleDescriptor> modsEnabled = new HashMap<>();
        for (ModuleDescriptor md : modResult) {
          modsAvailable.put(md.getId(), md);
          logger.info("mod available: " + md.getId());
          if (t.isEnabled(md.getId())) {
            logger.info("mod enabled: " + md.getId());
            modsEnabled.put(md.getId(), md);
          }
        }
        List<TenantModuleDescriptor> tml2
          = prepareTenantModuleList(modsAvailable, modsEnabled, tml);
        installAndCommit(t, pc, options, modsAvailable, modsEnabled, tml2, fut);
      });
    });
  }

  List<TenantModuleDescriptor> prepareTenantModuleList(
    Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml) {

    if (tml == null) { // upgrade case . Mark all newer modules for install
      List<TenantModuleDescriptor> tml2 = new LinkedList<>();
      for (String fId : modsEnabled.keySet()) {
        ModuleId moduleId = new ModuleId(fId);
        String uId = moduleId.getLatest(modsAvailable.keySet());
        if (!uId.equals(fId)) {
          TenantModuleDescriptor tmd = new TenantModuleDescriptor();
          tmd.setAction("enable");
          tmd.setId(uId);
          logger.info("upgrade.. enable " + uId);
          tmd.setFrom(fId);
          tml2.add(tmd);
        }
      }
      return tml2;
    } else {
      return tml;
    }
  }

  private void checkAutoDeploy(Tenant t, ProxyContext pc,
    TenantInstallOptions options,
    Map<String, ModuleDescriptor> modsAvailable, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (options.getAutoDeploy()) {
      fut.handle(new Failure<>(INTERNAL, "autoDeploy unsupported"));
    } else {
      installCommit(t, pc, modsAvailable, tml.iterator(), fut);
    }
  }

  private void installAndCommit(Tenant t, ProxyContext pc,
    TenantInstallOptions options,
    Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<List<TenantModuleDescriptor>>> fut) {

    installModules(modsAvailable, modsEnabled, tml, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      if (options.getSimulate()) {
        fut.handle(new Success<>(tml));
      } else {
        checkAutoDeploy(t, pc, options, modsAvailable, tml, res1 -> {
          if (res1.failed()) {
            fut.handle(new Failure<>(res1.getType(), res1.cause()));
          } else {
            fut.handle(new Success<>(tml));
          }
        });
      }
    });
  }

  /**
   * List modules for a given tenant.
   *
   * @param id Tenant ID
   * @param fut callback with a list of moduleIds
   */
  public void listModules(String id, Handler<ExtendedAsyncResult<List<String>>> fut) {
    tenants.get(id, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        Tenant t = gres.result();
        List<String> tl = new ArrayList<>(t.listModules());
        tl.sort(null);
        fut.handle(new Success<>(tl));
      }
    });
  }

  /**
   * Get the (first) tenant that uses the given module. Used to check if a
   * module may be deleted.
   *
   * @param mod id of the module in question.
   * @param fut - Succeeds if not in use. Fails with ANY and the module name
   */
  public void getModuleUser(String mod, Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.getKeys(kres -> {
      if (kres.failed()) {
        fut.handle(new Failure<>(kres.getType(), kres.cause()));
      } else {
        Collection<String> tkeys = kres.result();
        Iterator<String> it = tkeys.iterator();
        getModuleUserR(mod, it, fut);
      }
    });
  }

  private void getModuleUserR(String mod, Iterator<String> it, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) { // no problems found
      fut.handle(new Success<>());
    } else {
      String tid = it.next();
      tenants.get(tid, gres -> {
        if (gres.failed()) {
          fut.handle(new Failure<>(gres.getType(), gres.cause()));
        } else {
          Tenant t = gres.result();
          if (t.isEnabled(mod)) {
            fut.handle(new Failure<>(ANY, tid));
          } else {
            getModuleUserR(mod, it, fut);
          }
        }
      });
    }
  }

  /**
   * Load tenants from the store into the shared memory map
   *
   * @param fut
   */
  public void loadTenants(Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.getKeys(gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        Collection<String> keys = gres.result();
        if (!keys.isEmpty()) {
          logger.info("Not loading tenants, looks like someone already did");
          fut.handle(new Success<>());
        } else if (tenantStore == null) {
          logger.info("No storage to load tenants from, so starting with empty");
          fut.handle(new Success<>());
        } else {
          tenantStore.listTenants(lres -> {
            if (lres.failed()) {
              fut.handle(new Failure<>(lres.getType(), lres.cause()));
            } else {
              Iterator<Tenant> it = lres.result().iterator();
              loadR(it, fut);
            }
          });
        }
      }
    });
  }

  private void loadR(Iterator<Tenant> it,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) {
      logger.info("All tenants loaded");
      fut.handle(new Success<>());
      return;
    }
    Tenant t = it.next();
    String id = t.getId();
    tenants.add(id, t, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        loadR(it, fut);
      }
    });
  }


} // class
