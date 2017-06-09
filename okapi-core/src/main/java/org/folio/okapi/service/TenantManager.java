package org.folio.okapi.service;

import com.codahale.metrics.Timer;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInterface;
import org.folio.okapi.bean.RoutingEntry;
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

  /**
   * Get the moduleManager.
   */
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
              seenversion = pi;
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
            final String t = pi.getInterfaceType();
            if (t == null || "proxy".equals(t)) {
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

  public String updateModuleDepCheck(Tenant tenant, String module_from, String module_to) {
    ModuleDescriptor mod_to = moduleManager.get(module_to);
    if (mod_to == null) {
      return "module " + module_to + " not found";
    }
    ModuleDescriptor mod_from = null;
    if (module_from != null) {
      mod_from = moduleManager.get(module_from);
    }
    return checkDependencies(tenant, mod_from, mod_to);
  }

  public String updateModuleCommit(String id, String module_from, String module_to) {
    Tenant tenant = tenants.get(id);
    if (tenant == null) {
      return "tenant " + id + " not found";
    }
    if (module_from != null) {
      tenant.disableModule(module_from);
    }
    tenant.enableModule(module_to);
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
  public ModuleDescriptor findSystemInterface(String tenantId, String interfaceName) {
    Tenant tenant = tenants.get(tenantId);
    if (tenant == null) {
      logger.warn("findSystemInterface " + interfaceName + " for tenant "
        + tenantId + ": Tenant not found");
      return null; // Should not happen
    }
    Set<String> modlist = this.moduleManager.list();
    for (String m : modlist) {
      ModuleDescriptor md = this.moduleManager.get(m);
      if (md.getSystemInterface(interfaceName) != null
        && tenant.isEnabled(m)) {
        return md;
      }
    }
    return null;
  }

  public List<ModuleDescriptor> listModulesFromInterface(String tenantId, String interfaceName) {
    Tenant tenant = tenants.get(tenantId);
    if (tenant == null) {
      return null;
    }
    ArrayList<ModuleDescriptor> mdList = new ArrayList<>();
    Set<String> modlist = this.moduleManager.list();
    for (String m : modlist) {
      ModuleDescriptor md = this.moduleManager.get(m);
      if (tenant.isEnabled(m)) {
        for (ModuleInterface provide : md.getProvides()) {
          if (interfaceName.equals(provide.getId())) {
            mdList.add(md);
            break;
          }
        }
      }
    }
    return mdList;
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
