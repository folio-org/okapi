package org.folio.okapi.util;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.MultiMap;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.ModuleId;
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
    int sz = modulesList.size();
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(sz).isEqualTo(res.size());
  }

  @Test
  void testFilterOnly() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.add("npmSnapshot", "only");
    params.add("preRelease", "only");
    List<ModuleDescriptor> res = ModuleUtil.filter(params, modulesList, false, false);
    assertThat(res).isEmpty();
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
    List<ModuleDescriptor> list = List.of();
    assertThat(ModuleUtil.moduleList(list)).isEmpty();

    list = List.of(new ModuleDescriptor("foo-1.0.0"));
    assertThat(ModuleUtil.moduleList(list)).isEqualTo("foo-1.0.0");

    list = List.of(new ModuleDescriptor("foo-1.0.0"), new ModuleDescriptor("bar-2.0.0"));
    assertThat(ModuleUtil.moduleList(list)).isEqualTo("foo-1.0.0, bar-2.0.0");
  }

  @Test
  void testGetObsolete1() {
    assertThat(modulesList).hasSize(104);
    List<ModuleDescriptor> obsolete = ModuleUtil.getObsolete(modulesList, 1, 0);
    assertThat(obsolete).isEmpty();
  }

  @Test
  void testGetObsoleteBackend() {
    String ids[] = new String [] {
        "mod-a-0.9.0-SNAPSHOT.3",
        "mod-a-1.0.0-SNAPSHOT.4",
        "mod-a-1.0.0",
        "mod-b-1.0.0-SNAPSHOT.1",
        "mod-a-1.1.0-SNAPSHOT.5",
        "mod-a-0.8.0-SNAPSHOT.1",
        "mod-a-0.8.0-SNAPSHOT.2",
        "mod-a-0.8.0",
    };
    List<ModuleDescriptor> mds = new LinkedList<>();
    for (String id : ids) {
      mds.add(new ModuleDescriptor(id));
    }

    // remove all snapshots except for latest version
    List<ModuleDescriptor> obsolete = ModuleUtil.getObsolete(mds, 1, 0);
    assertThat(obsolete).hasSize(4);
    assertThat(obsolete.get(0).getId()).isEqualTo("mod-a-1.0.0-SNAPSHOT.4");
    assertThat(obsolete.get(1).getId()).isEqualTo("mod-a-0.9.0-SNAPSHOT.3");
    assertThat(obsolete.get(2).getId()).isEqualTo("mod-a-0.8.0-SNAPSHOT.2");
    assertThat(obsolete.get(3).getId()).isEqualTo("mod-a-0.8.0-SNAPSHOT.1");

    // remove all snapshots
    obsolete = ModuleUtil.getObsolete(mds, 0, 0);
    assertThat(obsolete).hasSize(6);
    assertThat(obsolete.get(0).getId()).isEqualTo("mod-b-1.0.0-SNAPSHOT.1");
    assertThat(obsolete.get(1).getId()).isEqualTo("mod-a-1.1.0-SNAPSHOT.5");
    assertThat(obsolete.get(2).getId()).isEqualTo("mod-a-1.0.0-SNAPSHOT.4");
    assertThat(obsolete.get(3).getId()).isEqualTo("mod-a-0.9.0-SNAPSHOT.3");
    assertThat(obsolete.get(4).getId()).isEqualTo("mod-a-0.8.0-SNAPSHOT.2");
    assertThat(obsolete.get(5).getId()).isEqualTo("mod-a-0.8.0-SNAPSHOT.1");

    // remove all snapshots except latest in all releases
    obsolete = ModuleUtil.getObsolete(mds, 0, 1);
    assertThat(obsolete).hasSize(2);
    assertThat(obsolete.get(0).getId()).isEqualTo("mod-a-0.9.0-SNAPSHOT.3");
    assertThat(obsolete.get(1).getId()).isEqualTo("mod-a-0.8.0-SNAPSHOT.1");

    // remove all snapshots except latest 2 in all releases
    obsolete = ModuleUtil.getObsolete(mds, 0, 2);
    assertThat(obsolete).isEmpty();

    obsolete = ModuleUtil.getObsolete(mds, 3, 0);
    assertThat(obsolete).hasSize(2);
    assertThat(obsolete.get(0).getId()).isEqualTo("mod-a-0.8.0-SNAPSHOT.2");
    assertThat(obsolete.get(1).getId()).isEqualTo("mod-a-0.8.0-SNAPSHOT.1");

    obsolete = ModuleUtil.getObsolete(mds, 3, 1);
    assertThat(obsolete).hasSize(1);
    assertThat(obsolete.get(0).getId()).isEqualTo("mod-a-0.8.0-SNAPSHOT.1");
  }

  @Test
  void testGetObsoleteUI() {
    String ids[] = new String [] {
        "a-2.3.100078",
        "a-2.3.100079",
        "a-2.3.0",
        "a-2.4.0",
        "a-2.4.10000104",
        "a-2.4.10000105",
        "a-2.4.10000106",
    };
    List<ModuleDescriptor> mds = new LinkedList<>();
    for (String id : ids) {
      mds.add(new ModuleDescriptor(id));
    }

    List<ModuleDescriptor> obsolete = ModuleUtil.getObsolete(mds, 1, 0);
    assertThat(obsolete).hasSize(2);
    assertThat(obsolete.get(0).getId()).isEqualTo("a-2.3.100079");
    assertThat(obsolete.get(1).getId()).isEqualTo("a-2.3.100078");

    obsolete = ModuleUtil.getObsolete(mds, 1, 1);
    assertThat(obsolete).hasSize(1);
    assertThat(obsolete.get(0).getId()).isEqualTo("a-2.3.100078");

    obsolete = ModuleUtil.getObsolete(mds, 2, 0);
    assertThat(obsolete).isEmpty();
  }

  @Test
  void testGetParamInteger() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    assertThat(ModuleUtil.getParamInteger(params, "x", 1)).isEqualTo(1);
    assertThrows(DecodeException.class, () -> ModuleUtil.getParamInteger(params, "x", null));
    params.add("x", "2");
    assertThat(ModuleUtil.getParamInteger(params, "x", 1)).isEqualTo(2);
    params.add("y", "bad");
    assertThrows(DecodeException.class, () -> ModuleUtil.getParamInteger(params, "y", 1));
  }

  @Test
  void testGetParamVersionFilter() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    assertThat(ModuleUtil.getParamVersionFilter(params, "n")).isEmpty();
    params.set("n", "true");
    assertThat(ModuleUtil.getParamVersionFilter(params, "n")).isEmpty();
    params.set("n", "false");
    assertThat(ModuleUtil.getParamVersionFilter(params, "n")).isEqualTo(Optional.of(false));
    params.set("n", "only");
    assertThat(ModuleUtil.getParamVersionFilter(params, "n")).isEqualTo(Optional.of(true));
  }

  @Test
  void testVersionFilterCheckRelease() {
    ModuleId moduleId = new ModuleId("mod-1.0.0");
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.empty(), Optional.empty())).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(false), Optional.empty())).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.empty(), Optional.of(false))).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(false), Optional.of(false))).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(true), Optional.of(true))).isFalse();
  }

  @Test
  void testVersionFilterCheckPreRelease() {
    ModuleId moduleId = new ModuleId("mod-1.0.0-SNAPSHOT");
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.empty(), Optional.empty())).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(false), Optional.empty())).isFalse();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(false), Optional.of(false))).isFalse();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(false), Optional.of(true))).isFalse();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(true), Optional.empty())).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(true), Optional.of(true))).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(true), Optional.of(false))).isTrue();
  }

  @Test
  void testVersionFilterCheckNpmSnapshot() {
    ModuleId moduleId = new ModuleId("mod-1.0.10000");
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.empty(), Optional.empty())).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.empty(), Optional.of(false))).isFalse();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(false), Optional.of(false))).isFalse();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(false), Optional.of(true))).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(true), Optional.empty())).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(true), Optional.of(true))).isTrue();
    assertThat(ModuleUtil.versionFilterCheck(moduleId, Optional.of(true), Optional.of(false))).isFalse();
  }

}
