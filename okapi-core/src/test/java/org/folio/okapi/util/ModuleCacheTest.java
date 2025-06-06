package org.folio.okapi.util;

import io.vertx.core.http.HttpMethod;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleCacheTest {
  Function<ModuleInstance, RoutingEntry> routingEntry = ModuleInstance::getRoutingEntry;

  @ParameterizedTest
  @CsvSource({
      "/, /",
      "/{id}, /",
      "{id}, ''",
      "/a/b{id}, /a/",
      "/a/*c, /a/",
      "/a/b*, /a/",
      "/a/b/, /a/b/",
      "/a/b, /a/b",
      "/a/{id}, /a/",
  })
  void testGetPatternPrefix(String pathPattern, String expect) {
    RoutingEntry routingEntry = new RoutingEntry();
    routingEntry.setPathPattern(pathPattern);
    assertThat(ModuleCache.getPatternPrefix(routingEntry)).isEqualTo(expect);
  }

  @Test
  void testPathPrefix()
  {
    RoutingEntry routingEntry = new RoutingEntry();
    routingEntry.setPath("/a/b");
    assertThat(ModuleCache.getPatternPrefix(routingEntry)).isEqualTo("/");
  }

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
    routingEntry5.setPathPattern("/perms/users*");
    routingEntry5.setMethods(new String[] {"GET"});
    routingEntries.add(routingEntry5);
    RoutingEntry routingEntry6 = new RoutingEntry();
    routingEntry6.setPath("/old/type");
    routingEntry6.setMethods(new String[] {"GET"});
    routingEntries.add(routingEntry6);

    Map<String, List<ModuleCache.ModuleCacheEntry>> map = new HashMap<>();
    ModuleCache.add(md, map, routingEntries);

    assertThat(ModuleCache.lookup("", HttpMethod.GET, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/", HttpMethod.GET, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/a", HttpMethod.GET, map, true, null)).isEmpty();
    List<ModuleInstance> instances = ModuleCache.lookup("/a/b", HttpMethod.GET, map, true, null);

    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry1);

    instances = ModuleCache.lookup("/a/b", HttpMethod.GET, map, true, "module-1.0.0");
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry1);

    assertThat(ModuleCache.lookup("/a", HttpMethod.GET, map, true, "other-1.0.0")).isEmpty();

    instances = ModuleCache.lookup("/a/b", HttpMethod.POST, map, true, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry2);

    assertThat(ModuleCache.lookup("/a", HttpMethod.PUT, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/a/b/", HttpMethod.GET, map, true, null)).isEmpty();

    instances = ModuleCache.lookup("/a/b/id/c", HttpMethod.GET, map, true, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry3);

    instances = ModuleCache.lookup("/p/id/y", HttpMethod.GET, map, true, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry4);

    assertThat(ModuleCache.lookup("/p/id/z", HttpMethod.GET, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/p/id", HttpMethod.GET, map, true, null)).isEmpty();
    assertThat(ModuleCache.lookup("/p/id/y/z", HttpMethod.GET, map, true, null)).isEmpty();

    instances = ModuleCache.lookup("/perms/users", HttpMethod.GET, map, true, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry5);

    instances = ModuleCache.lookup("/perms/users/y", HttpMethod.GET, map, true, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry5);

    instances = ModuleCache.lookup("/perms/users1", HttpMethod.GET, map, true, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry5);

    assertThat(ModuleCache.lookup("/perms/user", HttpMethod.GET, map, true, null)).isEmpty();

    assertThat(ModuleCache.lookup("/old/foo", HttpMethod.GET, map, true, null)).isEmpty();
    instances = ModuleCache.lookup("/old/type", HttpMethod.GET, map, true, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry6);
  }

  @Test
  void testModuleRegular() {
    RoutingEntry[] routingEntries = new RoutingEntry[3];
    RoutingEntry routingEntry1 = routingEntries[0] = new RoutingEntry();
    routingEntry1.setPathPattern("/a/client_id");
    routingEntry1.setMethods(new String[] {"GET"});

    RoutingEntry routingEntry2 = routingEntries[1] = new RoutingEntry();
    routingEntry2.setPathPattern("/a/{id}");
    routingEntry2.setMethods(new String[] {"GET"});

    RoutingEntry routingEntry3 = routingEntries[2] = new RoutingEntry();
    routingEntry3.setPathPattern("/a");
    routingEntry3.setMethods(new String[] {"POST"});

    InterfaceDescriptor[] interfaceDescriptors = new InterfaceDescriptor[1];
    InterfaceDescriptor interfaceDescriptor = interfaceDescriptors[0] = new InterfaceDescriptor();
    interfaceDescriptor.setId("int");
    interfaceDescriptor.setHandlers(routingEntries);

    ModuleDescriptor regularModule = new ModuleDescriptor();
    regularModule.setProvides(interfaceDescriptors);
    regularModule.setId("regular-1.0.0");

    List<ModuleDescriptor> modules = new LinkedList<>();
    modules.add(regularModule);
    ModuleCache moduleCache = new ModuleCache(modules);
    assertThat(moduleCache.getModules()).isEqualTo(modules);

    List<ModuleInstance> instances = moduleCache.lookup("/a/id", HttpMethod.GET, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry2);

    instances = moduleCache.lookup("/a/id#a", HttpMethod.GET, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry2);

    instances = moduleCache.lookup("/a/id?a", HttpMethod.GET, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry2);

    assertThat(moduleCache.lookup("/a/id", HttpMethod.GET, "module-id")).isEmpty();

    instances = moduleCache.lookup("/a/client_id", HttpMethod.GET, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry1);
  }

  @Test
  void testModuleMulti() {

    RoutingEntry[] routingEntries = new RoutingEntry[1];
    RoutingEntry routingEntry1 = routingEntries[0] = new RoutingEntry();
    routingEntry1.setPathPattern("/a/{id}");
    routingEntry1.setMethods(new String[]{"GET"});

    InterfaceDescriptor[] interfaceDescriptors = new InterfaceDescriptor[1];
    InterfaceDescriptor interfaceDescriptor = interfaceDescriptors[0] = new InterfaceDescriptor();
    interfaceDescriptor.setId("int");
    interfaceDescriptor.setInterfaceType("multiple");
    interfaceDescriptor.setHandlers(routingEntries);

    ModuleDescriptor regularModule = new ModuleDescriptor();
    regularModule.setProvides(interfaceDescriptors);
    regularModule.setId("module-1.0.0");

    List<ModuleDescriptor> modules = new LinkedList<>();
    modules.add(regularModule);
    ModuleCache moduleCache = new ModuleCache(modules);

    List<ModuleInstance> instances = moduleCache.lookup("/a/id", HttpMethod.GET, "module-1.0.0");
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry1);

    assertThat(moduleCache.lookup("/a/id", HttpMethod.GET, "other-1.0.0")).isEmpty();
    assertThat(moduleCache.lookup("/a/id", HttpMethod.GET, null)).isEmpty();
  }

  @Test
  void testModulesFilter() {

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

    List<ModuleDescriptor> modules = new LinkedList<>();
    modules.add(authModule);
    ModuleCache moduleCache = new ModuleCache(modules);

    List<ModuleInstance> instances = moduleCache.lookup("/a/id", HttpMethod.GET, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry2);

    instances = moduleCache.lookup("/token", HttpMethod.POST, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry2);

    instances = moduleCache.lookup("/token", HttpMethod.GET, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry2, routingEntry1);
  }

  @Test
  void testModuleRedirect() {
    RoutingEntry[] routingEntries2 = new RoutingEntry[4];
    RoutingEntry routingEntry2 = routingEntries2[0] = new RoutingEntry();
    routingEntry2.setPathPattern("/second");
    routingEntry2.setRedirectPath("/real");
    routingEntry2.setType("redirect");
    routingEntry2.setMethods(new String[] {"GET"});

    RoutingEntry routingEntry3 = routingEntries2[1] = new RoutingEntry();
    routingEntry3.setPathPattern("/third");
    routingEntry3.setRedirectPath("/second");
    routingEntry3.setType("redirect");
    routingEntry3.setMethods(new String[] {"GET"});

    RoutingEntry routingEntry4 = routingEntries2[2] = new RoutingEntry();
    routingEntry4.setPathPattern("/loop1");
    routingEntry4.setRedirectPath("/loop2");
    routingEntry4.setType("redirect");
    routingEntry4.setMethods(new String[] {"GET"});

    RoutingEntry routingEntry5 = routingEntries2[3] = new RoutingEntry();
    routingEntry5.setPathPattern("/loop2");
    routingEntry5.setRedirectPath("/loop1");
    routingEntry5.setType("redirect");
    routingEntry5.setMethods(new String[] {"GET"});

    ModuleDescriptor filterModule = new ModuleDescriptor();
    filterModule.setId("filter-1.0.0");
    filterModule.setFilters(routingEntries2);

    List<ModuleDescriptor> modules = new LinkedList<>();
    modules.add(filterModule);
    ModuleCache moduleCache = new ModuleCache(modules);

    assertThat(moduleCache.lookup("/real", HttpMethod.GET, "other-1.0.0")).isEmpty();

    IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      moduleCache.lookup("/second", HttpMethod.GET, null);
    });
    assertThat(ex.getMessage()).contains("Redirecting /second to /real FAILED. No suitable module found");

    ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      moduleCache.lookup("/third", HttpMethod.GET, null);
    });
    assertThat(ex.getMessage()).contains("Redirecting /second to /real FAILED. No suitable module found");

    ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      moduleCache.lookup("/loop1", HttpMethod.GET, null);
    });
    assertThat(ex.getMessage()).contains("Redirect loop:  -> /loop2 -> /loop1");

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      moduleCache.lookup("/loop2", HttpMethod.GET, null);
    });

    RoutingEntry[] routingEntries1 = new RoutingEntry[1];
    RoutingEntry routingEntry1 = routingEntries1[0] = new RoutingEntry();
    routingEntry1.setPathPattern("/real");
    routingEntry1.setMethods(new String[] {"GET"});

    InterfaceDescriptor[] interfaceDescriptors1 = new InterfaceDescriptor[1];
    InterfaceDescriptor interfaceDescriptor1 = interfaceDescriptors1[0] = new InterfaceDescriptor();
    interfaceDescriptor1.setId("int");
    interfaceDescriptor1.setHandlers(routingEntries1);

    ModuleDescriptor regularModule = new ModuleDescriptor();
    regularModule.setProvides(interfaceDescriptors1);
    regularModule.setId("regular-1.0.0");

    modules.add(regularModule);
    ModuleCache moduleCache2 = new ModuleCache(modules);

    List<ModuleInstance> instances = moduleCache2.lookup("/second", HttpMethod.GET, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry2, routingEntry1);

    instances = moduleCache2.lookup("/third", HttpMethod.GET, null);
    assertThat(instances).extracting(routingEntry).containsExactly(routingEntry3, routingEntry2, routingEntry1);
  }


}
