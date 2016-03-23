/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

import java.util.LinkedHashMap;
import java.util.Set;

public class Modules {

  LinkedHashMap<String, ModuleInstance> enabled = new LinkedHashMap<>();

  public ModuleInstance get(String name) {
    return enabled.getOrDefault(name, null);
  }

  public Set<String> list() {
    return enabled.keySet();
  }

  public void put(String name, ModuleInstance m) {
    enabled.put(name, m);
  }

  public void remove(String name) {
    enabled.remove(name);
  }
}
