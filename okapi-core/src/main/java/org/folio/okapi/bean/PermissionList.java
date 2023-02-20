package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * List of Permissions (and permission sets) belonging to a module. Used as a
 * parameter in the system request to initialize the permission module when a
 * module is being enabled for a tenant.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionList {
  private final String moduleId; // The module that owns these permissions.
  private final Permission[] perms;
  private final String[] replaces;

  /**
   * Create permissions object representing body of _tenantPermissions service.
   * @param moduleId module for which we pass permissions
   * @param perms the permissions defined by the module
   * @param replaces the modules that this one replaces (if any)
   */
  public PermissionList(String moduleId, Permission[] perms, String[] replaces) {
    this.moduleId = moduleId;
    this.perms = perms;
    this.replaces = replaces;
  }

  public PermissionList(String moduleId, Permission[] perms) {
    this(moduleId, perms, null);
  }

  public String getModuleId() {
    return moduleId;
  }

  public Permission[] getPerms() {
    return perms;
  }

  public String[] getReplaces() {
    return replaces;
  }
}
