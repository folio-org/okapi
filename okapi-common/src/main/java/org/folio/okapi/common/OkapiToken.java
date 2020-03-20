package org.folio.okapi.common;

import java.util.Base64;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * The Okapi security token.
 * This class implements some methods to extract some pieces of information out
 * of the security token. In theory the token is private to the auth subsystem,
 * but occasionally a module or even Okapi itself may need to extract the current
 * tenant-id, or some other piece of information.
 */
public class OkapiToken {
  private String token;

  public OkapiToken() {
    this.token = null;
  }

  public OkapiToken(RoutingContext ctx) {
    this.token = ctx.request().getHeader(XOkapiHeaders.TOKEN);
  }

  public void setToken(String token) {
    this.token = token;
  }

  private JsonObject getPayload() {
    String encodedJson;
    try {
      encodedJson = this.token.split("\\.")[1];
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
    String decodedJson = new String(Base64.getDecoder().decode(encodedJson));
    JsonObject j;
    try {
      j = new JsonObject(decodedJson);
    } catch (DecodeException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
    return j;
  }

  /**
   * Get the tenant out from the token.
   * @return null if no token, or no tenant there
   */
  public String getTenant() {
    if (token == null) {
      return null;
    }
    JsonObject pl = this.getPayload();
    return pl.getString("tenant");
  }
}
