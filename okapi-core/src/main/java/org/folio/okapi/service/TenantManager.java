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
        fut.handle(new Failure<>(INTERNAL, ""));
        return;
      }
      tenants.remove(id, fut); // remove in any case
    });
  }

  private String checkOneDependency(Tenant tenant, ModuleDescriptor mod_from,
    ModuleDescriptor mod_to, ModuleInterface req) {
    ModuleInterface seenversion = null;
    for (String enabledModule : tenant.listModules()) {
      ModuleDescriptor rm = moduleManager.get(enabledModule);
      if (rm != mod_from) {
        ModuleInterface[] provides = rm.getProvides();
        if (provides != null) {
          for (ModuleInterface pi : provides) {
            logger.debug("Checking dependency of " + mod_to.getId()
              + ": " + req.getId() + " " + req.getVersion()
              + " against " + pi.getId() + " " + pi.getVersion());
            if (req.getId().equals(pi.getId())) {
              if (seenversion == null || pi.compare(req) > 0) {
                seenversion = pi;
              }
              if (pi.isCompatible(req)) {
                return "";
              }
            }
          }
        }
      }
    }
    String msg;
    if (seenversion == null) {
      msg = "Can not enable module '" + mod_to.getId() + "'"
        + ", missing dependency " + req.getId() + ": " + req.getVersion();
    } else {
      msg = "Can not enable module '" + mod_to.getId() + "'"
        + "Incompatible version for " + req.getId() + ". "
        + "Need " + req.getVersion() + ". have " + seenversion.getVersion();
    }
    logger.debug(msg);
    return msg;
  }

  private String checkOneConflict(Tenant tenant, ModuleDescriptor mod_from,
    ModuleDescriptor mod_to, ModuleInterface prov) {
    for (String enabledModule : tenant.listModules()) {
      ModuleDescriptor rm = moduleManager.get(enabledModule);
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
              return msg;
            }
          }
        }
      }
    }
    return "";
  }

  private String checkDependencies(Tenant tenant, ModuleDescriptor mod_from,
    ModuleDescriptor mod_to) {
    ModuleInterface[] requires = mod_to.getRequires();
    if (requires != null) {
      for (ModuleInterface req : requires) {
        String one = checkOneDependency(tenant, mod_from, mod_to, req);
        if (!one.isEmpty()) {
          return one;
        }
      }
    }
    ModuleInterface[] provides = mod_to.getProvides();
    if (provides != null) {
      for (ModuleInterface prov : provides) {
        if ( ! prov.getId().startsWith("_")) { // skip system interfaces like _tenant
          String one = checkOneConflict(tenant, mod_from, mod_to, prov);
          if (!one.isEmpty()) {
            return one;
          }
        }
      }
    }
    return "";
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
  public String updateModuleDepCheck(Tenant tenant, String module_from, String module_to) {
    ModuleDescriptor mod_to = null;
    if (module_to != null) {
      mod_to = moduleManager.get(module_to);
      if (mod_to == null) {
        return "module " + module_to + " not found";
      }
    }
    ModuleDescriptor mod_from = null;
    if (module_from != null) {
      mod_from = moduleManager.get(module_from);
    }
    return checkDependencies(tenant, mod_from, mod_to);
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
  public String checkNoDependency(Tenant tenant, String module) {
    ModuleDescriptor mod = moduleManager.get(module);
    if (mod == null) { // should not happen
      logger.warn("Module " + module + " not found when checking delete dependencies!");
      return "";
    }
    logger.debug("Checking that we can delete " + module);
    ModuleInterface[] provides = mod.getProvides();
    if (provides == null) {
      return ""; // nothing can depend on this one
    }
    for (ModuleInterface prov : provides) {
      logger.debug("Checking provided service " + prov.getId());
      for (String enabledmodule : tenant.listModules()) {
        ModuleDescriptor em = moduleManager.get(enabledmodule);
        ModuleInterface[] req = em.getRequires();
        logger.debug("Checking provided service " + prov.getId() + " against " + enabledmodule);
        if (req != null) {
          for (ModuleInterface ri : req) {
            if (prov.getId().equals(ri.getId())) {
              String err = module + " " + prov.getId() + " is used by " + enabledmodule;
              logger.debug("checkNoDependency: " + err);
              return err;
            }
          }
        }
      }

    }
    return "";
  }

  /**
   * Disable a module for a given tenant.
   *
   * @param id
   * @param module
   */
  public void disableModule(String id, String module,
    Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.get(id, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(INTERNAL, gres.cause()));
        return;
      }
      Tenant t = gres.result();
      String err = checkNoDependency(t, module);
      logger.debug("disableModule: Dependency error " + err);
      if (!err.isEmpty()) {
        fut.handle(new Failure<>(USER, gres.cause()));
        return;
      }
      updateModuleCommit(id, module, null, fut);
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
   * @return the path to the interface, or "" if not supported.
   */
  public String getTenantInterface(String module) {
    ModuleDescriptor md = this.moduleManager.get(module);
    if (md == null) {
      return "";
    }
    String ti = md.getTenantInterface();
    if (ti != null && !ti.isEmpty()) {
      return ti; // DEPRECATED - warned when POSTing a ModuleDescriptor
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
                    return re.getPath();
                  }
                  if (re.getPathPattern() != null) {
                    return re.getPathPattern();
                  }
                }
              }
            }
            logger.warn("Tenant interface for module '" + module + "' "
              + "has no suitable RoutingEntry. Can not call the Tenant API");
            return "";
          }
          logger.warn("Module '" + module + "' uses old-fashioned tenant "
            + "interface. Define InterfaceType=system, and add a RoutingEntry."
            + " Falling back to calling /_/tenant.");
          return "/_/tenant";
        }
      }
    }
    return "";
  }

  /**
   * Find (the first) module that provides a given system interface. Module must
   * be enabled for the tenant.
   *
   * @return ModuleDescriptor for the module, or null if none found.
   *
   * TODO - Take a version too, pass it to getSystemInterface, check there
   */
  public void findSystemInterface(
    String tenantId, String interfaceName, Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    tenants.get(tenantId, gres -> {
      if (gres.failed()) {
        logger.debug("findSystemInterface: no tenant " + tenantId + " found, bailing out");
        fut.handle(new Failure<>(INTERNAL, gres.cause()));
        return;
      }
      Tenant tenant = gres.result();
      Set<String> modlist = this.moduleManager.list();
      logger.debug("findSystemInterface " + interfaceName + ": module list: " + Json.encode(modlist));
      for (String m : modlist) {
        ModuleDescriptor md = this.moduleManager.get(m);
        logger.debug("findSystemInterface: looking at " + m + ": "
          + "en: " + tenant.isEnabled(m) + " si: " + md.getSystemInterface(interfaceName));

        if (md.getSystemInterface(interfaceName) != null
          && tenant.isEnabled(m)) {
          logger.debug("findSystemInterface: found " + m);
          fut.handle(new Success<>(md));
          return;
        }
      }
      fut.handle(new Failure<>(NOT_FOUND, "No module provides " + interfaceName));
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
      List<String> tl = new ArrayList(t.listModules());
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
    } else {
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
  }

} // class
