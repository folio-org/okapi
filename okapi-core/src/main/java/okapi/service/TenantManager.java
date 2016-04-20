/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import okapi.bean.ModuleDescriptor;
import okapi.bean.ModuleInstance;
import okapi.bean.ModuleInterface;
import okapi.bean.Modules;
import okapi.bean.Tenant;
import okapi.bean.TenantDescriptor;
import okapi.util.ErrorType;
import static okapi.util.ErrorType.*;
import okapi.util.Failure;

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
    this.moduleManager = moduleManager;
  }

  public boolean insert(Tenant t) {
    String id = t.getId();
    if (tenants.containsKey(id)) {
      logger.debug("Not inserting duplicate '" + id + "':" + Json.encode(t));
      return false;
    }
    tenants.put(id, t);
    return true;
  }

  public boolean update(Tenant t) {
    String id = t.getId();
    if (!tenants.containsKey(id)) {
      logger.debug("Tenant '" + id + "' not found, can not update");
      return false;
    }
    tenants.put(id, t);
    return true;
  }

  public boolean updateDescriptor(String id, TenantDescriptor td, long ts) {
    if (!tenants.containsKey(id)) {
      logger.debug("Tenant '" + id + "' not found, can not update descriptor");
      return false;
    }
    Tenant t = tenants.get(id);
    Tenant nt = new Tenant(td, t.getEnabled());
    nt.setTimestamp(ts);
    tenants.put(id, nt);
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
    tenants.remove(id);
    return true;
  }

  private String checkOneDependency(Tenant tenant, Modules modules, ModuleDescriptor md, ModuleInterface req) {
    ModuleInterface seenversion = null;
    for ( String enabledmodule : tenant.listModules()) {
      ModuleInstance rm = modules.get(enabledmodule);
      ModuleInterface[] provides = rm.getModuleDescriptor().getProvides();
      if ( provides != null ) {
        for ( ModuleInterface pi: provides ) {
          logger.debug("Checking dependency of " + md.getId() + ": "
              + req.getId() + " " + req.getVersion()
              + " against " + pi.getId() + " " + pi.getVersion() );
          if ( req.getId().equals(pi.getId())) {
            if ( seenversion == null || pi.compare(req) > 0)
              seenversion = pi;
            if ( pi.isCompatible(req))
              return "";
          }
        }
      }
    }
    String msg;
    if (  seenversion == null ) {
      msg = "Can not enable module '" + md.getId() + "'"
        +", missing dependency " + req.getId() + ": " + req.getVersion();
    } else {
      msg = "Can not enable module '" + md.getId() + "'"
        + "Insufficient version for " + req.getId() + ". "
        + "Need " + req.getVersion() + ". have " + seenversion.getVersion();
    }
    logger.debug(msg);
    return msg;

  }

  private String checkDependencies(Tenant tenant, Modules modules, ModuleDescriptor md) {
    ModuleInterface[] requires = md.getRequires();
    if (requires != null) {
      for (ModuleInterface req : requires) {
        String one =  checkOneDependency(tenant, modules, md, req);
        if ( !one.isEmpty())
          return one;
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
    Modules modules = moduleManager.getModules();
    ModuleInstance mod = modules.get(module);
    if (mod == null)
      return "module " + module + " not found";
    String deperr = checkDependencies(tenant, modules, mod.getModuleDescriptor());
    if ( ! deperr.isEmpty())
      return deperr;
    tenant.enableModule(module);
    return "";
  }

  /**
   * Disable a module for a given tenant.
   *
   * @param id
   * @param module
   * @return
   */
  public ErrorType disableModule(String id, String module) {
    Tenant tenant = tenants.get(id);
    if (tenant == null) {
      return USER; // Indicates tenant not found
    }
    if (tenant.isEnabled(module)) {
      tenant.disableModule(module);
      return OK;
    } else {
      return NOT_FOUND; // tenant ok, but no such module
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


} // class
