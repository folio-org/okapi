/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */

package okapi.service;

import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import okapi.bean.Tenant;
import okapi.bean.TenantModuleDescriptor;
import okapi.util.ErrorType;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

/**
 * Manages the tenants in the run-time system.
 * These will be modified by the web service, and (more often) reloaded from
 * the storage. Note that these are all in-memory operations, so there is no
 * need to use vert.x callbacks for this.
 *
 * TODO
 * - Make TenantService us this instead of its own enabled.
 * - Rename TenantService to tenantWebService
 * - Add storage stuff to the tenantWebService
 * - Add tenant reloading
 * - Pass a ModuleManager, and validate the modules we try to enable etc. Or do that in the web service?
 */

public class TenantManager {
  Map<String, Tenant> tenants = new HashMap<>();

  public void put(String id, Tenant t) {
    tenants.put(id, t);
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
   * @param id
   * @return true on success, false if not there.
   */
  public boolean delete(String id) {
    if (!tenants.containsKey(id)) {
      return false;
    }
    tenants.remove(id);
    return true;
  }

  /**
   * Enable a module for a given tenant.
   *
   * @param id
   * @param module
   * @param fut - callback with a success, or some type of error
   * @return
   */
  public ErrorType enableModule(String id, String module ) {
    Tenant tenant = tenants.get(id);
    if (tenant == null) {
      return NOT_FOUND;
    }
    // TODO - Check if we know about the module. 
    tenant.enableModule(module);
    return OK;
  }

  /**
   * List modules for a given tenant.
   * @param id
   * @return null if no such tenant, or a list (possibly empty)
   */
  public Set<String> listModules(String id) {
    Tenant tenant = tenants.get(id);
    if (tenant == null)
      return null;
    return tenant.listModules();
  }

} // class
