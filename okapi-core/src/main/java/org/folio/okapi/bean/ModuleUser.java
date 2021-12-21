package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tells how a module is to be deployed. Either by exec'ing a command (and
 * killing the process afterwards), or by using command lines to start and stop
 * it.
 */
@JsonInclude(Include.NON_NULL)
public class ModuleUser {

  private String type;
  private String[] permissions;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String[] getPermissions() {
    return permissions;
  }

  public void setPermissions(String[] permissions) {
    this.permissions = permissions;
  }
}
