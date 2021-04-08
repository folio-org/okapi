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
  private final String token;
  private final JsonObject payloadWithoutValidation;

  /**
   * Construct from token string.
   * Note that there is no JWT validation taking place.
   */
  public OkapiToken(String token) {
    this.token = token;
    payloadWithoutValidation = this.getPayloadWithoutValidation();
  }

  @Override
  public String toString() {
    return token;
  }

  private JsonObject getPayloadWithoutValidation() {
    if (token == null) {
      return null;
    }
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
    try {
      return new JsonObject(decodedJson);
    } catch (DecodeException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  private String getFieldFromTokenWithoutValidation(String field) {
    if (payloadWithoutValidation == null) {
      return null;
    }
    return payloadWithoutValidation.getString(field);
  }

  /**
   * Get the tenant out from the token.
   * Note there is no JWT validation taking place.
   * @return null if no token, or no tenant there
   */
  public String getTenantWithoutValidation() {
    return getFieldFromTokenWithoutValidation("tenant");
  }

  /**
   * Get the user name out from the token.
   * Note there is no JWT validation taking place.
   * @return null if no token, or no tenant there
   */
  public String getUsernameWithoutValidation() {
    return getFieldFromTokenWithoutValidation("sub");
  }

  /**
   * Get the user id out from the token.
   * Note there is no JWT validation taking place.
   * @return null if no token, or no tenant there
   */
  public String getUserIdWithoutValidation() {
    return getFieldFromTokenWithoutValidation("user_id");
  }
}
