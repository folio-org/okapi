package org.folio.okapi.bean;

import io.vertx.core.json.DecodeException;

import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.util.ProxyContext;
import org.junit.Test;
import org.mockito.Mockito;

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

  @Test
  public void testPathPatternGlob() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);
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

  @Test
  public void testPathPatternId() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);

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

  @Test
  public void testPermissionsRequired() {
    ProxyContext pc = Mockito.mock(ProxyContext.class);
    RoutingEntry t = new RoutingEntry();
    String mod = "test";
    String perm = "user.read";
    String expected = "missing field permissionsRequired";

    assertTrue(t.validateHandlers(pc, mod).contains(expected));
    assertTrue(t.validateFilters(pc, mod).contains(expected));

    t.setPermissionsRequired(new String[] {});
    assertFalse(t.validateHandlers(pc, mod).contains(expected));
    assertFalse(t.validateFilters(pc, mod).contains(expected));

    t.setPermissionsRequired(new String[] { perm });
    assertFalse(t.validateHandlers(pc, mod).contains(expected));
    assertFalse(t.validateFilters(pc, mod).contains(expected));
  }
}
