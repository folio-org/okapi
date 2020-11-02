package org.folio.okapi.util;

import io.vertx.core.http.HttpMethod;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleCacheTest {
  @Test
  void testLookupEmpty() {
    Map<String, List<ModuleCache.ModuleCacheEntry>> map = new HashMap<>();

    assertThat(ModuleCache.lookup("", HttpMethod.GET, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/", HttpMethod.GET, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/a", HttpMethod.GET, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/a/b", HttpMethod.GET, map, true, null)).isEmpty();
  }

  @Test
  void testLookupRoutingEntries() {
    ModuleDescriptor md = new ModuleDescriptor();
    md.setId("module-1.0.0");
    List<RoutingEntry> routingEntries = new LinkedList<>();
    RoutingEntry routingEntry1 = new RoutingEntry();
    routingEntry1.setPathPattern("/a/b");
    routingEntry1.setMethods(new String[] {"GET"});
    routingEntries.add(routingEntry1);
    RoutingEntry routingEntry2 = new RoutingEntry();
    routingEntry2.setPathPattern("/a/b");
    routingEntry2.setMethods(new String[] {"POST"});
    routingEntries.add(routingEntry2);
    RoutingEntry routingEntry3 = new RoutingEntry();
    routingEntry3.setPathPattern("/a/b/{id}/c");
    routingEntry3.setMethods(new String[] {"GET"});
    routingEntries.add(routingEntry3);
    RoutingEntry routingEntry4 = new RoutingEntry();
    routingEntry4.setPathPattern("/p/*/y");
    routingEntry4.setMethods(new String[] {"GET"});
    routingEntries.add(routingEntry4);
    RoutingEntry routingEntry5 = new RoutingEntry();
    routingEntry5.setPath("/old/type");
    routingEntry5.setMethods(new String[] {"GET"});
    routingEntries.add(routingEntry5);

    Map<String, List<ModuleCache.ModuleCacheEntry>> map = new HashMap<>();
    ModuleCache.add(md, map, routingEntries);

    assertThat(ModuleCache.lookup("", HttpMethod.GET, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/", HttpMethod.GET, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/a", HttpMethod.GET, map, true, null)).isEmpty();
    List<ModuleInstance> instances = ModuleCache.lookup("/a/b", HttpMethod.GET, map, true, null);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry1);

    instances = ModuleCache.lookup("/a/b", HttpMethod.GET, map, true, "module-1.0.0");
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry1);

    assertThat(ModuleCache.lookup("/a", HttpMethod.GET, map, true, "other-1.0.0")).isEmpty();

    instances = ModuleCache.lookup("/a/b", HttpMethod.POST, map, true, null);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry2);

    assertThat(ModuleCache.lookup("/a", HttpMethod.PUT, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/a/b/", HttpMethod.GET, map, true, null)).isEmpty();

    instances = ModuleCache.lookup("/a/b/id/c", HttpMethod.GET, map, true, null);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry3);

    instances = ModuleCache.lookup("/p/id/y", HttpMethod.GET, map, true, null);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry4);

    assertThat(ModuleCache.lookup("/old/foo", HttpMethod.GET, map, true, null)).isEmpty();
    instances = ModuleCache.lookup("/old/type", HttpMethod.GET, map, true, null);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry5);
  }

  @Test
  void testModules() {
    ModuleCache moduleCache = new ModuleCache();

    RoutingEntry[] routingEntries = new RoutingEntry[2];
    RoutingEntry routingEntry1 = routingEntries[0] = new RoutingEntry();
    routingEntry1.setPathPattern("/a/{id}");
    routingEntry1.setMethods(new String[] {"GET"});

    RoutingEntry routingEntry2 = routingEntries[1] = new RoutingEntry();
    routingEntry2.setPathPattern("/a");
    routingEntry2.setMethods(new String[] {"POST"});

    InterfaceDescriptor[] interfaceDescriptors = new InterfaceDescriptor[1];
    InterfaceDescriptor interfaceDescriptor = interfaceDescriptors[0] = new InterfaceDescriptor();
    interfaceDescriptor.setId("int");
    interfaceDescriptor.setHandlers(routingEntries);

    ModuleDescriptor regularModule = new ModuleDescriptor();
    regularModule.setProvides(interfaceDescriptors);
    regularModule.setId("regular-1.0.0");
    moduleCache.add(regularModule);

    List<ModuleInstance> instances = moduleCache.lookup("/a/id", HttpMethod.GET, null);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry1);

    assertThat(moduleCache.lookup("/a/id", HttpMethod.GET, "module-id")).isEmpty();

    moduleCache.clear();
    assertThat(moduleCache.lookup("/a/id", HttpMethod.GET, null)).isEmpty();
  }

  @Test
  void testModulesAuth() {
    ModuleCache moduleCache = new ModuleCache();

    RoutingEntry[] routingEntries1 = new RoutingEntry[1];
    RoutingEntry routingEntry1 = routingEntries1[0] = new RoutingEntry();
    routingEntry1.setPathPattern("/token");
    routingEntry1.setMethods(new String[] {"GET"});

    InterfaceDescriptor[] interfaceDescriptors = new InterfaceDescriptor[1];
    InterfaceDescriptor interfaceDescriptor = interfaceDescriptors[0] = new InterfaceDescriptor();
    interfaceDescriptor.setId("auth");
    interfaceDescriptor.setHandlers(routingEntries1);

    RoutingEntry[] routingEntries2 = new RoutingEntry[1];
    RoutingEntry routingEntry2 = routingEntries2[0] = new RoutingEntry();
    routingEntry2.setPathPattern("/*");
    routingEntry2.setMethods(new String[] {"*"});

    ModuleDescriptor authModule = new ModuleDescriptor();
    authModule.setProvides(interfaceDescriptors);
    authModule.setFilters(routingEntries2);
    authModule.setId("auth-1.0.0");
    moduleCache.add(authModule);

    List<ModuleInstance> instances = moduleCache.lookup("/a/id", HttpMethod.GET, null);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry2);

    instances = moduleCache.lookup("/token", HttpMethod.POST, null);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry2);

    instances = moduleCache.lookup("/token", HttpMethod.GET, null);
    assertThat(instances.size()).isEqualTo(2);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry2);
    assertThat(instances.get(1).getRoutingEntry()).isEqualTo(routingEntry1);
  }
}
