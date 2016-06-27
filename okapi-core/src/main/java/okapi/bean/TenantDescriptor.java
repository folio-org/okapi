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
 * Description of a Tenant.
 * This is what gets POSTed to /_/proxy/tenants to create new tenants, etc.
 * Carries an id, and some human-readable info about the tenant.
 *
 */
public class TenantDescriptor {

  private String id;
  private String name;
  private String description;

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public TenantDescriptor() {
  }

  public TenantDescriptor(String id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
  }

  /**
   * Copy constructor. Makes a separate copy of everything
   *
   * @param other
   */
  public TenantDescriptor(TenantDescriptor other) {
    this.id = other.id;
    this.name = other.name;
    this.description = other.description;
  }

}
