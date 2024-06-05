package org.folio.okapi.common;

import static org.junit.Assert.assertNull;

import io.vertx.core.json.JsonObject;
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
    Assert.assertEquals(o.encode(), tok.getPayloadWithoutValidation().encode());
    Assert.assertEquals(TENANT, tok.getTenantWithoutValidation());
    Assert.assertEquals(USERNAME, tok.getUsernameWithoutValidation());
    Assert.assertEquals(USERID, tok.getUserIdWithoutValidation());
  }

  @Test
  public void testNullToken() {
    OkapiToken tok = new OkapiToken(null);
    assertNull(tok.getPayloadWithoutValidation());
    assertNull(tok.getTenantWithoutValidation());
    assertNull(tok.getUsernameWithoutValidation());
    assertNull(tok.getUserIdWithoutValidation());
  }

  @Test
  @SuppressWarnings("java:S125")  // false positive: "Sections of code should not be commented out"
  public void noTenant() {
    OkapiToken tok = new OkapiToken("a.eyB9Cg==.c"); // { }
    Assert.assertNull(tok.getTenantWithoutValidation());
    Assert.assertEquals("{}", tok.getPayloadWithoutValidation().encode());
  }

  @Test
  @SuppressWarnings("java:S125")  // false positive: "Sections of code should not be commented out"
  public void usernameWithUmlaut() {
    var token = new OkapiToken("x.eyJzdWIiOiJmb2_DpCJ9.x"); // {"sub":"fooä"}
    Assert.assertEquals("fooä", token.getUsernameWithoutValidation());
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
