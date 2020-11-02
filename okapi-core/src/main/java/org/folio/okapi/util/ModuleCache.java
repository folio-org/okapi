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

  Map<String, List<ModuleCacheEntry>> proxyMap = new HashMap<>();
  Map<String, List<ModuleCacheEntry>> multiMap = new HashMap<>();
  Map<String, List<ModuleCacheEntry>> filterMap = new HashMap<>();

  static String getPatternPrefix(RoutingEntry re) {
    String pathPattern = re.getPathPattern();
    if (pathPattern == null) {
      return "/";
    }
    int index = 0;
    while (index < pathPattern.length()) {
      if (pathPattern.charAt(index) == '*' || pathPattern.charAt(index) == '{') {
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

  /**
   * Add module to ModuleCache.
   * @param moduleDescriptor module descriptor
   */
  public void add(ModuleDescriptor moduleDescriptor) {
    add(moduleDescriptor, proxyMap, moduleDescriptor.getProxyRoutingEntries());
    add(moduleDescriptor, multiMap, moduleDescriptor.getMultiRoutingEntries());
    add(moduleDescriptor, filterMap, moduleDescriptor.getFilterRoutingEntries());
  }

  /**
   * clear module cache.
   */
  public void clear() {
    proxyMap.clear();
    multiMap.clear();
    filterMap.clear();
  }

  static List<ModuleInstance> lookup(String uri, HttpMethod method, Map<String,
      List<ModuleCacheEntry>> map, boolean handler, String id) {
    List<ModuleInstance> returnList = new LinkedList<>();
    logger.info("lookup uri={}", uri);
    String tryUri = uri;
    while (true) {
      logger.info("tryUri = {}", tryUri);
      List<ModuleCacheEntry> gotInstances = map.get(tryUri);
      if (gotInstances != null) {
        for (ModuleCacheEntry candiate : gotInstances) {
          if (candiate.routingEntry.match(uri, method.name())
              && (id == null || id.equals(candiate.moduleDescriptor.getId()))) {
            returnList.add(new ModuleInstance(candiate.moduleDescriptor,
                candiate.routingEntry, uri, method, handler));
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
    return returnList;
  }

  /**
   * Find module instances for uri(path) and method.
   * @param uri request uri
   * @param method HTTP method
   * @param id Proxy-ID for multi lookup; otherwise null
   * @return module instances that match
   */
  public List<ModuleInstance> lookup(String uri, HttpMethod method, String id) {
    // perform lookup
    List<ModuleInstance> instances = ModuleCache.lookup(uri, method, filterMap, false, null);
    if (id == null) {
      instances.addAll(lookup(uri, method, proxyMap, true, null));
    } else {
      instances.addAll(lookup(uri, method, multiMap, false, id));
    }
    return instances;
  }
}

