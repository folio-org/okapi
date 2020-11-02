package org.folio.okapi.util;

import io.vertx.core.http.HttpMethod;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.common.OkapiLogger;

public class ModuleCache {
  private static final Logger logger = OkapiLogger.get();

  static class ModuleCacheEntry {
    final ModuleDescriptor moduleDescriptor;
    final RoutingEntry routingEntry;

    public ModuleCacheEntry(ModuleDescriptor moduleDescriptor, RoutingEntry routingEntry) {
      this.moduleDescriptor = moduleDescriptor;
      this.routingEntry = routingEntry;
    }
  }

  final Map<String, List<ModuleCacheEntry>> proxyMap = new HashMap<>();
  final Map<String, List<ModuleCacheEntry>> multiMap = new HashMap<>();
  final Map<String, List<ModuleCacheEntry>> filterMap = new HashMap<>();
  final List<ModuleDescriptor> moduleDescriptors;

  /**
   * Construct cache with module descriptors.
   * @param moduleDescriptors to be cached
   */
  public ModuleCache(List<ModuleDescriptor> moduleDescriptors) {
    this.moduleDescriptors = moduleDescriptors;
    for (ModuleDescriptor moduleDescriptor : moduleDescriptors) {
      add(moduleDescriptor);
    }
  }

  /**
   * Return modules descriptors in cache.
   * @return list of modules
   */
  public List<ModuleDescriptor> getModules() {
    return moduleDescriptors;
  }

  static String getPatternPrefix(RoutingEntry re) {
    String pathPattern = re.getPathPattern();
    if (pathPattern == null) {
      return "/"; // anything but pathPattern is legacy so we don't care about those
    }
    int index = 0;
    while (index < pathPattern.length()) {
      if (pathPattern.charAt(index) == '*' || pathPattern.charAt(index) == '{') {
        while (index > 0 && pathPattern.charAt(index - 1) != '/') {
          --index;
        }
        break;
      }
      index++;
    }
    return pathPattern.substring(0, index);
  }

  static void add(ModuleDescriptor moduleDescriptor, Map<String, List<ModuleCacheEntry>> map,
                  List<RoutingEntry> entries) {
    for (RoutingEntry routingEntry : entries) {
      String prefix = getPatternPrefix(routingEntry);
      List<ModuleCacheEntry> list = map.get(prefix);
      if (list == null) {
        list = new LinkedList<>();
        map.put(prefix, list);
      }
      list.add(new ModuleCacheEntry(moduleDescriptor, routingEntry));
    }
  }

  private void add(ModuleDescriptor moduleDescriptor) {
    add(moduleDescriptor, proxyMap, moduleDescriptor.getProxyRoutingEntries());
    add(moduleDescriptor, multiMap, moduleDescriptor.getMultiRoutingEntries());
    add(moduleDescriptor, filterMap, moduleDescriptor.getFilterRoutingEntries());
  }

  static List<ModuleInstance> lookup(String uri, HttpMethod method, Map<String,
      List<ModuleCacheEntry>> map, boolean handler, String id) {
    List<ModuleInstance> instances = new LinkedList<>();
    String tryUri = uri;
    for (int index = 0; index < uri.length(); index++) {
      if (uri.charAt(index) == '#' || uri.charAt(index) == '?') {
        tryUri = tryUri.substring(0, index);
        break;
      }
    }
    while (true) {
      List<ModuleCacheEntry> candidateInstances = map.get(tryUri);
      if (candidateInstances != null) {
        for (ModuleCacheEntry candiate : candidateInstances) {
          if (candiate.routingEntry.match(uri, method.name())
              && (id == null || id.equals(candiate.moduleDescriptor.getId()))) {
            instances.add(new ModuleInstance(candiate.moduleDescriptor,
                candiate.routingEntry, uri, method, handler));
            if (handler) {
              return instances;
            }
          }
        }
      }
      int index = tryUri.length() - 1;
      while (index > 0 && tryUri.charAt(index - 1) != '/') {
        --index;
      }
      if (index <= 0) {
        break;
      }
      tryUri = tryUri.substring(0, index);
    }
    return instances;
  }

  /**
   * Find module instances for uri(path) and method.
   * @param uri request uri
   * @param method HTTP method
   * @param id Proxy-ID for multi lookup; otherwise null
   * @return module instances that match
   */
  public List<ModuleInstance> lookup(String uri, HttpMethod method, String id) {
    logger.debug("lookup {} {} id={}", method.name(), uri, id);
    StringBuilder str = new StringBuilder();
    for (ModuleDescriptor md : moduleDescriptors) {
      if (str.length() > 0) {
        str.append(", ");
      }
      str.append(md.getId());
    }
    logger.debug("Available modules {}", str);
    // perform lookup
    List<ModuleInstance> instances = ModuleCache.lookup(uri, method, filterMap, false, null);
    if (id == null) {
      instances.addAll(lookup(uri, method, proxyMap, true, null));
    } else {
      instances.addAll(lookup(uri, method, multiMap, true, id));
    }
    return instances;
  }
}

