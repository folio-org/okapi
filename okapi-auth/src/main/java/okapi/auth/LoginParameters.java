/*
 * Copyright (C) 2015 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parameters for the login service. These are in a class of their own, so we
 * can use Json to pack and unpack them as needed.
 *
 * @author heikki
 */
public class LoginParameters {

  private final String tenant;
  private final String username;
  private final String password;

  @JsonCreator
  public LoginParameters(
          @JsonProperty("tenant") String tenant,
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
