package okapi.util;

import org.folio.okapi.util.ModuleId;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleIdTest {

  public ModuleIdTest() {
  }

  @Test
  public void test() {
    ModuleId m1 = new ModuleId("module-1");
    assertEquals("module: module version: 1", m1.toString());

    ModuleId m2 = new ModuleId("foo-bar1-1.2");
    assertEquals("module: foo-bar1 version: 1 2", m2.toString());

    ModuleId m3 = new ModuleId("module-1.9");
    assertEquals("module: module version: 1 9", m3.toString());

    assertEquals(m1.compareTo(m2), 4);
    assertEquals(m2.compareTo(m1), -4);

    assertEquals(m1.compareTo(m3), -2);
    assertEquals(m3.compareTo(m1), 2);

    assertEquals(m2.compareTo(m3), -4);
    assertEquals(m3.compareTo(m2), 4);

    assertEquals(ModuleId.compare("abc-2.9", "abc-3.5"), -3);
    assertEquals(ModuleId.compare("abc-2", "abcd-3"), -4);
  }
}
