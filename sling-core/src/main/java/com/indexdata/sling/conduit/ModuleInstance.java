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

  public ModuleInstance(ModuleDescriptor md, ProcessModuleHandle pmh, int port) {
    this.md = md;
    this.pmh = pmh;
    this.port = port;
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
}
