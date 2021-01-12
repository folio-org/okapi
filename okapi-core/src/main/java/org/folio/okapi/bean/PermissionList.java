package org.folio.okapi.bean;

/**
 * List of Permissions (and permission sets) belonging to a module. Used as a
 * parameter in the system request to initialize the permission module when a
 * module is being enabled for a tenant.
 */
public class PermissionList {
  private final String moduleId; // The module that owns these permissions.
  private final Permission[] perms;

  public PermissionList(String moduleId, Permission[] perms) {
    this.moduleId = moduleId;
    this.perms = perms;
  }

  public String getModuleId() {
    return moduleId;
  }

  public Permission[] getPerms() {
    return perms;
  }

}
