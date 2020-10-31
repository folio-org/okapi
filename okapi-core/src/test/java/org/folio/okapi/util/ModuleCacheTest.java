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
import static org.assertj.core.api.Assertions.in;


public class ModuleCacheTest {
  @Test
  void testLookupEmpty() {
    Map<String, List<ModuleInstance>> map = new HashMap<>();

    assertThat(ModuleCache.lookup("", HttpMethod.GET, map, true)).isEmpty();
    assertThat(ModuleCache.lookup("/", HttpMethod.GET, map, true)).isEmpty();
    assertThat(ModuleCache.lookup("/a", HttpMethod.GET, map, true)).isEmpty();
    assertThat(ModuleCache.lookup("/a/b", HttpMethod.GET, map, true)).isEmpty();
  }

  @Test
  void testLookupRoutingEntries() {
    Map<String, List<ModuleInstance>> map = new HashMap<>();
    ModuleDescriptor md = new ModuleDescriptor();
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
    ModuleCache.add(md, map, routingEntries);

    assertThat(ModuleCache.lookup("", HttpMethod.GET, map, true)).isEmpty();
    assertThat(ModuleCache.lookup("/", HttpMethod.GET, map, true)).isEmpty();
    assertThat(ModuleCache.lookup("/a", HttpMethod.GET, map, true)).isEmpty();
    List<ModuleInstance> instances = ModuleCache.lookup("/a/b", HttpMethod.GET, map, true);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry1);

    instances = ModuleCache.lookup("/a/b", HttpMethod.POST, map, true);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry2);

    assertThat(ModuleCache.lookup("/a", HttpMethod.PUT, map, true)).isEmpty();
    assertThat(ModuleCache.lookup("/a/b/", HttpMethod.GET, map, true)).isEmpty();

    instances = ModuleCache.lookup("/a/b/id/c", HttpMethod.GET, map, true);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry3);

    instances = ModuleCache.lookup("/p/id/y", HttpMethod.GET, map, true);
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances.get(0).getRoutingEntry()).isEqualTo(routingEntry4);

    assertThat(ModuleCache.lookup("/old/foo", HttpMethod.GET, map, true)).isEmpty();
    instances = ModuleCache.lookup("/old/type", HttpMethod.GET, map, true);
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
}
