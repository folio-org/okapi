package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * List of Permissions (and permission sets) belonging to a module. Used as a
 * parameter in the system request to initialize the permission module when a
 * module is being enabled for a tenant.
 */
public class PermissionList {
  private final String moduleToId; // The module that owns these permissions.
  private final Permission[] permsTo;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String moduleFromId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final Permission[] permsFrom;

  public PermissionList(String moduleToId, Permission[] permsTo) {
    this(moduleToId, null, permsTo, null);
  }

  /**
   * Constructor taking all four args.
   *
   * @param moduleToId the module/version we're moving to
   * @param moduleFromId the current module/version
   * @param permsTo the permissions we're moving to
   * @param permsFrom the current permissions
   */
  public PermissionList(String moduleToId, String moduleFromId, Permission[] permsTo,
      Permission[] permsFrom) {
    this.moduleToId = moduleToId;
    this.moduleFromId = moduleFromId;
    this.permsTo = permsTo;
    this.permsFrom = permsFrom;
  }

  public String getModuleToId() {
    return moduleToId;
  }

  public Permission[] getPermsTo() {
    return permsTo;
  }

  public String getModuleFromId() {
    return moduleFromId;
  }

  public Permission[] getPermsFrom() {
    return permsFrom;
  }

}
