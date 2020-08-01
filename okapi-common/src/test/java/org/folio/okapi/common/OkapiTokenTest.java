package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;

import static org.junit.Assert.assertNull;

import java.util.Base64;
import org.junit.Assert;
import org.junit.Test;


public class OkapiTokenTest {

  private static final String TENANT = "test-lib";
  private static final String USERNAME = "tenant_user_id";
  private static final String USERID = "a9b62db1-f313-48eb-a64c-68a6f2b7fe36";

  @Test
  public void test() {
    JsonObject o = new JsonObject();
    o.put("tenant", TENANT);
    o.put("sub", USERNAME);
    o.put("user_id", USERID);
    o.put("foo", "bar");
    String s = o.encodePrettily();
    byte[] encodedBytes = Base64.getEncoder().encode(s.getBytes());
    String e = new String(encodedBytes);
    String tokenStr = "method." + e + ".trail";
    OkapiToken tok = new OkapiToken(tokenStr);
    Assert.assertEquals(TENANT, tok.getTenant());
    Assert.assertEquals(USERNAME, tok.getUsername());
    Assert.assertEquals(USERID, tok.getUserId());
  }

  @Test
  public void testNullToken() {
    OkapiToken tok = new OkapiToken(null);
    assertNull(tok.getTenant());
    assertNull(tok.getUsername());
    assertNull(tok.getUserId());
  }

  @Test
  public void noTenant() {
    OkapiToken tok = new OkapiToken("a.eyB9Cg==.c"); // "{ }"
    Assert.assertNull(tok.getTenant());
  }

  private String exceptionMessage(String token) {
    Exception e = Assert.assertThrows(
        IllegalArgumentException.class,
        () -> new OkapiToken(token));
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
