package org.folio.okapi.common;

import org.junit.Test;
import static org.junit.Assert.*;

public class SemVerTest {
  @Test
  public void test() {
    SemVer v1 = new SemVer("1");

    assertEquals("version: 1", v1.toString());

    SemVer v2 = new SemVer("2");
    assertEquals("version: 2", v2.toString());

    assertEquals(-4, v1.compareTo(v2));
    assertEquals(4, v2.compareTo(v1));
    assertEquals(0, v1.compareTo(v1));
    assertEquals(0, v2.compareTo(v2));

    assertFalse(v1.hasPrefix(v2));
    assertFalse(v2.hasPrefix(v1));

    SemVer v1_0 = new SemVer("1.0");
    assertEquals("version: 1 0", v1_0.toString());

    SemVer v1_2 = new SemVer("1.2");
    assertEquals("version: 1 2", v1_2.toString());
    assertTrue(v1_2.hasPrefix(v1));
    assertFalse(v1_2.hasPrefix(v2));

    assertEquals(3, v1_2.compareTo(v1));
    assertEquals(-3, v1.compareTo(v1_2));
    assertEquals(4, v2.compareTo(v1_2));

    SemVer v1_5 = new SemVer("1.5.0");
    assertEquals("version: 1 5 0", v1_5.toString());
    SemVer v1_10 = new SemVer("1.10.0");
    assertEquals("version: 1 10 0", v1_10.toString());
    assertEquals(3, v1_10.compareTo(v1_5));
    assertEquals(-3, v1_5.compareTo(v1_10));
    assertFalse(v1_5.hasPrefix(v1_10));
    assertFalse(v1_10.hasPrefix(v1_5));

    SemVer p1 = new SemVer("1.0.0-alpha");
    assertEquals("version: 1 0 0 pre: alpha", p1.toString());
    SemVer p2 = new SemVer("1.0.0-alpha.1");
    assertEquals("version: 1 0 0 pre: alpha 1", p2.toString());
    SemVer p3 = new SemVer("1.0.0-alpha.beta");
    assertEquals("version: 1 0 0 pre: alpha beta", p3.toString());
    SemVer p4 = new SemVer("1.0.0-beta");
    assertEquals("version: 1 0 0 pre: beta", p4.toString());
    SemVer p5 = new SemVer("1.0.0-beta.2");
    assertEquals("version: 1 0 0 pre: beta 2", p5.toString());
    SemVer p6 = new SemVer("1.0.0-beta.11");
    assertEquals("version: 1 0 0 pre: beta 11", p6.toString());
    SemVer p7 = new SemVer("1.0.0-rc.1");
    assertEquals("version: 1 0 0 pre: rc 1", p7.toString());
    SemVer p8 = new SemVer("1.0.0");
    assertEquals("version: 1 0 0", p8.toString());
    assertFalse(p1.equals(p2));
    assertTrue(p1.equals(p1));
    SemVer p1Copy = new SemVer("1.0.0-alpha");
    assertTrue(p1.equals(p1Copy));
    assertFalse(p1.equals(this));

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

    SemVer snap1 = new SemVer("1.0.0-rc.1+snapshot-2017.1");
    assertEquals("version: 1 0 0 pre: rc 1 metadata: snapshot-2017.1", snap1.toString());

    SemVer snap2 = new SemVer("1.0.0-rc.1+snapshot-2017.2");
    assertEquals("version: 1 0 0 pre: rc 1 metadata: snapshot-2017.2", snap2.toString());

    assertFalse(snap1.hasPrefix(snap2));

    SemVer snap3 = new SemVer("1.0.0+snapshot-2017.2");
    assertEquals("version: 1 0 0 metadata: snapshot-2017.2", snap3.toString());

    assertTrue(snap1.hasPrefix(snap1));
    SemVer snap4 = new SemVer("1.0.0-rc.1");
    assertTrue(snap1.hasPrefix(snap4));
    assertFalse(snap4.hasPrefix(snap1));
    assertEquals(1, snap1.compareTo(snap4));
    assertEquals(-1, snap4.compareTo(snap1));

    assertEquals(-1, snap1.compareTo(snap2));
    assertEquals(1, snap2.compareTo(snap1));
    assertEquals(1, snap3.compareTo(snap1));
    assertEquals(-1, snap1.compareTo(snap3));

    boolean thrown;

    thrown = false;
    try {
      SemVer tmp = new SemVer("");
    } catch (IllegalArgumentException ex) {
      assertEquals("missing major version: ", ex.getMessage());
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      SemVer tmp = new SemVer("x");
    } catch (IllegalArgumentException ex) {
      assertEquals("missing major version: x", ex.getMessage());
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      SemVer tmp = new SemVer("x.y");
    } catch (IllegalArgumentException ex) {
      assertEquals("missing major version: x.y", ex.getMessage());
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      SemVer tmp = new SemVer("1.y");
    } catch (IllegalArgumentException ex) {
      assertEquals("missing version component", ex.getMessage());
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      SemVer tmp = new SemVer("1.");
    } catch (IllegalArgumentException ex) {
      assertEquals("missing version component", ex.getMessage());
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      SemVer tmp = new SemVer("1-");
    } catch (IllegalArgumentException ex) {
      assertEquals("missing pre-release version component", ex.getMessage());
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      SemVer tmp = new SemVer("1-2.");
    } catch (IllegalArgumentException ex) {
      assertEquals("missing pre-release version component", ex.getMessage());
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      SemVer tmp = new SemVer("1-2.3.");
    } catch (IllegalArgumentException ex) {
      assertEquals("missing pre-release version component", ex.getMessage());
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      SemVer tmp = new SemVer("1-123snapshot.");
    } catch (IllegalArgumentException ex) {
      assertEquals("missing pre-release version component", ex.getMessage());
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      SemVer tmp = new SemVer("1x+");
    } catch (IllegalArgumentException ex) {
      assertEquals("invalid semver: 1x+", ex.getMessage());
      thrown = true;
    }
    assertTrue(thrown);

  }
}
