package org.folio.okapi.service;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.PermissionList;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.util.TenantInstallOptions;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor.Action;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.util.CompList;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.util.ProxyContext;

/**
 * Manages the tenants in the shared map, and passes updates to the database.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class TenantManager {

  private final Logger logger = OkapiLogger.get();
  private ModuleManager moduleManager = null;
  private ProxyService proxyService = null;
  private TenantStore tenantStore = null;
  private LockedTypedMap1<Tenant> tenants = new LockedTypedMap1<>(Tenant.class);
  private String mapName = "tenants";
  private Messages messages = Messages.getInstance();

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
        fut.handle(new Failure<>(USER, messages.getMessage("10400", id)));
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
        CompList<List<TenantDescriptor>> futures = new CompList<>(INTERNAL);
        List<TenantDescriptor> tdl = new LinkedList<>();
        for (String s : lres.result()) {
          Future<Tenant> future = Future.future();
          tenants.get(s, res -> {
            if (res.succeeded()) {
              tdl.add(res.result().getDescriptor());
            }
            future.handle(res);
          });
          futures.add(future);
        }
        futures.all(tdl, fut);
      }
    });
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
        ead1TenantInterface(tenant, mdFrom, mdTo, false, pc, res -> {
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
    ModuleDescriptor mdFrom, ModuleDescriptor mdTo, boolean purge,
    ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {

    getTenantInterface(mdFrom, mdTo, purge, ires -> {
      if (ires.failed()) {
        if (ires.getType() == NOT_FOUND) {
          logger.debug("eadTenantInterface: "
            + (mdTo != null ? mdTo.getId() : mdFrom.getId())
            + " has no support for tenant init");
          ead2TenantInterface(tenant, mdFrom, mdTo, pc, fut);
        } else {
          fut.handle(new Failure<>(ires.getType(), ires.cause()));
        }
      } else {
        ModuleInstance tenInst = ires.result();
        logger.debug("eadTenantInterface: tenint=" + tenInst.getPath());
        JsonObject jo = new JsonObject();
        if (mdTo != null) {
          jo.put("module_to", mdTo.getId());
        }
        if (mdFrom != null) {
          jo.put("module_from", mdFrom.getId());
        }
        final String req = purge ? "" : jo.encodePrettily();
        proxyService.callSystemInterface(tenant, tenInst, req, pc, cres -> {
          if (cres.failed()) {
            fut.handle(new Failure<>(cres.getType(), cres.cause()));
          } else {
            // We can ignore the result, the call went well.
            ead2TenantInterface(tenant, mdFrom, mdTo, pc, fut);
          }
        });
      }
    });
  }

  private void ead2TenantInterface(Tenant tenant,
    ModuleDescriptor mdFrom, ModuleDescriptor mdTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (mdTo == null) {
      ead5commit(tenant, mdFrom.getId(), null, pc, fut);
    } else {
      ead2PermMod(tenant, mdFrom, mdTo, pc, fut);
    }
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
    findSystemInterface(tenant, res -> {
      if (res.failed()) {
        if (res.getType() == NOT_FOUND) { // no perms interface.
          if (mdTo.getSystemInterface("_tenantPermissions") != null) {
            pc.debug("ead2PermMod: Here we reload perms of all enabled modules");
            Set<String> listModules = tenant.listModules();
            pc.debug("Got a list of already-enabled moduled: " + Json.encode(listModules));
            Iterator<String> modit = listModules.iterator();
            ead3RealoadPerms(tenant, modit, moduleFrom, mdTo, mdTo, pc, fut);
            return;
          }
          pc.debug("enablePermissions: No tenantPermissions interface found. "
            + "Carrying on without it.");
          ead5commit(tenant, moduleFrom, moduleTo, pc, fut);
        } else {
          pc.responseError(res.getType(), res.cause());
        }
      } else {
        ModuleDescriptor permsMod = res.result();
        if (mdTo.getSystemInterface("_tenantPermissions") != null) {
          pc.debug("Using the tenantPermissions of this module itself");
          permsMod = mdTo;
        }
        ead4Permissions(tenant, moduleFrom, mdTo, permsMod, pc, fut);
      }
    });
  }

  /**
   * enableAndDisable helper 3: Reload permissions. When we enable a module that
   * provides the tenantPermissions interface, we may have other modules already
   * enabled, who have not got their permissions pushed. Now that we have a
   * place to push those permissions to, we do it recursively for all enabled
   * modules.
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
      pc.debug("ead3RealoadPerms: No more modules to reload");
      ead4Permissions(tenant, moduleFrom, mdTo, permsModule, pc, fut);
      return;
    }
    String mdid = modit.next();
    moduleManager.get(mdid, res -> {
      if (res.failed()) { // not likely to happen
        pc.responseError(res.getType(), res.cause());
        return;
      }
      ModuleDescriptor md = res.result();
      pc.debug("ead3RealoadPerms: Should reload perms for " + md.getName());
      tenantPerms(tenant, md, permsModule, pc, pres -> {
        if (pres.failed()) { // not likely to happen
          pc.responseError(res.getType(), res.cause());
          return;
        }
        ead3RealoadPerms(tenant, modit, moduleFrom, mdTo, permsModule, pc, fut);
      });
    });
  }

  /**
   * enableAndDisable helper 4: Make the tenantPermissions call. For the module
   * itself.
   *
   * @param tenant
   * @param moduleFrom
   * @param module_to
   * @param mdTo
   * @param permsModule
   * @param pc
   * @param fut
   */
  private void ead4Permissions(Tenant tenant, String moduleFrom,
    ModuleDescriptor mdTo, ModuleDescriptor permsModule,
    ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {

    pc.debug("ead4Permissions: Perms interface found in "
      + permsModule.getName());

    tenantPerms(tenant, mdTo, permsModule, pc, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      String moduleTo = mdTo.getId();
      ead5commit(tenant, moduleFrom, moduleTo, pc, fut);
    });
  }

  /**
   * enableAndDisable helper 5: Commit the change in modules.
   *
   * @param tenant
   * @param moduleFrom
   * @param moduleTo
   * @param pc
   * @param fut
   */
  private void ead5commit(Tenant tenant,
    String moduleFrom, String moduleTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {

    pc.debug("ead5commit: " + moduleFrom + " " + moduleTo);
    updateModuleCommit(tenant, moduleFrom, moduleTo, ures -> {
      if (ures.failed()) {
        pc.responseError(ures.getType(), ures.cause());
      } else {
        pc.debug("ead5commit done");
        fut.handle(new Success<>());
      }
    });
  }

  /**
   * Helper to make the tenantPermissions call for one module. Used from
   * ead3RealoadPerms and ead4Permissions.
   */
  private void tenantPerms(Tenant tenant, ModuleDescriptor mdTo,
    ModuleDescriptor permsModule, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {

    pc.debug("Loading permissions for " + mdTo.getName()
      + " (using " + permsModule.getName() + ")");
    String moduleTo = mdTo.getId();
    PermissionList pl = new PermissionList(moduleTo, mdTo.getPermissionSets());
    String pljson = Json.encodePrettily(pl);
    pc.debug("tenantPerms Req: " + pljson);
    InterfaceDescriptor permInt = permsModule.getSystemInterface("_tenantPermissions");
    String permPath = "";
    List<RoutingEntry> routingEntries = permInt.getAllRoutingEntries();
    ModuleInstance permInst = null;
    if (!routingEntries.isEmpty()) {
      for (RoutingEntry re : routingEntries) {
        if (re.match(null, "POST")) {
          permPath = re.getPath();
          if (permPath == null || permPath.isEmpty()) {
            permPath = re.getPathPattern();
          }
          permInst = new ModuleInstance(permsModule, re, permPath, HttpMethod.POST);
        }
      }
    }
    if (permInst == null) {
      fut.handle(new Failure<>(USER,
        "Bad _tenantPermissions interface in module " + permsModule.getId()
        + ". No path to POST to"));
      return;
    }
    pc.debug("tenantPerms: " + permsModule.getId() + " and " + permPath);
    proxyService.callSystemInterface(tenant, permInst,
      pljson, pc, cres -> {
        if (cres.failed()) {
          fut.handle(new Failure<>(cres.getType(), cres.cause()));
        } else {
          pc.debug("tenantPerms request to " + permsModule.getName()
            + " succeeded for module " + moduleTo + " and tenant " + tenant.getId());
          fut.handle(new Success<>());
        }
      });
  }

  /**
   * Find the tenant API interface. Supports several deprecated versions of the
   * tenant interface: the 'tenantInterface' field in MD; if the module provides
   * a '_tenant' interface without RoutingEntries, and finally the proper way,
   * if the module provides a '_tenant' interface that is marked as a system
   * interface, and has a RoutingEntry that supports POST.
   *
   * @param module
   * @param fut callback with the getPath to the interface, "" if no interface,
   * or a failure
   *
   */
  private void getTenantInterface(ModuleDescriptor mdFrom,
    ModuleDescriptor mdTo, boolean purge,
    Handler<ExtendedAsyncResult<ModuleInstance>> fut) {

    ModuleDescriptor md = mdTo != null ? mdTo : mdFrom;
    InterfaceDescriptor[] prov = md.getProvidesList();
    logger.debug("findTenantInterface: prov: " + Json.encode(prov));
    for (InterfaceDescriptor pi : prov) {
      logger.debug("findTenantInterface: Looking at " + pi.getId());
      if ("_tenant".equals(pi.getId())) {
        final String v = pi.getVersion();
        final String method = purge ? "DELETE" : "POST";
        switch (v) {
          case "1.0":
            if (mdTo == null && !purge) {
              break;
            } else if (getTenantInterface1(pi, mdFrom, mdTo, method, fut)) {
              return;
            } else if (!purge) {
              logger.warn("Module '" + md.getId() + "' uses old-fashioned tenant "
                + "interface. Define InterfaceType=system, and add a RoutingEntry."
                + " Falling back to calling /_/tenant.");
              fut.handle(new Success<>(new ModuleInstance(md, null, "/_/tenant", HttpMethod.POST)));
              return;
            }
            break;
          case "1.1":
            if (getTenantInterface1(pi, mdFrom, mdTo, method, fut)) {
              return;
            }
            break;
          default:
            fut.handle(new Failure<>(USER, messages.getMessage("10401", v)));
            return;
        }
      }
    }
    fut.handle(new Failure<>(NOT_FOUND, messages.getMessage("10402", md.getId())));
  }

  private boolean getTenantInterface1(InterfaceDescriptor pi,
    ModuleDescriptor mdFrom, ModuleDescriptor mdTo, String method,
    Handler<ExtendedAsyncResult<ModuleInstance>> fut) {

    ModuleDescriptor md = mdTo != null ? mdTo : mdFrom;
    if ("system".equals(pi.getInterfaceType())) {
      List<RoutingEntry> res = pi.getAllRoutingEntries();
      for (RoutingEntry re : res) {
        if (re.match(null, method)) {
          String pattern = re.getPathPattern();
          if (pattern == null) {
            pattern = re.getPath();
          }
          if (method.equals("DELETE")) {
            fut.handle(new Success<>(new ModuleInstance(md, re, pattern, HttpMethod.DELETE)));
            return true;
          } else if ("/_/tenant/disable".equals(pattern)) {
            if (mdTo == null) {
              fut.handle(new Success<>(new ModuleInstance(md, re, pattern, HttpMethod.POST)));
              return true;
            }
          } else if (mdTo != null) {
            fut.handle(new Success<>(new ModuleInstance(md, re, pattern, HttpMethod.POST)));
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Find (the first) module that provides a given system interface. Module must
   * be enabled for the tenant.
   *  @param tenant tenant to check for
   * @param fut callback with a @return ModuleDescriptor for the module
   *
   */
  private void findSystemInterface(Tenant tenant,
                                   Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {

    Iterator<String> it = tenant.getEnabled().keySet().iterator();
    findSystemInterfaceR(tenant, "_tenantPermissions", it, fut);
  }

  private void findSystemInterfaceR(Tenant tenant, String interfaceName,
    Iterator<String> it,
    Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Failure<>(NOT_FOUND, messages.getMessage("10403", interfaceName)));
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
        + " si: " + md.getSystemInterface(interfaceName));
      if (md.getSystemInterface(interfaceName) != null ) {
        logger.debug("findSystemInterface: found " + mid);
        fut.handle(new Success<>(md));
        return;
      }
      findSystemInterfaceR(tenant, interfaceName, it, fut);
    });
  }

  public void listInterfaces(String tenantId, boolean full, String interfaceType,
          Handler<ExtendedAsyncResult<List<InterfaceDescriptor>>> fut) {

    tenants.get(tenantId, tres -> {
      if (tres.failed()) {
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
      } else {
        listInterfaces(tres.result(), full, interfaceType, fut);
      }
    });
  }

  private void listInterfaces(Tenant tenant, boolean full, String interfaceType,
    Handler<ExtendedAsyncResult<List<InterfaceDescriptor>>> fut) {

    List<InterfaceDescriptor> intList = new LinkedList<>();
    moduleManager.getEnabledModules(tenant, mres -> {
      if (mres.failed()) {
        fut.handle(new Failure<>(mres.getType(), mres.cause()));
        return;
      }
      List<ModuleDescriptor> modlist = mres.result();
      Set<String> ids = new HashSet<>();
      for (ModuleDescriptor md : modlist) {
        for (InterfaceDescriptor provide : md.getProvidesList()) {
          if (interfaceType == null || provide.isType(interfaceType)) {
            if (full) {
              intList.add(provide);
            } else {
              if (ids.add(provide.getId())) {
                InterfaceDescriptor tmp = new InterfaceDescriptor();
                tmp.setId(provide.getId());
                tmp.setVersion(provide.getVersion());
                intList.add(tmp);
              }
            }
          }
        }
      }
      fut.handle(new Success<>(intList));
    });
  }

  public void listModulesFromInterface(String tenantId,
      String interfaceName, String interfaceType,
      Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    tenants.get(tenantId, tres -> {
      if (tres.failed()) {
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
        return;
      }
      Tenant tenant = tres.result();
      List<ModuleDescriptor> mdList = new LinkedList<>();
      moduleManager.getEnabledModules(tenant, mres -> {
        if (mres.failed()) {
          fut.handle(new Failure<>(mres.getType(), mres.cause()));
          return;
        }
        List<ModuleDescriptor> modlist = mres.result();
        for (ModuleDescriptor md : modlist) {
          for (InterfaceDescriptor provide : md.getProvidesList()) {
            if (interfaceName.equals(provide.getId())
                    && (interfaceType == null || provide.isType(interfaceType))) {
              mdList.add(md);
              break;
            }
          }
        }
        fut.handle(new Success<>(mdList));
      }); // modlist
    }); // tenant
  }

  private void installCheckDependencies(Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    List<TenantModuleDescriptor> tml2 = new LinkedList<>();
    for (TenantModuleDescriptor tm : tml) {
      tml2.add(tm);
    }

    for (TenantModuleDescriptor tm : tml2) {
      if (tmAction(tm, modsAvailable, modsEnabled, tml, fut)) {
        return;
      }
    }
    String s = moduleManager.checkAllDependencies(modsEnabled);
    if (!s.isEmpty()) {
      logger.warn("installModules.checkAllDependencies: " + s);
      fut.handle(new Failure<>(USER, s));
      return;
    }

    logger.info("installModules.returning OK");
    fut.handle(new Success<>(Boolean.TRUE));
  }

  private boolean tmAction(TenantModuleDescriptor tm,
    Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    String id = tm.getId();
    ModuleId moduleId = new ModuleId(id);
    if (!moduleId.hasSemVer()) {
      id = moduleId.getLatest(modsAvailable.keySet());
      tml.remove(tm);
    }
    Action action = tm.getAction();
    if (null == action) {
      fut.handle(new Failure<>(INTERNAL, messages.getMessage("10404", "null")));
      return true;
    } else switch (action) {
      case enable:
        return tmEnable(id, modsAvailable, modsEnabled, tml, fut);
      case uptodate:
        return tmUpToDate(modsEnabled, id, fut);
      case disable:
        return tmDisable(id, modsAvailable, modsEnabled, tml, fut);
      default:
        fut.handle(new Failure<>(INTERNAL, messages.getMessage("10404", action.name())));
        return true;
    }
  }

  private boolean tmEnable(String id, Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    if (!modsAvailable.containsKey(id)) {
      fut.handle(new Failure<>(NOT_FOUND, id));
      return true;
    }
    if (modsEnabled.containsKey(id)) {
      for (TenantModuleDescriptor tm : tml) {
        if (tm.getId().equals(id) && tm.getFrom() == null) {
          tm.setAction(Action.uptodate);
        }
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
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    if (tmUpToDate(modsEnabled, id, fut)) {
      return true;
    }
    moduleManager.removeModuleDependencies(modsAvailable.get(id),
      modsEnabled, tml);
    return false;
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
        installUpgradeModules2(t, pc, options, modsAvailable, modsEnabled, tml2, fut);
      });
    });
  }

  private List<TenantModuleDescriptor> prepareTenantModuleList(
    Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml) {

    if (tml == null) { // upgrade case . Mark all newer modules for install
      List<TenantModuleDescriptor> tml2 = new LinkedList<>();
      for (String fId : modsEnabled.keySet()) {
        ModuleId moduleId = new ModuleId(fId);
        String uId = moduleId.getLatest(modsAvailable.keySet());
        if (!uId.equals(fId)) {
          TenantModuleDescriptor tmd = new TenantModuleDescriptor();
          tmd.setAction(Action.enable);
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

  /* phase 1 deploy modules if necessary */
  private void installCommit1(Tenant t, ProxyContext pc,
    TenantInstallOptions options,
    Map<String, ModuleDescriptor> modsAvailable,
    List<TenantModuleDescriptor> tml,
    Iterator<TenantModuleDescriptor> it,
    Handler<ExtendedAsyncResult<Void>> fut) {

    if (it.hasNext() && options.getDeploy()) {
      TenantModuleDescriptor tm = it.next();
      if (tm.getAction() == Action.enable || tm.getAction() == Action.uptodate) {
        ModuleDescriptor md = modsAvailable.get(tm.getId());
        proxyService.autoDeploy(md, pc, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            installCommit1(t, pc, options, modsAvailable, tml, it, fut);
          }
        });
      } else {
        installCommit1(t, pc, options, modsAvailable, tml, it, fut);
      }
    } else {
      installCommit2(t, pc, options, modsAvailable, tml, tml.iterator(), fut);
    }
  }

  /* phase 2 enable modules for tenant */
  private void installCommit2(Tenant tenant, ProxyContext pc,
    TenantInstallOptions options,
    Map<String, ModuleDescriptor> modsAvailable,
    List<TenantModuleDescriptor> tml,
    Iterator<TenantModuleDescriptor> it,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (it.hasNext()) {
      TenantModuleDescriptor tm = it.next();
      ModuleDescriptor mdFrom = null;
      ModuleDescriptor mdTo = null;
      boolean purge = false;
      if (tm.getAction() == Action.enable) {
        if (tm.getFrom() != null) {
          mdFrom = modsAvailable.get(tm.getFrom());
        }
        mdTo = modsAvailable.get(tm.getId());
      } else if (tm.getAction() == Action.disable) {
        mdFrom = modsAvailable.get(tm.getId());
        if (options.getPurge()) {
          purge = true;
        }
      }
      if (mdFrom == null && mdTo == null) {
        installCommit2(tenant, pc, options, modsAvailable, tml, it, fut);
      } else {
        ead1TenantInterface(tenant, mdFrom, mdTo, purge, pc, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            installCommit2(tenant, pc, options, modsAvailable, tml, it, fut);
          }
        });
      }
    } else {
      installCommit3(tenant, pc, options, modsAvailable, tml, tml.iterator(), fut);
    }
  }

  /* phase 3 undeploy if no longer needed */
  private void installCommit3(Tenant tenant, ProxyContext pc,
    TenantInstallOptions options,
    Map<String, ModuleDescriptor> modsAvailable,
    List<TenantModuleDescriptor> tml,
    Iterator<TenantModuleDescriptor> it,
    Handler<ExtendedAsyncResult<Void>> fut) {

    if (it.hasNext() && options.getDeploy()) {
      TenantModuleDescriptor tm = it.next();
      ModuleDescriptor md = null;
      if (tm.getAction() == Action.enable) {
        md = modsAvailable.get(tm.getFrom());
      }
      if (tm.getAction() == Action.disable) {
        md = modsAvailable.get(tm.getId());
      }
      if (md != null) {
        final ModuleDescriptor mdF = md;
        getModuleUser(md.getId(), ures -> {
          if (ures.failed()) {
            // in use or other error, so skip
            installCommit3(tenant, pc, options, modsAvailable, tml, it, fut);
          } else {
            // success means : not in use, so we can undeploy it
            logger.info("autoUndeploy mdF=" + mdF.getId());
            proxyService.autoUndeploy(mdF, pc, res -> {
              if (res.failed()) {
                fut.handle(new Failure<>(res.getType(), res.cause()));
              } else {
                installCommit3(tenant, pc, options, modsAvailable, tml, it, fut);
              }
            });
          }
        });
      } else {
        installCommit3(tenant, pc, options, modsAvailable, tml, it, fut);
      }
    } else {
      fut.handle(new Success<>());
    }
  }

  private void installUpgradeModules2(Tenant t, ProxyContext pc,
    TenantInstallOptions options,
    Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<List<TenantModuleDescriptor>>> fut) {

    installCheckDependencies(modsAvailable, modsEnabled, tml, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      if (options.getSimulate()) {
        fut.handle(new Success<>(tml));
      } else {
        installCommit1(t, pc, options, modsAvailable, tml, tml.iterator(),
          res1 -> {
            if (res1.failed()) {
              fut.handle(new Failure<>(res1.getType(), res1.cause()));
            } else {
              fut.handle(new Success<>(tml));
            }
          });
      }
    });
  }

  public void listModules(String id, boolean full,
    Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    tenants.get(id, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        Tenant t = gres.result();
        List<ModuleDescriptor> tl = new LinkedList<>();
        CompList<List<ModuleDescriptor>> futures = new CompList<>(INTERNAL);
        for (String mId : t.listModules()) {
          Future<ModuleDescriptor> f = Future.future();
          moduleManager.get(mId, res -> {
            if (res.succeeded()) {
              if (full) {
                tl.add(res.result());
              } else {
                ModuleDescriptor md = new ModuleDescriptor(res.result(), false);
                md.setName(null); // name not part of tenantModuleDescriptor
                tl.add(md);
              }
            }
            f.handle(res);
          });
          futures.add(f);
        }
        futures.all(tl, fut);
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
  private void loadTenants(Handler<ExtendedAsyncResult<Void>> fut) {
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
          loadTenants2(fut);
        }
      }
    });
  }

  private void loadTenants2(Handler<ExtendedAsyncResult<Void>> fut) {
    tenantStore.listTenants(lres -> {
      if (lres.failed()) {
        fut.handle(new Failure<>(lres.getType(), lres.cause()));
      } else {
        CompList<List<Void>> futures = new CompList<>(INTERNAL);
        for (Tenant t : lres.result()) {
          Future<Void> f = Future.future();
          tenants.add(t.getId(), t, f::handle);
          futures.add(f);
        }
        futures.all(fut);
      }
    });
  }

} // class
