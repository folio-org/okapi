package org.folio.okapi.bean;

/**
 * List of Permissions (and permission sets) belonging to a module. Used as a
 * parameter in the system request to initialize the permission module when a
 * module is being enabled for a tenant.
 */
public class PermissionChanges {
  private String moduleId; // The module that owns these permissions.
  private Permission[] newPermissions;
  private Permission[] modifiedPermissions;
  private Permission[] removedPermissions;

  /**
   * Constructor taking all fields.
   * @param moduleId module identifier
   * @param newPerms new permissions
   * @param modifiedPerms modified permissions
   * @param removedPerms removed permissions
   */
  public PermissionChanges(String moduleId, Permission[] newPerms, Permission[] modifiedPerms,
      Permission[] removedPerms) {
    this.moduleId = moduleId;
    this.newPermissions = newPerms;
    this.modifiedPermissions = modifiedPerms;
    this.removedPermissions = removedPerms;
  }

  public String getModuleId() {
    return moduleId;
  }

  public void setModuleId(String moduleId) {
    this.moduleId = moduleId;
  }

  public Permission[] getNewPermissions() {
    return newPermissions;
  }

  public void setNewPermissions(Permission[] newPermissions) {
    this.newPermissions = newPermissions;
  }

  public Permission[] getModifiedPermissions() {
    return modifiedPermissions;
  }

  public void setModifiedPermissions(Permission[] modifiedPermissions) {
    this.modifiedPermissions = modifiedPermissions;
  }

  public Permission[] getRemovedPermissions() {
    return removedPermissions;
  }

  public void setRemovedPermissions(Permission[] removedPermissions) {
    this.removedPermissions = removedPermissions;
  }

}
