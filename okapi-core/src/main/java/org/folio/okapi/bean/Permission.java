package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Permission {
  private String permissionName;
  private String displayName;
  private String description;
  private String[] subPermissions;
  private Boolean visible;

  public Permission() {
  }

  public Permission(Permission other) {
    this.permissionName = other.permissionName;
    this.displayName = other.displayName;
    this.description = other.description;
    this.subPermissions = other.subPermissions;
    this.visible = other.visible;
  }

  public String getPermissionName() {
    return permissionName;
  }

  public void setPermissionName(String permissionName) {
    this.permissionName = permissionName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String[] getSubPermissions() {
    return subPermissions;
  }

  public void setSubPermissions(String[] subPermissions) {
    this.subPermissions = subPermissions;
  }

  public Boolean getVisible() {
    return visible;
  }

  public void setVisible(Boolean visible) {
    this.visible = visible;
  }

}
