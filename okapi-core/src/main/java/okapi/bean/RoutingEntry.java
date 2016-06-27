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

/**
 * One entry in Okapi's routing table.
 * Each entry contains one or more HTTP methods, and the path they mean,
 * for example GET /foo. Incoming requests are mapped to a series of
 * routingEntries, ordered by their level. Also carries the permission bits
 * required and desired for this operation.
 */
public class RoutingEntry {

  private String[] methods;
  private String path;
  private String level;
  private String type;
  private String[] permissionsRequired;
  private String[] permissionsDesired;

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
