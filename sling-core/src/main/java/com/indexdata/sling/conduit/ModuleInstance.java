/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

public class ModuleInstance {

  ModuleDescriptor md;
  ProcessModuleHandle pmh;
  int port;
  final RoutingEntry re;

  public ModuleInstance(ModuleDescriptor md, ProcessModuleHandle pmh, int port) {
    this.md = md;
    this.pmh = pmh;
    this.port = port;
    this.re = null;
  }

  public ModuleInstance(ModuleInstance mi, RoutingEntry re) {
    this.md = mi.md;
    this.pmh = mi.pmh;
    this.port = mi.port;
    this.re = re;
  }

  public ModuleDescriptor getModuleDescriptor() {
    return md;
  }

  public ProcessModuleHandle getProcessModuleHandle() {
    return pmh;
  }
  
  public int getPort() {
    return port;
  }

  public RoutingEntry getRoutingEntry() {
    return re;
  }
}
