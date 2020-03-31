package org.folio.okapi.common;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import java.util.Base64;

/**
 * The Okapi security token.
 * This class implements some methods to extract some pieces of information out
 * of the security token. In theory the token is private to the auth subsystem,
 * but occasionally a module or even Okapi itself may need to extract the current
 * tenant-id, or some other piece of information.
 */
public class OkapiToken {
  private String token;

  /**
   * Construct from token string.
   * Note that there is no JWT validation taking place.
   */
  public OkapiToken(String token) {
    this.token = token;
  }

  private JsonObject getPayloadWithoutValidation() {
    int idx1 = token.indexOf('.');
    if (idx1 == -1) {
      throw new IllegalArgumentException("Missing . separator for token");
    }
    idx1++;
    int idx2 = token.indexOf('.', idx1);
    if (idx2 == -1) {
      throw new IllegalArgumentException("Missing . separator for token");
    }
    String encodedJson = token.substring(idx1, idx2);
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
   * Note there is no JWT validation taking place.
   * @return null if no token, or no tenant there
   */
  public String getTenant() {
    if (token == null) {
      return null;
    }
    JsonObject pl = this.getPayloadWithoutValidation();
    return pl.getString("tenant");
  }
}
