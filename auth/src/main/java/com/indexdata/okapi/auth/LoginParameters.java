/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.okapi.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parameters for the login service. These are in a class of their own,
 * so we can use Json to pack and unpack them as needed.
 * @author heikki
 */
public class LoginParameters {
  private final String tenant;
  private final String username;
  private final String password;

  
  @JsonCreator
  public LoginParameters(
      @JsonProperty("tenant")   String tenant, 
      @JsonProperty("username") String username, 
      @JsonProperty("password") String password) {
    this.tenant = tenant;
    this.username = username;
    this.password = password;
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
  
  
  
}
