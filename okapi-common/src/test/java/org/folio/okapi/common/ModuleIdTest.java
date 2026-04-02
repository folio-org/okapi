package org.folio.okapi.common;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleIdTest {

  @Test
  void test() {
    ModuleId module1 = new ModuleId("module-1");
    assertEquals("module-1", module1.getId());
    assertTrue(module1.hasSemVer());
    assertFalse(module1.hasPreRelease());
    assertEquals("module", module1.getProduct());
    assertEquals("module-1", module1.toString());

    ModuleId module1plus2 = new ModuleId("module-1-2+3");
    assertEquals("module-1-2+3", module1plus2.getId());
    assertTrue(module1plus2.hasSemVer());
    assertTrue(module1plus2.hasPreRelease());
    assertFalse(module1plus2.hasNpmSnapshot());
    assertEquals("module", module1plus2.getProduct());
    assertEquals("module-1-2+3", module1plus2.toString());
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
    var equals = module1.equals(null);
    assertFalse(equals);
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
    ModuleId module1 = new ModuleId("module-1");
    ModuleId foobar1dot2 = new ModuleId("foo-bar1-1.2");
    assertEquals("foo-bar1-1.2", foobar1dot2.toString());

    ModuleId module1dot9 = new ModuleId("module-1.9");
    assertEquals("module-1.9", module1dot9.toString());

    ModuleId module = new ModuleId("module");
    assertEquals("module", module.toString());

    assertTrue(module1.hasPrefix(module));
    assertFalse(module.hasPrefix(module1));
    assertTrue(module1dot9.hasPrefix(module));
    assertTrue(module1dot9.hasPrefix(module1));
    assertFalse(module1.hasPrefix(module1dot9));
    assertFalse(foobar1dot2.hasPrefix(module));

    assertEquals(5, module1.compareTo(foobar1dot2));
    assertEquals(-5, foobar1dot2.compareTo(module1));

    assertEquals(-3, module1.compareTo(module1dot9));
    assertEquals(3, module1dot9.compareTo(module1));

    assertEquals(-5, foobar1dot2.compareTo(module1dot9));
    assertEquals(5, module1dot9.compareTo(foobar1dot2));

    assertEquals(-4, module.compareTo(module1));
    assertEquals(4, module1.compareTo(module));
    assertEquals(0, module.compareTo(module));

    assertEquals(-4, ModuleId.compare("abc-2.9", "abc-3.5"));
    assertEquals(-5, ModuleId.compare("abc-2", "abcd-3"));

    assertNotEquals(module1, foobar1dot2);

    assertTrue(module1.hasSemVer());
    assertEquals("1", module1.getSemVer().toString());
  }

  @Test
  void testLatest() {
    ModuleId module = new ModuleId("module-1");
    List<String> versionsL = new LinkedList<>();
    versionsL.add("module-1.0");
    versionsL.add("module-1.0-2");
    versionsL.add("module-0.9");
    versionsL.add("other-1.1");
    versionsL.add("other-0.9");
    assertEquals("module-1.0", module.getLatest(versionsL));
    assertEquals("module-1", module.getLatest(new LinkedList<>()));
  }

  @Test
  void testNpmSnapshot() {
    ModuleId module = new ModuleId("module-1.2.10000");
    assertTrue(module.hasSemVer());
    assertFalse(module.hasPreRelease());
    assertTrue(module.hasNpmSnapshot());
  }

  @Test
  void testWithoutSemVer() {
    ModuleId module = new ModuleId("module");
    assertFalse(module.hasSemVer());
    assertFalse(module.hasPreRelease());
    assertFalse(module.hasNpmSnapshot());
  }
}
