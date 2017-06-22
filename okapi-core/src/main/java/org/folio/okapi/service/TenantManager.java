package org.folio.okapi.service;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInterface;
import org.folio.okapi.bean.PermissionList;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.Success;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.ProxyContext;

/**
 * Manages the tenants in the shared map, and passes updates to the database.
 */
public class TenantManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private ModuleManager moduleManager = null;
  ProxyService proxyService = null;
  TenantStore tenantStore = null;
  LockedTypedMap1<Tenant> tenants = new LockedTypedMap1<>(Tenant.class);

  public TenantManager(ModuleManager moduleManager, TenantStore tenantStore) {
    this.moduleManager = moduleManager;
    this.tenantStore = tenantStore;
  }

  /**
   * Initialize the TenantManager.
   *
   * @param vertx
   * @param fut
   */
  public void init(Vertx vertx,  Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.init(vertx, "tenants", ires -> {
      if (ires.failed()) {
        fut.handle(new Failure<>(ires.getType(), ires.cause()));
        return;
      }
      loadTenants(fut);
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

  /**
   * Get the moduleManager.
   * @return
   */
  public ModuleManager getModuleManager() {
    return moduleManager;
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
      if (gres.failed() && gres.getType() != NOT_FOUND) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      } else if (gres.succeeded() && gres.result() != null) {
        fut.handle(new Failure<>(USER, "Duplicate tenant id " + id));
        return;
      }
      if (tenantStore == null) { // no db, just add it to shared mem
        tenants.add(id, t, ares -> {
          if (ares.failed()) {
            fut.handle(new Failure<>(ares.getType(), ares.cause()));
            return;
          }
          fut.handle(new Success<>(id));
        });
      } else { // insert into db first
        tenantStore.insert(t, res -> {
          if (res.failed()) {
            logger.warn("TenantManager: Adding " + id + " FAILED: ", res);
            fut.handle(new Failure<>(res.getType(), res.cause()));
            return;
          }
          tenants.add(id, t, ares -> { // and then into shared memory
            if (ares.failed()) {
              fut.handle(new Failure<>(ares.getType(), ares.cause()));
              return;
            }
            fut.handle(new Success<>(id));
          });
        });
      } // have db
    });
  } // insert

  public void updateDescriptor(TenantDescriptor td,
    Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = td.getId();
    tenants.get(id, gres -> {
      if (gres.failed() && gres.getType() != NOT_FOUND) {
        logger.warn("TenantManager: UpDesc: getting " + id + " FAILED: ", gres);
        fut.handle(new Failure<>(INTERNAL, ""));
        return;
      }
      Tenant oldT = gres.result();
      Tenant t;
      if (oldT == null) { // notfound
        t = new Tenant(td);
      } else {
        t = new Tenant(td, oldT.getEnabled());
      }
      if (tenantStore == null) {
        tenants.add(id, t, fut); // no database. handles success directly
        return;
      }
      tenantStore.updateDescriptor(td, upres -> {
        if (upres.failed()) {
          logger.warn("TenantManager: Updating database for " + id + " FAILED: ", upres);
          fut.handle(new Failure<>(INTERNAL, ""));
          return;
        }
        tenants.add(id, t, fut); // handles success
      });
    });
  }

  /**
   * List the Ids of all tenants.
   *
   * @param fut
   */
  public void getIds(Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    tenants.getKeys(res -> {
      if (res.failed()) {
        logger.warn("TenantManager: Getting keys FAILED: ", res);
        fut.handle(new Failure<>(INTERNAL, res.cause()));
        return;
      }
      fut.handle(new Success<>(res.result()));
    });
  }

  public void list(Handler<ExtendedAsyncResult<List<TenantDescriptor>>> fut) {
    tenants.getKeys(lres -> {
      if (lres.failed()) {
        logger.warn("TenantManager list: Getting keys FAILED: ", lres);
        fut.handle(new Failure<>(INTERNAL, lres.cause()));
        return;
      }
      List<String> ids = new ArrayList<>(lres.result());
      ids.sort(null); // to keep test resulsts consistent
      List<TenantDescriptor> tdl = new ArrayList<>();
      logger.debug("TenantManager list: " + Json.encode(ids));
      Iterator<String> it = ids.iterator();
      list_r(it, tdl, fut);
    });
  }

  /**
   * Recursive helper to list tenants.
   *
   * @param it iterator to recurse through
   * @param tdl list to build
   * @param fut
   */
  private void list_r(Iterator<String> it, List<TenantDescriptor> tdl,
    Handler<ExtendedAsyncResult<List<TenantDescriptor>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(tdl));
    } else {
      String tid = it.next();
      tenants.get(tid, gres -> {
        if (gres.failed()) {
          logger.warn("TenantManager list: Getting " + tid + " FAILED: ", gres);
          fut.handle(new Failure<>(gres.getType(), gres.cause()));
          return;
        }
        Tenant t = gres.result();
        TenantDescriptor td = t.getDescriptor();
        tdl.add(td);
        logger.debug("TenantManager list: Added " + tid + ":" + Json.encode(td));
        list_r(it, tdl, fut);
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
      return;
    }
    tenantStore.delete(id, dres -> {
      if (dres.failed() && dres.getType() != NOT_FOUND) {
        logger.warn("TenantManager: Deleting " + id + " FAILED: ", dres);
        fut.handle(new Failure<>(INTERNAL, dres.cause()));
        return;
      }
      tenants.remove(id, fut);
    });
  }


  /**
   * Check module dependencies and conflicts.
   *
   * @param tenant to check for
   * @param mod_from module to be removed. Ignored in the checks
   * @param mod_to module to be added
   * @param fut Callback for error messages, or a simple Success
   */
  private void checkDependencies(Tenant tenant,
    ModuleDescriptor mod_from, ModuleDescriptor mod_to,
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
      if (mod_from != null) {
        mods.remove(mod_from.getId());
      }
      if (mod_to != null) {
        ModuleDescriptor already = mods.get(mod_to.getId());
        if (already != null) {
          fut.handle(new Failure<>(USER,
            "Module " + mod_to.getId() + " already provided"));
          return;
        }
        mods.put(mod_to.getId(), mod_to);
      }
      String conflicts = moduleManager.checkAllConflicts(mods);
      String deps = moduleManager.checkAllDependencies(mods);
      if (conflicts.isEmpty() && deps.isEmpty()) {
        fut.handle(new Success<>());
        return;
      }
      fut.handle(new Failure<>(USER, conflicts + " " + deps));
    });
  }



  /**
   * Check dependencies after removing a module and adding one. Will not
   * actually add/remove modules for real.
   *
   * @param tenant to operate on
   * @param module_from module to be removed
   * @param module_to module to be added
   * @param fut callback with error message string, or success
   */
  public void updateModuleDepCheck(Tenant tenant,
    String module_from, String module_to,
    Handler<ExtendedAsyncResult<Void>> fut) {
    moduleManager.get(module_to, tres -> {
      if (tres.failed()) {
        if (tres.getType() == NOT_FOUND) {
          fut.handle(new Failure<>(NOT_FOUND,
            "Module " + module_to + " not found (t)"));
          return;
        }
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
        return;
      }
      ModuleDescriptor mod_to = tres.result();
      moduleManager.get(module_from, fres -> {
        if (fres.failed()) {
          if (tres.getType() == NOT_FOUND) {
            fut.handle(new Failure<>(NOT_FOUND,
              "Module " + module_from + " not found (f)"));
            return;
          }
          fut.handle(new Failure<>(fres.getType(), fres.cause()));
          return;
        }
        ModuleDescriptor mod_from = fres.result();
        checkDependencies(tenant, mod_from, mod_to, fut);
      });
    });
  }

  /**
   * Actually update the enabled modules. Assumes dependencies etc have been
   * checked.
   *
   * @param id - tenant to update for
   * @param module_from - module to be disabled, may be null if none
   * @param module_to - module to be enabled, may be null if none
   * @param fut callback for errors.
   */
  public void updateModuleCommit(String id,
    String module_from, String module_to,
    Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.get(id, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      Tenant t = gres.result();
      if (module_from != null) {
        t.disableModule(module_from);
      }
      if (module_to != null) {
        t.enableModule(module_to);
      }
      if (tenantStore == null) {
        tenants.put(id, t, pres -> {
          if (pres.failed()) {
            fut.handle(new Failure<>(INTERNAL, pres.cause()));
            return;
          }
          fut.handle(new Success<>());
          return;
        });
      } else {
          tenantStore.updateModules(id, t.getEnabled(), ures -> {
            if (ures.failed()) {
              fut.handle(new Failure<>(ures.getType(), ures.cause()));
              return;
            }
            tenants.put(id, t, pres -> {
              if (pres.failed()) {
                fut.handle(new Failure<>(INTERNAL, pres.cause()));
                return;
              }
              fut.handle(new Success<>());
          });
        });
      }
    });
  }

  /**
   * Enable a module for a tenant and disable another. Checks dependencies,
   * invokes the tenant interface, and the tenantPermissions interface, and
   * finally marks the modules as enabled and disabled.
   *
   * @param tenantId - id of the the tenant in question
   * @param module_from id of the module to be disabled, or null
   * @param module_to id of the module to be enabled, or null
   * @param pc proxyContext for proper logging, etc
   * @param fut callback with success, or various errors
   *
   * To avoid too much callback hell, this has been split into several helpers.
   */
  public void enableAndDisableModule(String tenantId,
    String module_from, String module_to, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {
    pc.debug("enableAndDisableModule for " + tenantId
      + " fr=" + module_from + " to=" + module_to);
    tenants.get(tenantId, tres -> {
      if (tres.failed()) {
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
        return;
      }
      Tenant tenant = tres.result();
      updateModuleDepCheck(tenant, module_from, module_to, cres -> {
        if (cres.failed()) {
          pc.debug("enableAndDisableModule: depcheck fail: " + cres.cause().getMessage());
          fut.handle(new Failure<>(cres.getType(), cres.cause()));
          return;
        }
        pc.debug("enableAndDisableModule: depcheck ok");
        if (module_to == null || module_to.isEmpty()) {
          ead4commit(tenant, module_from, module_to, pc, fut);
          return;
        } else {
          ead1TenantInterface(tenant, module_from, module_to, pc, fut);
          return;
        }
      });
    });
  }

  /**
   * enableAndDisable helper 1: call the tenant interface.
   *
   * @param tenant
   * @param module_from
   * @param module_to
   * @param fut
   */
  private void ead1TenantInterface(Tenant tenant,
    String module_from, String module_to, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {
    getTenantInterface(module_to, ires -> {
      if (ires.failed()) {
        if (ires.getType() == NOT_FOUND) {
          logger.debug("eadTenantInterface: "
            + module_to + " has no support for tenant init");
          ead2PermMod(tenant, module_from, module_to, pc, fut);
          return;
        }
        fut.handle(new Failure<>(ires.getType(), ires.cause()));
        return;
      }
      String tenInt = ires.result();
      logger.debug("eadTenantInterface: tenint=" + tenInt);
      JsonObject jo = new JsonObject();
      jo.put("module_to", module_to);
      if (module_from != null) {
        jo.put("module_from", module_from);
      }
      String req = jo.encodePrettily();
      proxyService.callSystemInterface(tenant.getId(),
        module_to, tenInt, req, pc, cres -> {
        if (cres.failed()) {
          fut.handle(new Failure<>(cres.getType(), cres.cause()));
          return;
        }
        // TODO - Copy X-headers over to ctx.resp
        ead2PermMod(tenant, module_from, module_to, pc, fut);
      });
    });
  }

  /**
   * enableAndDisable helper 2: Choose which permission module to invoke.
   *
   * @param tenant
   * @param module_from
   * @param module_to
   * @param pc
   * @param fut
   */
  private void ead2PermMod(Tenant tenant,
    String module_from, String module_to, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {
    ModuleManager modMan = getModuleManager();
    if (modMan == null) { // Should never happen
      fut.handle(new Failure<>(INTERNAL,
        "eadTenantPermissions: No moduleManager found. "
        + "Can not make _tenantPermissions request"));
      return;
    }
    // TODO - check if we have no permissions, skip the rest
    modMan.get(module_to, mres -> {
      if (mres.failed() && mres.getType() != NOT_FOUND) { // something really wrong
        fut.handle(new Failure<>(mres.getType(), mres.cause()));
        return;
      }
      ModuleDescriptor md = mres.result();
      if (md != null && md.getSystemInterface("_tenantPermissions") != null) {
        pc.debug("Using the tenantPermissions of this module itself");
        ead3Permissions(tenant, module_from, module_to, md, md, pc, fut);
        return;
      }
      findSystemInterface(tenant.getId(), "_tenantPermissions", res -> {
        if (res.failed()) {
          if (res.getType() == NOT_FOUND) { // no perms interface. TODO
            // just continue with the process. Should probably trigger an error
            pc.debug("enablePermissions: No tenantPermissions interface found. "
              + "Carrying on without it.");
            ead4commit(tenant, module_from, module_to, pc, fut);
            return;
          }
          pc.responseError(res.getType(), res.cause());
          return;
        }
        ModuleDescriptor permsMod = res.result();
        ead3Permissions(tenant, module_from, module_to, md, permsMod, pc, fut);
        return;
      });
    });
  }

  /**
   * enableAndDisable helper 2: Make the tenantPermissions call.
   *
   * @param tenant
   * @param module_from
   * @param module_to
   * @param md
   * @param permsModule
   * @param pc
   * @param fut
   */
  private void ead3Permissions(Tenant tenant,
    String module_from, String module_to,
    ModuleDescriptor md, ModuleDescriptor permsModule,
    ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {
    pc.debug("ead3Permissions: Perms interface found in "
      + permsModule.getId());
    PermissionList pl = new PermissionList(module_to, md.getPermissionSets());
    String pljson = Json.encodePrettily(pl);
    pc.debug("ead3Permissions Req: " + pljson);

    ModuleInterface permInt = permsModule.getSystemInterface("_tenantPermissions");
    String PermPath = "";
    List<RoutingEntry> routingEntries = permInt.getAllRoutingEntries();
    if (!routingEntries.isEmpty()) {
      for (RoutingEntry re : routingEntries) {
        if (re.match(null, "POST")) {
          PermPath = re.getPath();
          if (PermPath == null || PermPath.isEmpty()) {
            PermPath = re.getPathPattern();
          }
        }
      }
    }
    if (PermPath == null || PermPath.isEmpty()) {
      fut.handle(new Failure<>(USER,
        "Bad _tenantPermissions interface in module " + permsModule.getId()
        + ". No path to POST to"));
      return;
    }
    pc.debug("ead3Permissions: " + permsModule.getId() + " and " + PermPath);
    proxyService.callSystemInterface(tenant.getId(),
      permsModule.getId(), PermPath, pljson, pc, cres -> {
      if (cres.failed()) {
        fut.handle(new Failure<>(cres.getType(), cres.cause()));
        return;
      }
      pc.debug("enablePermissions: request to " + permsModule.getNameOrId()
        + " succeeded for module " + module_to + " and tenant " + tenant.getId());
      ead4commit(tenant, module_from, module_to, pc, fut);
    });
  }

  private void ead4commit(Tenant tenant,
    String module_from, String module_to, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {
    pc.debug("ead4commit: " + module_from + " " + module_to);
    updateModuleCommit(tenant.getId(), module_from, module_to, ures -> {
      if (ures.failed()) {
        pc.responseError(ures.getType(), ures.cause());
        return;
      }
      pc.debug("ead4commit done");
      fut.handle(new Success<>());
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
  public void getTenantInterface(String module, Handler<ExtendedAsyncResult<String>> fut) {
    moduleManager.get(module, gres -> {
      if (gres.failed()) {
        if (gres.getType() == NOT_FOUND) {
          fut.handle(new Success<>(""));  // TODO - Or return error?
        } else {
          fut.handle(new Failure<>(gres.getType(), gres.cause()));
        }
        return;
      }
      ModuleDescriptor md = gres.result();
      String ti = md.getTenantInterface();
      if (ti != null && !ti.isEmpty()) {
        fut.handle(new Success<>(ti)); // DEPRECATED - warned when POSTing a ModuleDescriptor
        return;
      }
      ModuleInterface[] prov = md.getProvides();
      logger.debug("findTenantInterface: prov: " + Json.encode(prov));
      if (prov != null) {
        for (ModuleInterface pi : prov) {
          logger.debug("findTenantInterface: Looking at " + pi.getId());
          if ("_tenant".equals(pi.getId())) {
            if ("system".equals(pi.getInterfaceType())) { // looks like a new type
              List<RoutingEntry> res = pi.getAllRoutingEntries();
              if (!res.isEmpty()) {
                // TODO - Check the version of the interface. Must be 1.0
                for (RoutingEntry re : res) {
                  if (re.match(null, "POST")) {
                    if (re.getPath() != null) {
                      // TODO - Remove this in version 2.0
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
              logger.warn("Tenant interface for module '" + module + "' "
                + "has no suitable RoutingEntry. Can not call the Tenant API");
              fut.handle(new Success<>(""));
              return; // TODO Process this as an error!
            }
            logger.warn("Module '" + module + "' uses old-fashioned tenant "
              + "interface. Define InterfaceType=system, and add a RoutingEntry."
              + " Falling back to calling /_/tenant.");
            fut.handle(new Success<>("/_/tenant"));
            return;
          }
        }
        fut.handle(new Failure<>(NOT_FOUND, "No _tenant interface found for "
          + module));
        return;
      }
      fut.handle(new Failure<>(NOT_FOUND, "No _tenant interface found for "
        + module + " (it provides nothing at all?)"));
      return;
    });
  }

  /**
   * Find (the first) module that provides a given system interface. Module must
   * be enabled for the tenant.
   *
   * @param tenantId tenant to check for
   * @param interfaceName interface name to look for
   * @param fut callback with a @return ModuleDescriptor for the module
   *
   * TODO - Take a version too, pass it to getSystemInterface, check there
   */
  public void findSystemInterface(String tenantId, String interfaceName,
    Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    tenants.get(tenantId, gres -> {
      if (gres.failed()) {
        logger.debug("findSystemInterface: no tenant " + tenantId + " found, bailing out");
        fut.handle(new Failure<>(INTERNAL, gres.cause()));
        return;
      }
      Tenant tenant = gres.result();
      moduleManager.list(lres -> {
        if (lres.failed()) { // should not happen
          fut.handle(new Failure<>(lres.getType(), lres.cause()));
          return;
        }
        Collection<String> modlist = lres.result();
        logger.debug("findSystemInterface " + interfaceName
          + ": module list: " + Json.encode(modlist));
        Iterator<String> it = modlist.iterator();
        findSystemInterfaceR(tenant, interfaceName, it, fut);
      });
    });
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
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
        return;
      }
      Tenant tenant = tres.result();
      ArrayList<ModuleDescriptor> mdList = new ArrayList<>();
      moduleManager.getEnabledModules(tenant, mres -> {
        if (mres.failed()) {
          fut.handle(new Failure<>(mres.getType(), mres.cause()));
          return;
        }
        List<ModuleDescriptor> modlist = mres.result();
        for (ModuleDescriptor md : modlist) {
          for (ModuleInterface provide : md.getProvides()) {
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

  /**
   * List modules for a given tenant.
   *
   * @param id
   * @param fut calbback with a list of moduleIds
   */
  public void listModules(String id, Handler<ExtendedAsyncResult<List<String>>> fut) {
    tenants.get(id, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(INTERNAL, gres.cause()));
        return;
      }
      Tenant t = gres.result();
      List<String> tl = new ArrayList<>(t.listModules());
      tl.sort(null);
      fut.handle(new Success<>(tl));
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
        fut.handle(new Failure<>(INTERNAL, kres.cause()));
        return;
      }
      Collection<String> tkeys = kres.result();
      Iterator<String> it = tkeys.iterator();
      getModuleUserR(mod, it, fut);
    });
  }

  private void getModuleUserR(String mod, Iterator<String> it, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) { // no problems found
      fut.handle(new Success<>());
    } else {
      String tid = it.next();
      tenants.get(tid, gres -> {
        if (gres.failed()) {
          fut.handle(new Failure<>(INTERNAL, gres.cause()));
          return;
        }
        Tenant t = gres.result();
        if (t.isEnabled(mod)) {
          fut.handle(new Failure<>(ANY, tid));
          return;
        }
        getModuleUserR(mod, it, fut);
      });
    }
  }


  /**
   * Load tenants from the store into the shared memory map
   *
   * @param fut
   */
  public void loadTenants(Handler<ExtendedAsyncResult<Void>> fut) {
    if (tenantStore == null) {  // no storage, we are done.
      logger.info("No storage to load tenants from starting with empty");
      fut.handle(new Success<>());
      return;
    }
    tenants.getKeys(gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      Collection<String> keys = gres.result();
      if (!keys.isEmpty()) {
        logger.info("Not loading tenants, looks like someone already did");
        fut.handle(new Success<>());
        return;
      }
      tenantStore.listTenants(lres -> {
        if (lres.failed()) {
          fut.handle(new Failure<>(INTERNAL, lres.cause()));
          return;
        }
        Iterator<Tenant> it = lres.result().iterator();
        loadR(it, fut);
      });
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
        return;
      }
      logger.debug("Loaded tenant " + t.getId());
      loadR(it, fut);
    });
  }

} // class
