package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Permission {
  private String permissionName;
  private String[] replaces;
  private String displayName;
  private String description;
  private String[] subPermissions;
  private Boolean visible;

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

  public String[] getReplaces() {
    return replaces;
  }

  public void setReplaces(String[] replaces) {
    this.replaces = replaces;
  }
}
