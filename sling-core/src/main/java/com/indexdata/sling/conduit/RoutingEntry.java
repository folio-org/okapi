/*
 * Copyright (c) 1995-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

/**
 *
 * @author jakub
 */
public class RoutingEntry {
  private String[] methods;
  private String pathPrefix;

  public String[] getMethods() {
    return methods;
  }

  public String getPathPrefix() {
    return pathPrefix;
  }

  public void setMethods(String[] methods) {
    this.methods = methods;
  }

  public void setPathPrefix(String pathPrefix) {
    this.pathPrefix = pathPrefix;
  }
  
}
