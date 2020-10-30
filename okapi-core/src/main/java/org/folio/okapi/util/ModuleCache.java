package org.folio.okapi.util;

import io.vertx.core.http.HttpMethod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;

public class ModuleCache {
  Map<String, List<ModuleInstance>> proxyMap = new HashMap<>();
  Map<String, List<ModuleInstance>> multiMap = new HashMap<>();
  Map<String, List<ModuleInstance>> filterMap = new HashMap<>();
  Set<String> modules = new HashSet<>();

  String getPatternPrefix(RoutingEntry re) {
    String pathPattern = re.getPathPattern();
    if (pathPattern == null) {
      return "/";
    }
    int i = 0;
    while (i < pathPattern.length()) {
      if (pathPattern.charAt(i) == '*' || pathPattern.charAt(i) == '{') {
        break;
      }
      i++;
    }
    return pathPattern.substring(0, i);
  }

  void add(ModuleDescriptor moduleDescriptor, Map<String, List<ModuleInstance>> map,
           List<RoutingEntry> entries) {
    for (RoutingEntry routingEntry : entries) {
      String prefix = getPatternPrefix(routingEntry);
      List<ModuleInstance> list = map.get(prefix);
      if (list == null) {
        list = new LinkedList<>();
        map.put(prefix, list);
      }
      list.add(new ModuleInstance(moduleDescriptor, routingEntry, null, HttpMethod.GET, false));
    }
  }

  void add(ModuleDescriptor moduleDescriptor) {
    add(moduleDescriptor, proxyMap, moduleDescriptor.getProxyRoutingEntries());
    add(moduleDescriptor, multiMap, moduleDescriptor.getMultiRoutingEntries());
    add(moduleDescriptor, filterMap, moduleDescriptor.getFilterRoutingEntries());
  }

  List<ModuleInstance> lookup(String uri, HttpMethod method, Map<String, List<ModuleInstance>> map,
                              boolean handler) {
    List<ModuleInstance> returnList = new LinkedList<>();
    String tryUri = uri;
    while (true) {
      List<ModuleInstance> gotInstances = map.get(tryUri);
      if (gotInstances != null) {
        for (ModuleInstance candiate : gotInstances) {
          if (candiate.getRoutingEntry().match(uri, method.name())) {
            returnList.add(new ModuleInstance(candiate.getModuleDescriptor(),
                candiate.getRoutingEntry(), uri, method, handler));
          }
        }
      }
      int index = tryUri.lastIndexOf('/');
      if (index == -1) {
        break;
      }
      tryUri = tryUri.substring(0, index + 1);
    }
    return returnList;
  }

  /**
   * Find module instances for uri(path) and method.
   * @param uri request uri
   * @param method HTTP method
   * @param id Proxy-ID for multi lookup; otherwise null
   * @param enabledModules enabled modules for tenant
   * @return module instances that match
   */
  public List<ModuleInstance> lookup(String uri, HttpMethod method, String id,
                              List<ModuleDescriptor> enabledModules) {
    // add modules to cache we don't know about
    for (ModuleDescriptor md : enabledModules) {
      if (modules.add(md.getId())) {
        add(md);
      }
    }
    // perform lookup
    List<ModuleInstance> instances = lookup(uri, method, filterMap, false);
    if (id == null) {
      instances.addAll(lookup(uri, method, proxyMap, true));
    } else {
      instances.addAll(lookup(uri, method, multiMap, false));
    }
    // remove from the result those that are not enabled for tenant=enabledModules
    Iterator<ModuleInstance> iterator = instances.iterator();
    while (iterator.hasNext()) {
      ModuleInstance next = iterator.next();
      boolean found = false;
      for (ModuleDescriptor md : enabledModules) {
        if (next.getModuleDescriptor().getId().equals(md.getId())) {
          found = true;
        }
      }
      if (!found) {
        iterator.remove();
      }
    }
    return instances;
  }
}

