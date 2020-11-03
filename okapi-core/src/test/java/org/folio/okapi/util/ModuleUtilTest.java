package org.folio.okapi.util;

import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleUtilTest {

  @Test
  void testModuleList() {
    List<ModuleDescriptor> list = new LinkedList<>();
    assertThat(ModuleUtil.moduleList(list)).isEqualTo("");

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
