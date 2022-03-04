package org.folio.okapi.common;

import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleIdTest {

  @java.lang.SuppressWarnings({"squid:S5961"}) // more than 25 assertions
  @Test
  public void test() {
    ModuleId module_1 = new ModuleId("søvang-1");
    assertEquals("søvang-1", module_1.getId());
    assertTrue(module_1.hasSemVer());
    assertFalse(module_1.hasPreRelease());
    assertEquals("søvang", module_1.getProduct());
    assertEquals("søvang-1", module_1.toString());

    ModuleId module_1_9 = new ModuleId("søvang-1.9");
    assertEquals("søvang-1.9", module_1_9.toString());

    ModuleId module = new ModuleId("søvang");
    assertEquals("søvang", module.toString());

    ModuleId module_1plus2 = new ModuleId("module-1-2+3");
    assertEquals("module-1-2+3", module_1plus2.getId());
    assertTrue(module_1plus2.hasSemVer());
    assertTrue(module_1plus2.hasPreRelease());
    assertFalse(module_1plus2.hasNpmSnapshot());
    assertEquals("module", module_1plus2.getProduct());
    assertEquals("module-1-2+3", module_1plus2.toString());

    assertNotEquals(module_1, module_1plus2);
    ModuleId module_1plus2copy = new ModuleId("module-1-2+3");
    assertEquals(module_1plus2, module_1plus2copy);

    assertEquals(module_1plus2.hashCode(), module_1plus2copy.hashCode());

    ModuleId foobar_1_2 = new ModuleId("foo-bar1-1.2");
    assertEquals("foo-bar1-1.2", foobar_1_2.toString());

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
  public void testHyphenMinusEnd() {
    assertEquals("a-", new ModuleId("a-").getProduct());
  }

  @Test
  public void testHyphenNoVersion() {
    assertEquals("a-x", new ModuleId("a-x").getProduct());
  }

  @Test
  public void testUnderScore() {
    assertEquals("a_x", new ModuleId("a_x").getProduct());
  }

  @Test
  public void testEmpty() {
    assertEquals("ModuleID must not be empty",
        assertThrows(IllegalArgumentException.class, () -> new ModuleId("")).getMessage());
  }

  @Test
  public void testBadLead() {
    assertEquals("ModuleID '1' must start with letter",
        assertThrows(IllegalArgumentException.class, () -> new ModuleId("1")).getMessage());
    assertEquals("ModuleID '-1' must start with letter",
        assertThrows(IllegalArgumentException.class, () -> new ModuleId("-1")).getMessage());
    assertEquals("ModuleID '_1' must start with letter",
        assertThrows(IllegalArgumentException.class, () -> new ModuleId("_1")).getMessage());
    assertEquals("ModuleID ' a' must start with letter",
        assertThrows(IllegalArgumentException.class, () -> new ModuleId(" a")).getMessage());
  }

  @Test
  public void testBadCharacter() {
    assertEquals("ModuleID 'my 2' has non-allowed character at offset 2",
        assertThrows(IllegalArgumentException.class, () -> new ModuleId("my 2")).getMessage());
    assertEquals("ModuleID 'my ' has non-allowed character at offset 2",
        assertThrows(IllegalArgumentException.class, () -> new ModuleId("my ")).getMessage());
  }

  @Test
  public void testLatest() {
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
  public void testNpmSnapshot() {
    ModuleId module_1_10000 = new ModuleId("module-1.2.10000");
    assertTrue(module_1_10000.hasSemVer());
    assertFalse(module_1_10000.hasPreRelease());
    assertTrue(module_1_10000.hasNpmSnapshot());
  }

  @Test
  public void testWithoutSemVer() {
    ModuleId module = new ModuleId("module");
    assertFalse(module.hasSemVer());
    assertFalse(module.hasPreRelease());
    assertFalse(module.hasNpmSnapshot());
  }
}
