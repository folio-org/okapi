package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;
import java.util.Base64;
import org.junit.Assert;
import org.junit.Test;


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
  public void testNull() {
    OkapiToken tok = new OkapiToken(null);
    Assert.assertEquals(null, tok.getTenant());
  }

  private String exceptionMessage(String token) {
    Exception e = Assert.assertThrows(
        IllegalArgumentException.class,
        () -> new OkapiToken(token).getTenant());
    return e.getMessage();
  }

  @Test
  public void emptyTokenException() {
    Assert.assertEquals("Missing . separator for token", exceptionMessage(""));
  }

  @Test
  public void noDotException() {
    Assert.assertEquals("Missing . separator for token", exceptionMessage("a"));
  }

  @Test
  public void oneDotException() {
    Assert.assertEquals("Missing . separator for token", exceptionMessage("a.b"));
  }

  @Test
  public void singleByteException() {
    Assert.assertEquals("Input byte[] should at least have 2 bytes for base64 bytes",
        exceptionMessage("a.b.c"));
  }

  @Test
  public void endOfInputException() {
    Assert.assertTrue(exceptionMessage("a.ewo=.c").contains("Unexpected end-of-input"));
  }
}
