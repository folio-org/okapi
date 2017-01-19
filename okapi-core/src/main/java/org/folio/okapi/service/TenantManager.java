package org.folio.okapi.service;

import com.codahale.metrics.Timer;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInterface;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.util.DropwizardHelper;

/**
 * Manages the tenants in the run-time system. These will be modified by the web
 * service, and (more often) reloaded from the storage. Note that these are all
 * in-memory operations, so there is no need to use vert.x callbacks for this.
 */
public class TenantManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private ModuleManager moduleManager = null;
  Map<String, Tenant> tenants = new HashMap<>();

  public TenantManager(ModuleManager moduleManager) {
    String metricKey = "tenants.count";
    this.moduleManager = moduleManager;
    DropwizardHelper.registerGauge(metricKey, () -> tenants.size());
  }

  public ModuleManager getModuleManager() {
    return moduleManager;
  }

  public boolean insert(Tenant t) {
    String id = t.getId();
    Timer.Context tim = DropwizardHelper.getTimerContext("tenants." + id + ".create");
    if (tenants.containsKey(id)) {
      logger.debug("Not inserting duplicate '" + id + "':" + Json.encode(t));
      return false;
    }
    tenants.put(id, t);
    tim.close();
    return true;
  }

  public boolean updateDescriptor(TenantDescriptor td, long ts) {
    Tenant t;
    final String id = td.getId();
    if (!tenants.containsKey(td.getId())) {
      t = new Tenant(td);
    } else {
      Tenant oldT = tenants.get(id);
      t = new Tenant(td, oldT.getEnabled());
    }
    t.setTimestamp(ts);
    tenants.put(id, t);
    return true;
  }

  public Set<String> getIds() {
    Set<String> ids = tenants.keySet();
    return ids;
  }

  public Tenant get(String id) {
    return tenants.get(id);
  }

  /**
   * Delete a tenant.
   *
   * @param id
   * @return true on success, false if not there.
   */
  public boolean delete(String id) {
    if (!tenants.containsKey(id)) {
      logger.debug("TenantManager: Tenant '" + id + "' not found, can not delete");
      return false;
    }
    Timer.Context tim = DropwizardHelper.getTimerContext("tenants." + id + ".delete");
    tenants.remove(id);
    tim.close();
    return true;
  }

  private String checkOneDependency(Tenant tenant, ModuleDescriptor md, ModuleInterface req) {
    ModuleInterface seenversion = null;
    for (String enabledModule : tenant.listModules()) {
      ModuleDescriptor rm = moduleManager.get(enabledModule);
      ModuleInterface[] provides = rm.getProvides();
      if (provides != null) {
        for (ModuleInterface pi : provides) {
          logger.debug("Checking dependency of " + md.getId() + ": "
                  + req.getId() + " " + req.getVersion()
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
    String msg;
    if (seenversion == null) {
      msg = "Can not enable module '" + md.getId() + "'"
              + ", missing dependency " + req.getId() + ": " + req.getVersion();
    } else {
      msg = "Can not enable module '" + md.getId() + "'"
              + "Incompatible version for " + req.getId() + ". "
              + "Need " + req.getVersion() + ". have " + seenversion.getVersion();
    }
    logger.debug(msg);
    return msg;
  }
  private String checkOneConflict(Tenant tenant, ModuleDescriptor md, ModuleInterface prov) {
    ModuleInterface seenversion = null;
    for (String enabledModule : tenant.listModules()) {
      ModuleDescriptor rm = moduleManager.get(enabledModule);
      ModuleInterface[] provides = rm.getProvides();
      if (provides != null) {
        for (ModuleInterface pi : provides) {
          logger.debug("Checking conflict of " + md.getId() + ": "
                  + prov.getId() + " " + prov.getVersion()
                  + " against " + pi.getId() + " " + pi.getVersion());
          if (prov.getId().equals(pi.getId())) {
            String msg = "Can not enable module '" +  md.getId() + "'"
              + " for tenant '" + tenant.getId() + "'"
              + " because of conflict:"
              + " Interfcace '" + prov.getId() + "' already provided by module '"
              + enabledModule + "'";
            logger.debug(msg);
            return msg;
          }
        }
      }
    }
    return "";
  }

  private String checkDependencies(Tenant tenant, ModuleDescriptor md) {
    ModuleInterface[] requires = md.getRequires();
    if (requires != null) {
      for (ModuleInterface req : requires) {
        String one = checkOneDependency(tenant, md, req);
        if (!one.isEmpty()) {
          return one;
        }
      }
    }
    ModuleInterface[] provides = md.getProvides();
    if (provides != null) {
      for (ModuleInterface prov : provides) {
        if ( ! prov.getId().startsWith("_")) { // skip system interfaces like _tenant
          String one = checkOneConflict(tenant, md, prov);
          if (!one.isEmpty()) {
            return one;
          }
        }
      }
    }
    return "";
  }

  /**
   * Enable a module for a given tenant.
   *
   * @param id
   * @param module
   * @return error message, or "" if all is ok
   */
  public String enableModule(String id, String module) {
    Tenant tenant = tenants.get(id);
    if (tenant == null) {
      return "tenant " + id + " not found";
    }
    ModuleDescriptor mod = moduleManager.get(module);
    if (mod == null) {
      return "module " + module + " not found";
    }
    String deperr = checkDependencies(tenant, mod);
    if (!deperr.isEmpty()) {
      return deperr;
    }
    tenant.enableModule(module);
    return "";
  }

  /**
   * Check that no enabled module depends on any service provided by this
   * module.
   *
   * @param tenant
   * @param module
   * @return true if it is ok to delete the module
   */
  public boolean checkNoDependency(Tenant tenant, String module) {
    ModuleDescriptor mod = moduleManager.get(module);
    if (mod == null) { // should not happen
      logger.warn("Module " + module + " not found when checking delete dependencies!");
      return true;
    }
    logger.debug("Checking that we can delete " + module);
    ModuleInterface[] provides = mod.getProvides();
    if (provides == null) {
      return true; // nothing can depend on this one
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
              logger.debug("checkNoDependency: " + module + " " + prov.getId() + " is used by " + enabledmodule);
              return false;
            }
          }
        }
      }

    }
    return true;
  }

  /**
   * Disable a module for a given tenant.
   *
   * @param id
   * @param module
   * @return "" if ok, or an error message
   */
  public String disableModule(String id, String module) {
    Tenant tenant = tenants.get(id);
    if (tenant == null) {
      return "Tenant " + id + " not found";
    }
    if (!tenant.isEnabled(module)) {
      return "Module " + module + " not found for tenant " + id;
    } else if (!checkNoDependency(tenant, module)) {
      return "Can not delete module " + module + " is in use";
    } else {
      tenant.disableModule(module);
      return "";
    }
  }

  /**
   * List modules for a given tenant.
   *
   * @param id
   * @return null if no such tenant, or a list (possibly empty)
   */
  public Set<String> listModules(String id) {
    Tenant tenant = tenants.get(id);
    if (tenant == null) {
      return null;
    }
    return tenant.listModules();
  }

  /**
   * Get the (first) tenant that uses the given module. Used to check if a
   * module may be deleted.
   *
   * @param mod id of the module in question.
   * @return The id of the (first) tenant that uses the module, or "" if none
   */
  public String getModuleUser(String mod) {

    Set<String> tkeys = tenants.keySet();

    for (String tk : tkeys) {
      Tenant t = tenants.get(tk);
      if (t.isEnabled(mod)) {
        return tk;
      }
    }
    return "";
  }

} // class
