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
}
