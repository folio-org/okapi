package org.folio.okapi.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parameters for the login service. These are in a class of their own, so we
 * can use JSON to pack and unpack them as needed.
 */
class LoginParameters {

  private final String tenant;
  private final String username;
  private final String password;
  private final String [] permissions;

  @JsonCreator
  public LoginParameters(
          @JsonProperty("tenant") String tenant,
          @JsonProperty("username") String username,
          @JsonProperty("password") String password,
          @JsonProperty("permissions") String [] permissions) {
    this.tenant = tenant;
    this.username = username;
    this.password = password;
    this.permissions = permissions;
  }

  public String getTenant() {
    return tenant;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String [] getPermissions() {
    return permissions;
  }

}
