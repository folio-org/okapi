package org.folio.okapi.util;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleUtilTest {
  static JsonArray ar;
  List<ModuleDescriptor> modulesList;

  @BeforeAll
  static void beforeClass() throws IOException {
    String modulesJson = new String(ModuleUtilTest.class.getClassLoader().getResourceAsStream("modules2.json").readAllBytes());
    ar = new JsonArray(modulesJson);
  }

  @BeforeEach
  void beforeEach() {
    modulesList = new LinkedList<>();
    for (int i = 0; i < ar.size(); i++) {
      modulesList.add(ar.getJsonObject(i).mapTo(ModuleDescriptor.class));
    }
  }

  @Test
  void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(ModuleUtil.class);
  }

  @Test
  void testFilterNone() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(modulesList.size());
  }

  @Test
  void testFilterRequireNotify() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("require", "notify");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(1);
  }

  @Test
  void testFilterRequireNotify2_0() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("require", "notify=2.0");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(1);
  }

  @Test
  void testFilterRequireNotify2_1() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("require", "notify=2.1");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(0);
  }

  @Test
  void testFilterRequireNotify2() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("require", "notify=2");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(0);
  }

  @Test
  void testFilterRequirePermissions() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("require", "permissions");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(5);
  }

  @Test
  void testFilterRequireNotes() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("require", "notes");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(3);
  }

  @Test
  void testFilterRequireNotifyOrPermissions() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("require", "notify,permissions");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(5);
  }

  @Test
  void testFilterProvideCodex() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("provide", "codex");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(4);
  }

  @Test
  void testFilterProvidePermissions() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("provide", "permissions");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(1);
  }

  @Test
  void testFilterPermissions() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("filter", "mod-permissions");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(1);
  }

  @Test
  void testFilterPermissions5_11_4() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("filter", "mod-permissions-5.11.4");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(1);
  }

  @Test
  void testFilterPermissions5_11_0() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.set("filter", "mod-permissions-5.11.0");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res.size()).isEqualTo(0);
  }

  @Test
  void testModuleList() {
    List<ModuleDescriptor> list = new LinkedList<>();
    assertThat(ModuleUtil.moduleList(list)).isEmpty();

    ModuleDescriptor md = new ModuleDescriptor();
    md.setId("foo-1.0.0");
    list.add(md);
    assertThat(ModuleUtil.moduleList(list)).isEqualTo("foo-1.0.0");

    md = new ModuleDescriptor();
    md.setId("bar-2.0.0");
    list.add(md);
    assertThat(ModuleUtil.moduleList(list)).isEqualTo("foo-1.0.0, bar-2.0.0");
  }
}
