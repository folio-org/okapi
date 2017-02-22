package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * One entry in Okapi's routing table.
 * Each entry contains one or more HTTP methods, and the path they mean,
 * for example "GET /foo". Incoming requests are mapped to a series of
 * routingEntries, ordered by their level. Also carries the permission bits
 * required and desired for this operation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoutingEntry {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private String[] methods;
  private String path;
  private String level;
  private String type;
  private String redirectPath; // only for type='redirect'

  private String[] permissionsRequired;
  private String[] permissionsDesired;
  private String[] modulePermissions;

  public String[] getPermissionsRequired() {
    return permissionsRequired;
  }

  public void setPermissionsRequired(String[] permissionsRequired) {
    this.permissionsRequired = permissionsRequired;
  }

  public String[] getPermissionsDesired() {
    return permissionsDesired;
  }

  public void setPermissionsDesired(String[] permissionsDesired) {
    this.permissionsDesired = permissionsDesired;
  }

  public String[] getModulePermissions() {
    return modulePermissions;
  }

  public void setModulePermissions(String[] modulePermissions) {
    this.modulePermissions = modulePermissions;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getRedirectPath() {
    return redirectPath;
  }

  public void setRedirectPath(String redirectPath) {
    this.redirectPath = redirectPath;
  }

  public String getLevel() {
    return level;
  }

  public void setLevel(String level) {
    this.level = level;
  }

  public String[] getMethods() {
    return methods;
  }

  public String getPath() {
    return path;
  }

  public void setMethods(String[] methods) {
    this.methods = methods;
  }

  public void setPath(String path) {
    this.path = path;
  }

  // TODO - Fix this after refactoring the ModuleDescriptor
  // For now, it is a dirty hack to make sure Okapi can return a 404
  // in case only auth module gets invoked
  public boolean actuallyIsFilter() {
    if ("/".equals(this.path)) {
      return true; // A path of "/" is a filter by default
    } else {
      return false; // everythign else is not
    }
  }
}
