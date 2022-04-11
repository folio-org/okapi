package org.folio.okapi.common;

import org.junit.Test;
import static org.junit.Assert.*;

public class SemVerTest {

  private SemVer createVersion(String id, boolean isPrelease, boolean isNpmsnapshot) {
    SemVer v = new SemVer(id);
    assertEquals(id, v.toString());
    assertEquals(isPrelease, v.hasPreRelease());
    assertEquals(isNpmsnapshot, v.hasNpmSnapshot());
    return v;
  }

  private void invalidVersion(String id, String exp) {
    SemVer v = null;
    try {
      v = new SemVer(id);
    } catch (IllegalArgumentException ex) {
      if (exp != null) {
        assertEquals(exp, ex.getMessage());
      }
    }
    assertNull(v);
  }

  @java.lang.SuppressWarnings({"squid:S5961"}) // more than 25 assertions
  @Test
  public void test() {
    SemVer v1 = createVersion("1", false, false);
    SemVer v2 = createVersion("2", false, false);

    assertEquals(-4, v1.compareTo(v2));
    assertEquals(4, v2.compareTo(v1));
    assertEquals(0, v1.compareTo(v1));
    assertEquals(0, v2.compareTo(v2));

    assertFalse(v1.hasPrefix(v2));
    assertFalse(v2.hasPrefix(v1));

    SemVer v1_0 = createVersion("1.0", false, false);
    SemVer v1_2 = createVersion("1.2", false, false);
    assertTrue(v1_2.hasPrefix(v1));
    assertFalse(v1_2.hasPrefix(v2));

    assertEquals(3, v1_2.compareTo(v1));
    assertEquals(-3, v1.compareTo(v1_2));
    assertEquals(4, v2.compareTo(v1_2));

    SemVer v1_10_0 = createVersion("1.10.0", false, false);
    SemVer v1_10_1 = createVersion("1.10.1", false, false);
    assertEquals(2, v1_10_1.compareTo(v1_10_0));
    assertEquals(-2, v1_10_0.compareTo(v1_10_1));
    assertEquals(3, v1_10_1.compareTo(v1_0));
    assertEquals(-3, v1_0.compareTo(v1_10_0));

    assertFalse(v1_10_0.hasPrefix(v1_10_1));
    assertFalse(v1_10_1.hasPrefix(v1_10_0));

    SemVer p1 = createVersion("1.0.0-alpha", true, false);
    SemVer p2 = createVersion("1.0.0-alpha.1", true, false);
    SemVer p3 = createVersion("1.0.0-alpha.beta", true, false);
    SemVer p4 = createVersion("1.0.0-beta", true, false);
    SemVer p5 = createVersion("1.0.0-beta.2", true, false);
    SemVer p6 = createVersion("1.0.0-beta.11", true, false);
    SemVer p7 = createVersion("1.0.0-rc.1", true, false);
    SemVer p8 = createVersion("1.0.0", false, false);
    assertNotEquals(p1, p2);
    SemVer p1Copy = createVersion("1.0.0-alpha", true, false);
    assertEquals(p1, p1Copy);

    assertTrue(p1.hasPrefix(v1));
    assertTrue(p7.hasPrefix(v1));
    assertTrue(p8.hasPrefix(v1));
    assertTrue(p1.hasPrefix(v1_0));
    assertTrue(p7.hasPrefix(v1_0));
    assertTrue(p8.hasPrefix(v1_0));
    assertFalse(v1.hasPrefix(p8));
    assertTrue(p1.hasPrefix(p8));
    assertFalse(p4.hasPrefix(p5));
    assertTrue(p5.hasPrefix(p4));
    assertTrue(p6.hasPrefix(p4));
    assertFalse(p6.hasPrefix(p5));

    assertEquals(-1, p1.compareTo(p2));
    assertEquals(-1, p2.compareTo(p3));
    assertEquals(-1, p3.compareTo(p4));
    assertEquals(-1, p4.compareTo(p5));
    assertEquals(-1, p5.compareTo(p6));
    assertEquals(-1, p6.compareTo(p7));
    assertEquals(-1, p7.compareTo(p8));

    assertEquals(1, p2.compareTo(p1));
    assertEquals(1, p3.compareTo(p2));
    assertEquals(1, p4.compareTo(p3));
    assertEquals(1, p5.compareTo(p4));
    assertEquals(1, p6.compareTo(p5));
    assertEquals(1, p7.compareTo(p6));
    assertEquals(1, p8.compareTo(p7));

    SemVer snap1 = createVersion("1.0.0-rc.1+snapshot-2017.1", true, false);
    SemVer snap2 = createVersion("1.0.0-rc.1+snapshot-2017.2", true, false);
    assertTrue(snap1.hasPrefix(snap1));
    assertFalse(snap1.hasPrefix(snap2));
    assertFalse(snap2.hasPrefix(snap1));

    SemVer snap3 = createVersion("1.0.0+snapshot-2017.2", false, false);
    SemVer snap4 = createVersion("1.0.0-rc.1", true, false);
    assertTrue(snap1.hasPrefix(snap4));
    assertFalse(snap4.hasPrefix(snap1));
    assertEquals(1, snap1.compareTo(snap4));
    assertEquals(-1, snap4.compareTo(snap1));

    assertEquals(-1, snap1.compareTo(snap2));
    assertEquals(1, snap2.compareTo(snap1));
    assertEquals(1, snap3.compareTo(snap1));
    assertEquals(-1, snap1.compareTo(snap3));

    SemVer npmSnapshot = new SemVer("4000001006.1.0");
    assertEquals(-4, snap1.compareTo(npmSnapshot));

    SemVer longV = new SemVer("123456789012345678");
    assertEquals(4, longV.compareTo(npmSnapshot));
  }

  @Test
  public void testMixedPrerelease() {
    SemVer v2a3a = createVersion("1.0.0-2a-3a", true, false);
    SemVer v2a3 = createVersion("1.0.0-2a-3", true, false);
    SemVer v1234 = createVersion("1.0.0-1234", true, false);
    SemVer v911 = createVersion("1.0.0-911", true, false);
    assertEquals(1, v2a3a.compareTo(v2a3));
    assertEquals(1, v2a3.compareTo(v1234));
    assertEquals(1, v1234.compareTo(v911));
    assertEquals(-1, v911.compareTo(v2a3a));
  }

  @Test
  public void testInvalid() {
    invalidVersion("", "missing major version: ");
    invalidVersion("x", "missing major version: x");
    invalidVersion("x.y", "missing major version: x.y");
    invalidVersion("1.y", "missing version component");
    invalidVersion("1.", "missing version component");
    invalidVersion("1-", "missing pre-release version component");
    invalidVersion("1-2.", "missing pre-release version component");
    invalidVersion("1-2.3.", "missing pre-release version component");
    invalidVersion("1-123snapshot.", "missing pre-release version component");
    invalidVersion("1x+", "invalid semver: 1x+");
    invalidVersion("1234567890123456789", "at most 18 digits for numeric component");
  }
}
