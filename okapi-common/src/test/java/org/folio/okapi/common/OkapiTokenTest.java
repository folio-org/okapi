package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Base64;

public class OkapiTokenTest {

  @Test
  public void test() {
    OkapiToken t = new OkapiToken();

    JsonObject o = new JsonObject();
    o.put("tenant", "test-lib");
    o.put("foo", "bar");
    String s = o.encodePrettily();
    byte[] encodedBytes = Base64.getEncoder().encode(s.getBytes());
    String e = new String(encodedBytes);
    String tokenStr = "method." + e + ".trail";
    t.setToken(tokenStr);
    assertEquals("test-lib", t.getTenant());
  }

  @Test
  public void test2() {
    String s = "eyJzdWIiOiJfX3VuZGVmaW5lZF9fIiwidXNlcl9pZCI6Ijk5OTk5OTk5L"
      + "Tk5OTktNDk5OS05OTk5LTk5OTk5OTk5OTk5OSIsInRlbmFudCI6InRlc3RsaWIifQ";
    byte[] buf = Base64.getDecoder().decode(s);
    String got = new String(buf);
    String exp = "{\"sub\":\"__undefined__\","
      + "\"user_id\":\"99999999-9999-4999-9999-999999999999\",\"tenant\":\"testlib\"}";
    assertEquals(exp, got);
  }

  @Test
  public void test3() {
    OkapiToken tok = new OkapiToken();
    tok.setToken(null);
    assertEquals(null, tok.getTenant());

    Boolean ex;

    ex = false;
    tok.setToken("");
    try {
      String v = tok.getTenant();
    } catch (IllegalArgumentException e) {
      ex = true;
    }
    assertTrue(ex);

    ex = false;
    tok.setToken("a");
    try {
      String v = tok.getTenant();
    } catch (IllegalArgumentException e) {
      ex = true;
    }
    assertTrue(ex);

    ex = false;
    tok.setToken("a.b");
    try {
      String v = tok.getTenant();
    } catch (IllegalArgumentException e) {
      ex = true;
    }
    assertTrue(ex);

    ex = false;
    tok.setToken("a.b.c");
    try {
      String v = tok.getTenant();
    } catch (IllegalArgumentException e) {
      ex = true;
    }
    assertTrue(ex);

    ex = false;
    tok.setToken("a.ewo=.c");
    try {
      String v = tok.getTenant();
    } catch (IllegalArgumentException e) {
      ex = true;
    }
    assertTrue(ex);

    ex = false;
    tok.setToken("a.eyB9Cg==.c"); // "{ }"
    try {
      String v = tok.getTenant();
      assertEquals(null, v);
    } catch (IllegalArgumentException e) {
      ex = true;
    }
    assertFalse(ex);
  }
}
