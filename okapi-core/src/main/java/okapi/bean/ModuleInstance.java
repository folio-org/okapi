/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

public class ModuleInstance {

  ModuleDescriptor md;
  String url;
  final RoutingEntry re;

  public ModuleInstance(ModuleDescriptor md, RoutingEntry re) {
    this.md = md;
    this.url = null;
    this.re = re;
  }

  public ModuleDescriptor getModuleDescriptor() {
    return md;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public RoutingEntry getRoutingEntry() {
    return re;
  }
}
