package org.folio.okapi.bean;

import io.vertx.core.json.DecodeException;
import org.junit.Test;

import static org.junit.Assert.*;

@java.lang.SuppressWarnings({"squid:S1166", "squid:S1192"})
public class RoutingEntryTest {
  @Test
  public void test1() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[2];
    methods[0] = "HEAD";
    methods[1] = "GET";

    t.setMethods(methods);

    t.setPath("/a");
    assertTrue(t.match(null, "GET"));
    assertFalse(t.match("/", "GET"));
    assertFalse(t.match("/a", "POST"));
    assertTrue(t.match("/a", "GET"));
    assertTrue(t.match("/a", "HEAD"));
    assertTrue(t.match("/a/", "GET"));
    assertFalse(t.match("", "GET"));
    assertFalse(t.match("/?query", "GET"));
    assertFalse(t.match("/#x", "GET"));

    t.setPathPattern("/");
    assertTrue(t.match("/", "GET"));
    assertFalse(t.match("/", "POST"));
    assertFalse(t.match("/a", "GET"));
    assertFalse(t.match("/a/", "GET"));
    assertFalse(t.match("", "GET"));
    assertTrue(t.match("/?query", "GET"));
    assertTrue(t.match("/#x", "GET"));

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
  public void test2() {
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
  public void test3() {
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
