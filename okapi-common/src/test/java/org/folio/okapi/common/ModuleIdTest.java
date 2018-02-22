package org.folio.okapi.common;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleIdTest {
  @Test
  public void test() {
    ModuleId module_1 = new ModuleId("module-1");
    assertEquals("module: module version: 1", module_1.toString());

    ModuleId foobar_1_2 = new ModuleId("foo-bar1-1.2");
    assertEquals("module: foo-bar1 version: 1 2", foobar_1_2.toString());

    ModuleId module_1_9 = new ModuleId("module-1.9");
    assertEquals("module: module version: 1 9", module_1_9.toString());

    ModuleId module = new ModuleId("module");
    assertEquals("module: module", module.toString());

    assertTrue(module_1.hasPrefix(module));
    assertFalse(module.hasPrefix(module_1));
    assertTrue(module_1_9.hasPrefix(module));
    assertTrue(module_1_9.hasPrefix(module_1));
    assertFalse(module_1.hasPrefix(module_1_9));
    assertFalse(foobar_1_2.hasPrefix(module));

    assertEquals(5, module_1.compareTo(foobar_1_2));
    assertEquals(-5, foobar_1_2.compareTo(module_1));

    assertEquals(-3, module_1.compareTo(module_1_9));
    assertEquals(3, module_1_9.compareTo(module_1));

    assertEquals(-5, foobar_1_2.compareTo(module_1_9));
    assertEquals(5, module_1_9.compareTo(foobar_1_2));

    assertEquals(-4, module.compareTo(module_1));
    assertEquals(4, module_1.compareTo(module));
    assertEquals(0, module.compareTo(module));

    assertEquals(-4, ModuleId.compare("abc-2.9", "abc-3.5"));
    assertEquals(-5, ModuleId.compare("abc-2", "abcd-3"));

    assertTrue(module_1.equals(module_1));
    assertFalse(module_1.equals(foobar_1_2));
  }
}
