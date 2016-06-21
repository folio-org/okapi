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
package okapi.bean;

import java.util.Set;
import java.util.TreeSet;

public class RoutingEntry {

  private String[] methods;
  private String path;
  private String level;
  private String type;
  private String[] requiredPermissions;

  public String[] getRequiredPermissions() {
    return requiredPermissions;
  }

  public void setRequiredPermissions(String[] requiredPermissions) {
    this.requiredPermissions = requiredPermissions;
  }

  public String[] getWantedPermissions() {
    return wantedPermissions;
  }

  public void setWantedPermissions(String[] wantedPermissions) {
    this.wantedPermissions = wantedPermissions;
  }
  private String[] wantedPermissions;


  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
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

}
