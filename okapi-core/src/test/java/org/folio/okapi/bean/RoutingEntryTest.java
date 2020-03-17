package org.folio.okapi.bean;

import io.vertx.core.json.DecodeException;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.junit.Test;

import static org.junit.Assert.*;

@java.lang.SuppressWarnings({"squid:S1166", "squid:S1192"})
public class RoutingEntryTest {
  private final Logger logger = OkapiLogger.get();

  @Test
  public void testMethods() {
    RoutingEntry t = new RoutingEntry();

    t.setPath("/a");
    assertFalse(t.match("/a", "GET"));

    String[] methods = new String[2];
    methods[0] = "HEAD";
    methods[1] = "GET";
    t.setMethods(methods);
    assertTrue(t.match("/a", "GET"));
    assertTrue(t.match("/a", "HEAD"));
    assertFalse(t.match("/a", "POST"));
    assertTrue(t.match("/a", null));

    methods[0] = "HEAD";
    methods[1] = "*";
    t.setMethods(methods);
    assertTrue(t.match("/a", "GET"));
    assertTrue(t.match("/a", "HEAD"));
    assertTrue(t.match("/a", "POST"));
  }

  @Test
  public void testPath() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";

    t.setMethods(methods);
    assertTrue(t.match("/a", "GET"));
    assertFalse(t.match("/a", "POST"));

    t.setPath("/a");
    assertTrue(t.match(null, "GET"));
    assertFalse(t.match("/", "GET"));
    assertTrue(t.match("/a", "GET"));
    assertTrue(t.match("/a/", "GET"));
    assertFalse(t.match("", "GET"));
    assertFalse(t.match("/?query", "GET"));
    assertFalse(t.match("/#x", "GET"));
  }

  @Test
    public void testPathPatternSimple() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);

    for (int i = 0; i < 2; i++) {
      t.enableFastMatch = i != 0;
      t.setPathPattern("/");
      assertTrue(t.match(null, "GET"));
      assertTrue(t.match("/", "GET"));
      assertFalse(t.match("/", "POST"));
      assertFalse(t.match("/a", "GET"));
      assertFalse(t.match("/a/", "GET"));
      assertFalse(t.match("", "GET"));
      assertTrue(t.match("/?query", "GET"));
      assertTrue(t.match("/#x", "GET"));
    }
  }

  @Test
  public void testPathPatternGlob() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);

    for (int i = 0; i < 2; i++) {
      t.enableFastMatch = i != 0;
      t.setPathPattern("/*");

      assertTrue(t.match("/", "GET"));
      assertFalse(t.match("/", "POST"));
      assertTrue(t.match("/a", "GET"));
      assertTrue(t.match("/a/", "GET"));
      assertFalse(t.match("", "GET"));
      assertTrue(t.match("/?query", "GET"));
      assertTrue(t.match("/#x", "GET"));

      t.setPathPattern("/*/a");

      assertFalse(t.match("/", "GET"));
      assertFalse(t.match("/", "POST"));
      assertFalse(t.match("/a", "GET"));
      assertFalse(t.match("/a/", "GET"));
      assertFalse(t.match("", "GET"));
      assertFalse(t.match("/?query", "GET"));
      assertFalse(t.match("/#x", "GET"));
      assertTrue(t.match("/b/a", "GET"));
      assertTrue(t.match("/c/b/a", "GET"));
    }
  }

  @Test
  public void testPathPatternId() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);

    for (int i = 0; i < 2; i++) {
      t.enableFastMatch = i != 0;

      t.setPathPattern("/a/{id}");
      assertFalse(t.match("/", "GET"));
      assertFalse(t.match("/", "POST"));
      assertFalse(t.match("/a", "GET"));
      assertFalse(t.match("/a/", "GET"));
      assertFalse(t.match("", "GET"));
      assertFalse(t.match("/?query", "GET"));
      assertFalse(t.match("/#x", "GET"));
      assertTrue(t.match("/a/b", "GET"));
      assertTrue(t.match("/a/0-9", "GET"));
      assertTrue(t.match("/a/0-9?a=1", "GET"));
      assertTrue(t.match("/a/0-9#a=1", "GET"));
      assertFalse(t.match("/a/b/", "GET"));
      assertFalse(t.match("/a/b/", "GET"));
      assertFalse(t.match("/a/b/c", "GET"));

      t.setPathPattern("/a/{id}/c");
      assertFalse(t.match("/", "GET"));
      assertFalse(t.match("/", "POST"));
      assertFalse(t.match("/a", "GET"));
      assertFalse(t.match("/a/", "GET"));
      assertFalse(t.match("", "GET"));
      assertFalse(t.match("/?query", "GET"));
      assertFalse(t.match("/#x", "GET"));
      assertFalse(t.match("/a/b", "GET"));
      assertFalse(t.match("/a/0-9", "GET"));
      assertFalse(t.match("/a/b/", "GET"));
      assertFalse(t.match("/a/b/", "GET"));
      assertTrue(t.match("/a/b/c", "GET"));
    }
  }

  @Test
  public void testFastMatch() {
    assertTrue(RoutingEntry.fastMatch("{id}/", "a/"));
    assertFalse(RoutingEntry.fastMatch("{/", "a/"));
    assertTrue(RoutingEntry.fastMatch("{", "a"));
    assertTrue(RoutingEntry.fastMatch("{}/", "a/"));
    assertFalse(RoutingEntry.fastMatch("{id}/", "/"));
    assertFalse(RoutingEntry.fastMatch("/{id}/", "//"));
    assertTrue(RoutingEntry.fastMatch("/{id}/", "/a/"));
    assertTrue(RoutingEntry.fastMatch("{id}/", "a/#a?"));
    assertTrue(RoutingEntry.fastMatch("{id}/", "a/?a#"));
    assertFalse(RoutingEntry.fastMatch("{id}/", "a#/"));
    assertFalse(RoutingEntry.fastMatch("{id}/", "a?/"));
    assertTrue(RoutingEntry.fastMatch("{ud}/", "a/?a"));
    assertTrue(RoutingEntry.fastMatch("{id1}/*/{id2}", "a/b/c/d"));
    assertTrue(RoutingEntry.fastMatch("{id1}/*/{id2}", "a//d"));
    assertFalse(RoutingEntry.fastMatch("{id1}/*/{id2}", "a/d"));
    assertFalse(RoutingEntry.fastMatch("{id1}/*/{id2}", "//"));
  }

  @Test
  public void testPerformance() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);

    t.setPathPattern("/inventory-storage/instances/{id}");

    long t1, t2;
    t.enableFastMatch = true;
    t1 = testPerfmanceOne(t);
    t.enableFastMatch = false;
    t2 = testPerfmanceOne(t);
    logger.info("comp tfast: {} ms tregular: {} ms", t1, t2);

    t.setPathPattern("/inventory-*/instances/{id}");
    t.enableFastMatch = true;
    t1 = testPerfmanceOne(t);
    t.enableFastMatch = false;
    t2 = testPerfmanceOne(t);
    logger.info("star+comp tfast: {} ms tregular: {} ms", t1, t2);
  }

  private long testPerfmanceOne(RoutingEntry t) {
    final int it = 10000;

    int count = -1;
    if (t.match("/inventory-storage/instances/123", "GET")) {
      count++;
    }
    long l = System.nanoTime();
    for (int i = 0; i < it; i++) {
      if (t.match("/inventory-storage/instances/123", "GET")) {
        count++;
      }
    }
    long msec = (System.nanoTime() - l) / 1000000;
    assertEquals(it, count);
    return msec;
  }

  @Test
  public void testInvalidPatterns() {
    RoutingEntry t = new RoutingEntry();
    boolean caught = false;
    try {
      t.setPathPattern("/a{a{");
    } catch (DecodeException e) {
      caught = true;
    }
    assertTrue(caught);

    caught = false;
    try {
      t.setPathPattern("/a{");
    } catch (DecodeException e) {
      caught = true;
    }
    assertTrue(caught);

    caught = false;
    try {
      t.setPathPattern("/?a=b");
    } catch (DecodeException e) {
      caught = true;
    }
    assertTrue(caught);

    caught = false;
    try {
      t.setPathPattern("/a.b");
    } catch (DecodeException e) {
      caught = true;
    }
    assertTrue(caught);

    caught = false;
    try {
      t.setPathPattern("/a\\b");
    } catch (DecodeException e) {
      caught = true;
    }
    assertTrue(caught);

    caught = false;
    try {
      t.setPathPattern("/a{:}b");
    } catch (DecodeException e) {
      caught = true;
    }
    assertTrue(caught);

    caught = false;
    try {
      t.setPathPattern("/a{}b");
    } catch (DecodeException e) {
      caught = true;
    }
    assertFalse(caught);
  }

  @Test
  public void testRedirectPath() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);
    t.setPathPattern("/req");
    t.setRedirectPath("/res");
    assertTrue(t.match("/req", "GET"));
    assertEquals("/res", t.getRedirectUri("/req"));
    assertEquals("/res?abc", t.getRedirectUri("/req?abc"));
    assertEquals("/res?abc#a", t.getRedirectUri("/req?abc#a"));
    assertEquals("/res#a", t.getRedirectUri("/req#a"));

    t.setPathPattern("/req/{id}");
    t.setRedirectPath("/res/1234");
    assertTrue(t.match("/req/2", "GET"));
    assertEquals("/res/1234", t.getRedirectUri("/req/2"));

    t.setPathPattern("/req/{id}/bongo");
    t.setRedirectPath("/res/1234/a/b");
    assertTrue(t.match("/req/q/bongo", "GET"));
    assertEquals("/res/1234/a/b", t.getRedirectUri("/req/2/bongo"));

    t.setPathPattern("/req/*/s");
    t.setRedirectPath("/res/1234");
    assertTrue(t.match("/req/a/s", "GET"));
    assertEquals("/res/1234", t.getRedirectUri("/req/a/s"));

  }

  @Test
  public void testRewritePath() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "*";
    t.setMethods(methods);
    t.setPathPattern("/*");
    t.setRewritePath("/events");
    assertTrue(t.match("/", "GET"));
    assertEquals("/events", t.getRewritePath());
  }
}
