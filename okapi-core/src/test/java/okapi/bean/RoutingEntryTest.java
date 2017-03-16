package okapi.bean;

import io.vertx.core.json.DecodeException;
import org.folio.okapi.bean.RoutingEntry;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoutingEntryTest {
  @Test
  public void test1() {
    RoutingEntry t = new RoutingEntry();
    String methods[] = new String[1];
    methods[0] = "GET";

    t.setPathPattern("/");
    t.setMethods(methods);
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
}
