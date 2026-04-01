package org.folio.okapi.common;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleIdTest {

  @Test
  void test() {
    ModuleId module_1 = new ModuleId("module-1");
    assertEquals("module-1", module_1.getId());
    assertTrue(module_1.hasSemVer());
    assertFalse(module_1.hasPreRelease());
    assertEquals("module", module_1.getProduct());
    assertEquals("module-1", module_1.toString());

    ModuleId module_1plus2 = new ModuleId("module-1-2+3");
    assertEquals("module-1-2+3", module_1plus2.getId());
    assertTrue(module_1plus2.hasSemVer());
    assertTrue(module_1plus2.hasPreRelease());
    assertFalse(module_1plus2.hasNpmSnapshot());
    assertEquals("module", module_1plus2.getProduct());
    assertEquals("module-1-2+3", module_1plus2.toString());
  }

  static class ExtendedModuleId extends ModuleId {
    public final String extension;

    public ExtendedModuleId(String s, String extension) {
      super(s);
      this.extension = extension;
    }
  }

  @Test
  void testEquals() {
    var module1 = new ModuleId("module-1");
    var module1plus2 = new ModuleId("module-1-2+3");
    assertNotEquals(module1, module1plus2);
    var module1ref = module1;
    assertEquals(module1, module1ref);
    var module1plus2copy = new ModuleId("module-1-2+3");
    assertEquals(module1plus2, module1plus2copy);
    assertFalse(module1.equals(null));
    assertNotEquals(module1, "module-1");
    var extendedModule1 = new ExtendedModuleId("module-1", "e");
    var extendedModule2 = new ExtendedModuleId("module-2", "e");
    var extendedModule1copy = new ExtendedModuleId("module-1", "f");
    assertEquals(extendedModule1, extendedModule1);
    assertNotEquals(extendedModule1, extendedModule2);
    assertEquals(extendedModule1, extendedModule1copy);
    assertNotEquals(extendedModule1, module1);
    assertNotEquals(module1, extendedModule1);

    assertEquals(module1plus2.hashCode(), module1plus2copy.hashCode());
    assertNotEquals(module1.hashCode(), module1plus2.hashCode());
  }

  @Test
  void testComparisons() {
    ModuleId module_1 = new ModuleId("module-1");
    ModuleId foobar_1_2 = new ModuleId("foo-bar1-1.2");
    assertEquals("foo-bar1-1.2", foobar_1_2.toString());

    ModuleId module_1_9 = new ModuleId("module-1.9");
    assertEquals("module-1.9", module_1_9.toString());

    ModuleId module = new ModuleId("module");
    assertEquals("module", module.toString());

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

    assertNotEquals(module_1, foobar_1_2);

    assertTrue(module_1.hasSemVer());
    assertEquals("1", module_1.getSemVer().toString());
  }

  @Test
  void testLatest() {
    ModuleId module_1 = new ModuleId("module-1");
    List<String> versionsL = new LinkedList<>();
    versionsL.add("module-1.0");
    versionsL.add("module-1.0-2");
    versionsL.add("module-0.9");
    versionsL.add("other-1.1");
    versionsL.add("other-0.9");
    assertEquals("module-1.0", module_1.getLatest(versionsL));
    assertEquals("module-1", module_1.getLatest(new LinkedList<>()));
  }

  @Test
  void testNpmSnapshot() {
    ModuleId module_1_10000 = new ModuleId("module-1.2.10000");
    assertTrue(module_1_10000.hasSemVer());
    assertFalse(module_1_10000.hasPreRelease());
    assertTrue(module_1_10000.hasNpmSnapshot());
  }

  @Test
  void testWithoutSemVer() {
    ModuleId module = new ModuleId("module");
    assertFalse(module.hasSemVer());
    assertFalse(module.hasPreRelease());
    assertFalse(module.hasNpmSnapshot());
  }
}
