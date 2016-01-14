/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

public class TenantDescriptor {
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

  public TenantDescriptor() {}

  public TenantDescriptor(String name, String description) {
    this.name = name;
    this.description = description;
  }
}
