package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;
import org.junit.Test;
import org.junit.Assert;
import java.util.Base64;


public class OkapiTokenTest {

  @Test
  public void test() {
    JsonObject o = new JsonObject();
    o.put("tenant", "test-lib");
    o.put("foo", "bar");
    String s = o.encodePrettily();
    byte[] encodedBytes = Base64.getEncoder().encode(s.getBytes());
    String e = new String(encodedBytes);
    String tokenStr = "method." + e + ".trail";
    OkapiToken tok = new OkapiToken(tokenStr);
    Assert.assertEquals("test-lib", tok.getTenant());
  }

  @Test
  public void noTenant() {
    OkapiToken tok = new OkapiToken("a.eyB9Cg==.c"); // "{ }"
    Assert.assertNull(tok.getTenant());
  }

  @Test
  public void testExceptions() {
    OkapiToken tok = new OkapiToken(null);
    Assert.assertEquals(null, tok.getTenant());

    String msg = null;
    tok = new OkapiToken("");
    try {
      String v = tok.getTenant();
    } catch (IllegalArgumentException e) {
      msg = e.getMessage();
    }
    Assert.assertEquals("Missing . separator for token", msg);

    msg = null;
    tok = new OkapiToken("a");
    try {
      String v = tok.getTenant();
    } catch (IllegalArgumentException e) {
      msg = e.getMessage();
    }
    Assert.assertEquals("Missing . separator for token", msg);

    msg = null;
    tok = new OkapiToken("a.b");
    try {
      String v = tok.getTenant();
    } catch (IllegalArgumentException e) {
      msg = e.getMessage();
    }
    Assert.assertEquals("Missing . separator for token", msg);

    msg = null;
    tok = new OkapiToken("a.b.c");
    try {
      String v = tok.getTenant();
    } catch (IllegalArgumentException e) {
      msg = e.getMessage();
    }
    Assert.assertEquals("Input byte[] should at least have 2 bytes for base64 bytes", msg);

    msg = null;
    tok = new OkapiToken("a.ewo=.c");
    try {
      String v = tok.getTenant();
    } catch (IllegalArgumentException e) {
      msg = e.getMessage();
    }
    Assert.assertTrue(msg.contains("Unexpected end-of-input"));

    msg = null;
    tok = new OkapiToken("a.eyB9Cg==.c"); // "{ }"
    try {
      String v = tok.getTenant();
      Assert.assertEquals(null, v);
    } catch (IllegalArgumentException e) {
      msg = e.getMessage();
    }
    Assert.assertNull(msg);
  }
}
