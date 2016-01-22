/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

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
    if (id == null)
      return name;
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public TenantDescriptor() {}

  public TenantDescriptor(String id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
  }

}
