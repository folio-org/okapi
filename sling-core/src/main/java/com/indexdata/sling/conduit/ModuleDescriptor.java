/*
 * Copyright (c) 1995-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

import java.util.List;

public class ModuleDescriptor {
  
  private String name;
  private ProcessDeploymentDescriptor descriptor;
  private RoutingEntry [] routingEntries;

  public RoutingEntry[] getRoutingEntries() {
    return routingEntries;
  }

  public void setRoutingEntries(RoutingEntry[] routingEntries) {
    this.routingEntries = routingEntries;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ProcessDeploymentDescriptor getDescriptor() {
    return descriptor;
  }

  public void setDescriptor(ProcessDeploymentDescriptor descriptor) {
    this.descriptor = descriptor;
  }
  
}
