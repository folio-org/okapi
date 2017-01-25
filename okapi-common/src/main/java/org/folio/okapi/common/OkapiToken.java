package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.util.Base64;

/**
 * The Okapi security token.
 * This class implements some methods to extract some pieces of information out
 * of the security token. In theory the token is private to the auth subsystem,
 * but occasionally a module or even Okapi itself may need to extract the current
 * tenant-id, or some other piece of information.
 */
public class OkapiToken {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private String token;

  public OkapiToken() {
    this.token = null;
  }

  public OkapiToken(RoutingContext ctx) {
   this.token = this.readHeader(ctx);

  }

  public void SetToken(String token) {
    this.token = token;
  }



  /**
   * Read the X-Okapi-Token header from ctx.
   *
   *
   * @param ctx Routing context, with the headers
   * @return the value of the header, or null if not found
   */
  private String readHeader(RoutingContext ctx) {
    String tok = ctx.request().getHeader(XOkapiHeaders.TOKEN);
    if (tok != null && ! tok.isEmpty()) {
      return tok;
    }
    return null;
  }


  private JsonObject getPayload() {
    String encodedJson = this.token.split("\\.")[1];
    if ( encodedJson == null || encodedJson.isEmpty())
      return null;
    String decodedJson = new String( Base64.getDecoder().decode(encodedJson) );
    return new JsonObject(decodedJson);
  }

  /**
   * Get the tenant out from the token.
   * @return null if no token, or no tenant there
   */
  public String getTenant() {
    if ( this.token == null || this.token.isEmpty())
      return null;
    JsonObject pl = this.getPayload();
    if ( pl == null )
      return null;
    String tenant = pl.getString("tenant");
    return tenant;
  }

}
