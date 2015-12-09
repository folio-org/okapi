/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 *
 * @author adam
 */
public class Modules {
  LinkedHashMap<String, ModuleInstance> enabled = new LinkedHashMap<>();
  
  public ModuleInstance get(String name) {
    return enabled.getOrDefault(name, null);
  }
  
  public void put(String name, ModuleInstance m) {
    enabled.put(name, m);
  }
  
  public void remove(String name) {
    enabled.remove(name);
  }
  
  public Iterator<String> iterator() {
    return enabled.keySet().iterator();
  }
}
