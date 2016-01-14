/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.okapi.conduit;

public class RoutingEntry {
  private String[] methods;
  private String path;
  private String level;
  private String type;

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
