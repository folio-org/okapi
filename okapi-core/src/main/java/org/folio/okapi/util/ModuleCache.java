package org.folio.okapi.util;

import io.vertx.core.http.HttpMethod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;

public class ModuleCache {
  private static final Logger logger = OkapiLogger.get();
  private static final Messages messages = Messages.getInstance();

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
    int lastSlash = 0;
    for (int i = 0; i < pathPattern.length(); i++) {
      switch (pathPattern.charAt(i)) {
        case '*':
        case '{':
          return pathPattern.substring(0, lastSlash);
        case '/':
          lastSlash = i + 1;
          break;
        default:
          break;
      }
    }
    return pathPattern;
  }

  static void add(ModuleDescriptor moduleDescriptor, Map<String, List<ModuleCacheEntry>> map,
                  List<RoutingEntry> entries) {
    for (RoutingEntry routingEntry : entries) {
      String prefix = getPatternPrefix(routingEntry);
      List<ModuleCacheEntry> list = map.computeIfAbsent(prefix, k -> new LinkedList<>());
      list.add(new ModuleCacheEntry(moduleDescriptor, routingEntry));
    }
  }

  private void add(ModuleDescriptor moduleDescriptor) {
    add(moduleDescriptor, proxyMap, moduleDescriptor.getProxyRoutingEntries());
    add(moduleDescriptor, multiMap, moduleDescriptor.getMultiRoutingEntries());
    add(moduleDescriptor, filterMap, moduleDescriptor.getFilterRoutingEntries());
  }

  private void resolveRedirect(List<ModuleInstance> instances, RoutingEntry re, String loop,
                               Set<RoutingEntry> routingEntries,
                               HttpMethod method, String uri) {
    if (re.getProxyType() != RoutingEntry.ProxyType.REDIRECT) {
      return;
    }
    logger.debug("resolveRedirect begin redirectPath={}", re.getRedirectPath());
    boolean found = false;
    final String redirectPath = re.getRedirectPath();

    List<ModuleInstance> lookup = lookup(redirectPath, method, filterMap, false, null);
    for (ModuleInstance instance : lookup) {
      RoutingEntry tryre = instance.getRoutingEntry();
      String redirectUri = re.getRedirectUri(uri);
      found = true;
      if (routingEntries.add(tryre)) {
        ModuleInstance mi = new ModuleInstance(instance.getModuleDescriptor(), tryre, redirectUri,
            method, false);
        instances.add(mi);
        resolveRedirect(instances, tryre, loop + " -> " + redirectPath, routingEntries,
            method, redirectUri);
      } else {
        throw new IllegalArgumentException(messages.getMessage("10100", loop, redirectPath));
      }
    }
    lookup = lookup(redirectPath, method, proxyMap, true, null);
    for (ModuleInstance instance : lookup) {
      RoutingEntry tryre = instance.getRoutingEntry();
      String redirectUri = re.getRedirectUri(uri);
      found = true;
      if (routingEntries.add(tryre)) {
        ModuleInstance mi = new ModuleInstance(instance.getModuleDescriptor(), tryre, redirectUri,
            method, true);
        instances.add(mi);
      }
    }
    logger.debug("resolveRedirect end redirectPath={} found={}", re.getRedirectPath(), found);
    if (!found) {
      throw new IllegalArgumentException(messages.getMessage("10101", uri, redirectPath));
    }
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
      int index = tryUri.lastIndexOf('/', tryUri.length() - 2);
      if (index < 0) {
        break;
      }
      tryUri = tryUri.substring(0, index + 1);
    }
    return instances;
  }

  /**
   * Find module instances for uri(path) and method.
   * @param uri request uri
   * @param method HTTP method
   * @param id Proxy-ID for multi lookup; otherwise null
   * @return module instances that match
   * @throws IllegalArgumentException for redirect errors
   */
  public List<ModuleInstance> lookup(String uri, HttpMethod method, String id) {
    logger.debug("lookup {} {} id={}", method::name, () -> uri, () -> id);
    logger.debug("Available modules {}", () -> ModuleUtil.moduleList(moduleDescriptors));
    // perform lookup of filters
    List<ModuleInstance> instances = lookup(uri, method, filterMap, false, null);
    // handle redirects
    Set<RoutingEntry> visitRoutingEntries = new HashSet<>();
    Iterator<ModuleInstance> iterator = instances.iterator();
    while (iterator.hasNext()) {
      ModuleInstance next = iterator.next();
      if (visitRoutingEntries.add(next.getRoutingEntry())) {
        resolveRedirect(instances, next.getRoutingEntry(), "", visitRoutingEntries, method, uri);
        iterator = instances.iterator();
      }
    }
    // perform lookup of proxy or multi
    if (id == null) {
      instances.addAll(lookup(uri, method, proxyMap, true, null));
    } else {
      instances.addAll(lookup(uri, method, multiMap, true, id));
    }
    return instances;
  }
}

