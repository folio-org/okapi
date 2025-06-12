package org.folio.okapi.util;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.MultiMap;
import io.vertx.core.json.DecodeException;
import java.util.Optional;
import org.folio.okapi.common.ModuleId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleVersionFilterTest {
  @Test
  void testConstructor() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    ModuleVersionFilter f = new ModuleVersionFilter(params);
    assertThat(f.npmSnapshot).isNull();
    assertThat(f.preRelease).isNull();

    params.set("npmSnapshot", "only");
    f = new ModuleVersionFilter(params);
    assertThat(f.npmSnapshot).isTrue();
    assertThat(f.preRelease).isNull();
  }

  @Test
  void testGetParamVersionFilter() {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    assertThat(ModuleVersionFilter.getParamVersionFilter(params, "n")).isEmpty();
    params.set("n", "true");
    assertThat(ModuleVersionFilter.getParamVersionFilter(params, "n")).isEmpty();
    params.set("n", "false");
    assertThat(ModuleVersionFilter.getParamVersionFilter(params, "n")).isEqualTo(Optional.of(false));
    params.set("n", "only");
    assertThat(ModuleVersionFilter.getParamVersionFilter(params, "n")).isEqualTo(Optional.of(true));

    params.set("n", "foo");
    String msg = assertThrows(DecodeException.class, () -> ModuleVersionFilter.getParamVersionFilter(params, "n")).getMessage();
    assertThat(msg).isEqualTo("Expected \"true\", \"false\", \"only\" or undefined/null for parameter n, but got: foo");
  }

  @Test
  void testVersionFilterCheckRelease() {
    ModuleId moduleId = new ModuleId("mod-1.0.0");
    assertThat(new ModuleVersionFilter(null, null).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(false, null).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(true, null).matchesModule(moduleId)).isFalse();
    assertThat(new ModuleVersionFilter(null, false).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(null, true).matchesModule(moduleId)).isFalse();
    assertThat(new ModuleVersionFilter(false, false).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(true, true).matchesModule(moduleId)).isFalse();
  }

  @Test
  void testVersionFilterCheckPreRelease() {
    ModuleId moduleId = new ModuleId("mod-1.0.0-SNAPSHOT");
    assertThat(new ModuleVersionFilter(null, null).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(false, null).matchesModule(moduleId)).isFalse();
    assertThat(new ModuleVersionFilter(false, false).matchesModule(moduleId)).isFalse();
    assertThat(new ModuleVersionFilter(false, true).matchesModule(moduleId)).isFalse();
    assertThat(new ModuleVersionFilter(true, null).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(true, true).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(true, false).matchesModule(moduleId)).isTrue();
  }

  @Test
  void testVersionFilterCheckNpmSnapshot() {
    ModuleId moduleId = new ModuleId("mod-1.0.10000");
    assertThat(new ModuleVersionFilter(null, null).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(null, false).matchesModule(moduleId)).isFalse();
    assertThat(new ModuleVersionFilter(false, false).matchesModule(moduleId)).isFalse();
    assertThat(new ModuleVersionFilter(false, true).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(true, null).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(true, true).matchesModule(moduleId)).isTrue();
    assertThat(new ModuleVersionFilter(true, false).matchesModule(moduleId)).isFalse();
  }

}
