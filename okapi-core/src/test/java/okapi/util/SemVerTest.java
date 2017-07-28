package okapi.util;

import org.folio.okapi.util.SemVer;
import org.junit.Test;
import static org.junit.Assert.*;

public class SemVerTest {

  public SemVerTest() {
  }

  @Test
  public void test() {
    SemVer v1 = new SemVer("1");

    System.out.println(v1.toString());
    assertEquals("version: 1", v1.toString());

    SemVer v2 = new SemVer("2");
    assertEquals("version: 2", v2.toString());

    assertEquals(v1.compareTo(v2), -3);
    assertEquals(v2.compareTo(v1), 3);
    assertEquals(v1.compareTo(v1), 0);
    assertEquals(v2.compareTo(v2), 0);

    SemVer v1_2 = new SemVer("1.2");
    assertEquals("version: 1 2", v1_2.toString());

    assertEquals(v1_2.compareTo(v1), 2);
    assertEquals(v1.compareTo(v1_2), -2);
    assertEquals(v2.compareTo(v1_2), 3);

    SemVer v1_5 = new SemVer("1.5.0");
    assertEquals("version: 1 5 0", v1_5.toString());
    SemVer v1_10 = new SemVer("1.10.0");
    assertEquals("version: 1 10 0", v1_10.toString());
    assertEquals(v1_10.compareTo(v1_5), 2);
    assertEquals(v1_5.compareTo(v1_10), -2);

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

    assertEquals(p1.compareTo(p2), -1);
    assertEquals(p2.compareTo(p3), -1);
    assertEquals(p3.compareTo(p4), -1);
    assertEquals(p4.compareTo(p5), -1);
    assertEquals(p5.compareTo(p6), -1);
    assertEquals(p6.compareTo(p7), -1);
    assertEquals(p7.compareTo(p8), -1);

    assertEquals(p2.compareTo(p1), 1);
    assertEquals(p3.compareTo(p2), 1);
    assertEquals(p4.compareTo(p3), 1);
    assertEquals(p5.compareTo(p4), 1);
    assertEquals(p6.compareTo(p5), 1);
    assertEquals(p7.compareTo(p6), 1);
    assertEquals(p8.compareTo(p7), 1);

    SemVer snap1 = new SemVer("1.0.0-rc.1+snapshot-2017.1");
    assertEquals("version: 1 0 0 pre: rc 1 metadata: snapshot-2017.1", snap1.toString());

    SemVer snap2 = new SemVer("1.0.0-rc.1+snapshot-2017.2");
    assertEquals("version: 1 0 0 pre: rc 1 metadata: snapshot-2017.2", snap2.toString());

    SemVer snap3 = new SemVer("1.0.0+snapshot-2017.2");
    assertEquals("version: 1 0 0 metadata: snapshot-2017.2", snap3.toString());

    assertEquals(snap1.compareTo(snap2), -1);
    assertEquals(snap2.compareTo(snap1), 1);
    assertEquals(snap3.compareTo(snap1), 1);
    assertEquals(snap1.compareTo(snap3), -1);
  }
}
