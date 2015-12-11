/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

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
  
  public Iterator<ModuleInstance> sortedIterator() {
    List<ModuleInstance> r = new ArrayList<>();
    for (String s : enabled.keySet()) {
      RoutingEntry[] rr = enabled.get(s).getModuleDescriptor().getRoutingEntries();
      for (int i = 0; i < rr.length; i++)
      {
        ModuleInstance mi = new ModuleInstance(enabled.get(s), rr[i]);
        r.add(mi);
      }
    }
    Comparator<ModuleInstance> cmp = new Comparator<ModuleInstance>() {
      public int compare(ModuleInstance a, ModuleInstance b) {
        return a.getRoutingEntry().getLevel().compareTo(b.getRoutingEntry().getLevel());
      }
    };
    r.sort(cmp);
    System.out.println("Modules");
    for (ModuleInstance mi : r) {
      System.out.println("mi: " + mi.getModuleDescriptor().getName() + " " + mi.getRoutingEntry().getLevel());
    }
    return r.iterator();
  }
}
