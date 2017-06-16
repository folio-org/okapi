package org.folio.okapi.service;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInterface;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.util.LockedTypedMap1;

/**
 * Manages the tenants in the shared map, and passes updates to the database.
 */
public class TenantManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private ModuleManager moduleManager = null;
  TenantStore tenantStore = null;
  LockedTypedMap1<Tenant> tenants = new LockedTypedMap1<>(Tenant.class);

  public TenantManager(ModuleManager moduleManager, TenantStore tenantStore) {
    this.moduleManager = moduleManager;
    this.tenantStore = tenantStore;
  }

  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.init(vertx, "tenants", ires -> {
      if (ires.failed()) {
        fut.handle(new Failure<>(ires.getType(), ires.cause()));
      } else {
        loadTenants(fut);
      }
    });
  }

  /**
   * Get the moduleManager.
   */
  public ModuleManager getModuleManager() {
    return moduleManager;
  }

  public void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getId();
    tenants.get(id, gres -> {
      if (gres.failed() && gres.getType() != NOT_FOUND) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else if (gres.succeeded() && gres.result() != null) {
        fut.handle(new Failure<>(USER, "Duplicate tenant id " + id));
      } else {
        tenants.add(id, t, res -> {
          if (res.failed()) {
            logger.warn("TenantManager: Adding " + id + " FAILED: ", res);
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            if (tenantStore != null) {
              tenantStore.insert(t, fut);
            } else {
              fut.handle(new Success<>(id));
            }
          }
        });
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
        return;
      }
      Tenant oldT = gres.result();
      Tenant t;
      if (oldT == null) { // notfound
        t = new Tenant(td);
      } else {
        t = new Tenant(td, oldT.getEnabled());
      }
      if (tenantStore != null) {
        tenantStore.updateDescriptor(td, upres -> {
          if (upres.failed()) {
            logger.warn("TenantManager: Updating database for " + id + " FAILED: ", upres);
            fut.handle(new Failure<>(INTERNAL, ""));
            return;
          }
          tenants.add(id, t, fut); // handles success
        });
      } else { // no database
        tenants.add(id, t, fut); // handles success
      }
    });
  }

  public void getIds(Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    tenants.getKeys(res -> {
      if (res.failed()) {
        logger.warn("TenantManager: Getting keys FAILED: ", res);
        fut.handle(new Failure<>(INTERNAL, ""));
        return;
      }
      fut.handle(new Success<>(res.result()));
    });
  }


  public void list(Handler<ExtendedAsyncResult<List<TenantDescriptor>>> fut) {
    tenants.getKeys(lres -> {
      if (lres.failed()) {
        logger.warn("TenantManager list: Getting keys FAILED: ", lres);
        fut.handle(new Failure<>(INTERNAL, ""));
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

  public void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut) {
    tenants.get(id, fut);
  }

  /**
   * Delete a tenant.
   *
   * @param id
   * @returns a Boolean in the callback, true if done, false if not there
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
   * Check that some (enabled) module provides the required interface.
   *
   * @param tenant to have the module enabled
   * @param mod_from module to be disabled. Does not count as provides
   * @param mod_to module to be enabled. The one that needs the interface.
   * @param req required interface.
   * @param fut callback with an error message,
   */
  private void checkOneDependency(Tenant tenant, ModuleDescriptor mod_from,
    ModuleDescriptor mod_to, ModuleInterface req,
    Handler<ExtendedAsyncResult<Void>> fut) {
    ModuleInterface seenversion = null;
    Set<String> enabledModules = tenant.listModules();
    Iterator<String> it = enabledModules.iterator();
    checkOneDependencyAgainstOneModule(mod_from, mod_to, req, it, null, fut);
  }

  /**
   * Recursive helper to check dependencies.
   *
   * @param mod_from
   * @param mod_to
   * @param req
   * @param it
   * @param seenversion for error messages
   * @param fut
   */
  private void checkOneDependencyAgainstOneModule(ModuleDescriptor mod_from,
    ModuleDescriptor mod_to, ModuleInterface req,
    Iterator<String> it, ModuleInterface seenversion,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) { // did not find it
      String msg;
      if (seenversion == null) {
        msg = "Can not enable module '" + mod_to.getId() + "'"
          + ", missing dependency " + req.getId() + ": " + req.getVersion();
      } else {
        msg = "Can not enable module '" + mod_to.getId() + "'"
          + "Incompatible version for " + req.getId() + ". "
          + "Need " + req.getVersion() + ". have " + seenversion.getVersion();
      }
      fut.handle(new Failure<>(USER, msg));
      return;
    }
    String enabledModule = it.next();
    moduleManager.get(enabledModule, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      ModuleDescriptor rm = gres.result();
      if (rm != mod_from) {
        ModuleInterface[] provides = rm.getProvides();
        if (provides != null) {
          ModuleInterface sv = seenversion; // seenversion needs to be final...
          for (ModuleInterface pi : provides) {
            logger.debug("Checking dependency of " + mod_to.getId()
              + ": " + req.getId() + " " + req.getVersion()
              + " against " + pi.getId() + " " + pi.getVersion());
            if (pi.isCompatible(req)) {
              fut.handle(new Success<>());  // Found it!
              return;
            }
            if (req.getId().equals(pi.getId())) {
              if (sv == null || pi.compare(req) > 0) {
                sv = pi;
              }
            }
          }
          checkOneDependencyAgainstOneModule(mod_from, mod_to, req, it, sv, fut);
        }
      }
    });
  }

  /**
   * Check that nobody else provides the same interface.
   *
   * @param tenant to check for
   * @param mod_from module to be disabled. Not checked against.
   * @param mod_to module to be enabled, provider of the interface
   * @param prov interface that we are about to provide
   * @param fut Success, or error with message.
   */
  private void checkOneConflict(Tenant tenant, ModuleDescriptor mod_from,
    ModuleDescriptor mod_to, ModuleInterface prov,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (prov.getId().startsWith("_")) {
      logger.debug("Not checking old-style system interface " + prov.getId());
      fut.handle(new Success<>());
      return;
    }
    Set<String> enabledModules = tenant.listModules();
    Iterator<String> it = enabledModules.iterator();
    checkOneConflictingModule(tenant, mod_from, mod_to, prov, it, fut);
  }

  /**
   * Recursive helper for checkOneConflict
   */
  private void checkOneConflictingModule(Tenant tenant, ModuleDescriptor mod_from,
    ModuleDescriptor mod_to, ModuleInterface prov, Iterator<String> it,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>());  // nobody conflicted, declare success
      return;
    }
    String enabledModule = it.next();
    moduleManager.get(enabledModule, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      ModuleDescriptor rm = gres.result();
      if (mod_from != rm) {
        ModuleInterface[] provides = rm.getProvides();
        if (provides != null) {
          for (ModuleInterface pi : provides) {
            logger.debug("Checking conflict of " + mod_to.getId() + ": "
              + prov.getId() + " " + prov.getVersion()
              + " against " + pi.getId() + " " + pi.getVersion());
            if (prov.getId().equals(pi.getId())) {
              String msg = "Can not enable module '" + mod_to.getId() + "'"
                + " for tenant '" + tenant.getId() + "'"
                + " because of conflict:"
                + " Interface '" + prov.getId() + "' already provided by module '"
                + enabledModule + "'";
              logger.debug(msg);
              fut.handle(new Failure<>(USER, msg));
              return;
            }
          }
        }
      }
      logger.debug("checkOneConflictingModule: " + enabledModule + " ok");
      checkOneConflictingModule(tenant, mod_from, mod_to, prov, it, fut);
    });
  }

  /**
   * Check module dependencies. Part 1, set up recursion
   *
   * @param tenant to check for
   * @param mod_from module to be removed. Ignored in the checks
   * @param mod_to module to be added
   * @param fut Callback for error messages, or a simple Success
   */
  private void checkDependencies(Tenant tenant,
    ModuleDescriptor mod_from, ModuleDescriptor mod_to,
    Handler<ExtendedAsyncResult<Void>> fut) {
    ModuleInterface[] requires = mod_to.getRequires();
    checkDependenciesOneReq(tenant, mod_from, mod_to, requires, 0, fut);
  }

  /**
   * First of the recursive helper for checkDependencies. Checks one dependency.
   */
  private void checkDependenciesOneReq(Tenant tenant,
    ModuleDescriptor mod_from, ModuleDescriptor mod_to,
    ModuleInterface[] requires, int i,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (requires == null || i >= requires.length || requires[i] == null) {
      checkDependenciesOneConflict(tenant, mod_from, mod_to, mod_to.getProvides(), 0, fut);
    } else {
      checkOneDependency(tenant, mod_from, mod_to, requires[i], res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        checkDependenciesOneReq(tenant, mod_from, mod_to, requires, i + 1, fut);
      });
    }
  }

  /**
   * Second of the recursive helpers for checkDependencies. Checks one conflict.
   */
  private void checkDependenciesOneConflict(Tenant tenant,
    ModuleDescriptor mod_from, ModuleDescriptor mod_to,
    ModuleInterface[] provides, int i,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (provides == null || i >= provides.length || provides[i] == null) {
      // all checks ok, finally!
      fut.handle(new Success<>());
      return;
    }
    checkOneConflict(tenant, mod_from, mod_to, provides[i], res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      checkDependenciesOneConflict(tenant, mod_from, mod_to, provides, i + 1, fut);
    });
  }


  /**
   * Check dependencies after removing a module and adding one. Will not
   * actually add/remove modules for real.
   *
   * @param tenant to operate on
   * @param module_from module to be removed
   * @param module_to module to be added
   * @return Error message string, or "" if all is well
   */
  public void updateModuleDepCheck(Tenant tenant,
    String module_from, String module_to,
    Handler<ExtendedAsyncResult<Void>> fut) {
    moduleManager.get(module_to, tres -> {
      if (tres.failed()) {
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
        return;
        // return "module " + module_to + " not found";  // Old error message
      }
      ModuleDescriptor mod_to = tres.result();
      moduleManager.get(module_from, fres -> {
        if (fres.failed()) {
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
   * @param timestamp
   * @param module_from - module to be disabled
   * @param module_to - module to be enabled
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
      tenants.put(id, t, pres -> {
        if (pres.failed()) {
          fut.handle(new Failure<>(INTERNAL, pres.cause()));
          return;
        }
        if (tenantStore != null) {
          tenantStore.updateModules(id, t.getEnabled(), ures -> {
            if (ures.failed()) {
              fut.handle(new Failure<>(ures.getType(), ures.cause()));
            } else {
              fut.handle(new Success<>());
            }
          });
        } else {
          fut.handle(new Success<>());
        }
      });
    });
  }

  /**
   * Check that no enabled module depends on any service provided by this
   * module.
   *
   * @param tenant
   * @param module
   * @return "" if it is ok to delete the module, or an error message
   */
  public void checkNoDependency(Tenant tenant, String module,
    Handler<ExtendedAsyncResult<Void>> fut) {
    moduleManager.get(module, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      ModuleDescriptor mod = gres.result();
      logger.debug("Checking that we can delete " + module);
      ModuleInterface[] provides = mod.getProvides();
      if (provides == null) { // nothing can depend on a module that provides nothing
        fut.handle(new Success<>());
        return;
      }
      Set<String> enabledModules = tenant.listModules();

      checkNoOneDep(tenant, enabledModules, mod, provides, 0, fut);
    });
  }

  private void checkNoOneDep(Tenant tenant, Set<String> enabledModules,
    ModuleDescriptor module,
    ModuleInterface[] provides, int i,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (i >= provides.length || provides[i] == null) {
      fut.handle(new Success<>()); // all checks out
      return;
    }
    ModuleInterface prov = provides[i];
    logger.debug("Checking provided service " + prov.getId());
    Iterator<String> it = enabledModules.iterator();
    checkNoOneDepEn(tenant, it, module, prov, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      checkNoOneDep(tenant, enabledModules, module, provides, i + 1, fut);
    });
  }

  private void checkNoOneDepEn(Tenant tenant, Iterator<String> it,
    ModuleDescriptor module, ModuleInterface prov,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>());
      return;
    }
    String enabledModuleId = it.next();
    moduleManager.get(enabledModuleId, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      ModuleDescriptor em = gres.result();
      ModuleInterface[] req = em.getRequires();
      logger.debug("Checking provided service " + prov.getId()
        + " against " + enabledModuleId);
      if (req != null) {
        for (ModuleInterface ri : req) {
          if (prov.getId().equals(ri.getId())) {
            String err = "Module " + prov.getId() + " is used by " + enabledModuleId;
            logger.debug("checkNoDependency: " + err);
            fut.handle(new Failure<>(USER, err));
            return;
          }
        }
      }
      checkNoOneDepEn(tenant, it, module, prov, fut);
    });
  }

  /**
   * Disable a module for a given tenant.
   *
   * @param tenantId
   * @param moduleId
   * @param fut
   */
  public void disableModule(String tenantId, String moduleId,
    Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.get(tenantId, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      Tenant t = gres.result();
      checkNoDependency(t, moduleId, cres -> {
        if (cres.failed()) {
          logger.debug("disableModule: Dependency error " + cres.cause().getMessage());
          fut.handle(new Failure<>(cres.getType(), cres.cause()));
          return;
        }
        updateModuleCommit(tenantId, moduleId, null, fut);
      });
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
   * @return ModuleDescriptor for the module, or null if none found.
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


  /**
   * List modules for a given tenant.
   *
   * @param id
   * @return null if no such tenant, or a list (possibly empty)
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
      } else {
        Collection<String> keys = gres.result();
        if (!keys.isEmpty()) {
          logger.info("Not loading tenants, looks like someone already did");
          fut.handle(new Success<>());
        } else {
          tenantStore.listTenants(lres -> {
            if (lres.failed()) {
              fut.handle(new Failure<>(INTERNAL, lres.cause()));
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
      logger.info("All tenants deployed");
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
