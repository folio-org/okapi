/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

import io.vertx.core.http.HttpServerRequest;
import java.util.ArrayList;
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

  public Set<String> list() {
     return enabled.keySet();
  }
  
  public void put(String name, ModuleInstance m) {
    enabled.put(name, m);
  }
  
  public void remove(String name) {
    enabled.remove(name);
  }
  
  private boolean match(RoutingEntry e, HttpServerRequest req) {
    if (req.uri().startsWith(e.getPath())) {
      String[] methods = e.getMethods();
      for (int j = 0; j < methods.length; j++) {
        if (methods[j].equals("*") || methods[j].equals(req.method().name())) {
          return true;
        }
      }
    }
    return false;
  }
   
  public Iterator<ModuleInstance> getModulesForRequest(HttpServerRequest hreq, Tenant t) {
    List<ModuleInstance> r = new ArrayList<>();
    for (String s : enabled.keySet()) {
      if (t.isEnabled(s)) {
        RoutingEntry[] rr = enabled.get(s).getModuleDescriptor().getRoutingEntries();
        for (int i = 0; i < rr.length; i++) {
          if (match(rr[i], hreq)) {
            ModuleInstance mi = new ModuleInstance(enabled.get(s), rr[i]);
            r.add(mi);
          }
        }
      }
    }
    Comparator<ModuleInstance> cmp = new Comparator<ModuleInstance>() {
      public int compare(ModuleInstance a, ModuleInstance b) {
        return a.getRoutingEntry().getLevel().compareTo(b.getRoutingEntry().getLevel());
      }
    };
    r.sort(cmp);
    return r.iterator();
  }
}
