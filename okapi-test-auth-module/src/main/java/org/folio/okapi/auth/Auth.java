package org.folio.okapi.auth;

import static org.folio.okapi.common.HttpResponse.responseError;
import static org.folio.okapi.common.HttpResponse.responseJson;
import static org.folio.okapi.common.HttpResponse.responseText;

import java.util.Base64;
import java.util.HashMap;

import org.folio.okapi.common.XOkapiHeaders;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * A dummy auth module. Provides a minimal authentication mechanism.
 * Mostly for testing Okapi itself.
 *
 * Does generate tokens for module permissions, but otherwise does not
 * check permissions for anything, but does return X-Okapi-Permissions-Desired
 * in X-Okapi-Permissions, as if all desired permissions were granted.
 *
 * TODO - we could do more trickery with -Required
 *
 * @author heikki
 *
 *
 *
 */
public class Auth {

  private final Logger logger = LoggerFactory.getLogger("okapi-test-auth-module");

  /**
   * Calculate a token from tenant and username. Fakes a JWT token, almost
   * but not quite completely unlike the one that a real auth module would create.
   * The important point is that it is a JWT, and that it has a tenant in it,
   * so Okapi can recover it in case it does not see a X-Okapi-Tenant header,
   * as happens in some unit tests.
   *
   * @param tenant
   * @param user
   * @return the token
   */
  private String token(String tenant, String user)  {

    // Create a dummy JWT token with the correct tenant
    JsonObject payload = new JsonObject()
                .put("sub", user)
                .put("tenant", tenant);
    String encodedpl = payload.encode();
    logger.debug("test-auth: payload: " + encodedpl );
    byte[] bytes = encodedpl.getBytes();
    byte[] pl64bytes = Base64.getEncoder().encode(bytes);
    String pl64 = new String(pl64bytes);
    String token = "dummyJwt." + pl64 + ".sig";
    logger.debug("test-auth: token: " + token);
    return token;
  }

  public void login(RoutingContext ctx) {
    final String json = ctx.getBodyAsString();
    if (json.length() == 0) {
      logger.debug("test-auth: accept OK in login");
      responseText(ctx, 202).end("Auth accept in /authn/login");
      return;
    }
    LoginParameters p;
    try {
      p = Json.decodeValue(json, LoginParameters.class);
    } catch (DecodeException ex) {
      responseText(ctx, 400).end("Error in decoding parameters: " + ex);
      return;
    }

    // Simple password validation: "peter" has a password "peter-password", etc.
    String u = p.getUsername();
    String correctpw = u + "-password";
    if (!p.getPassword().equals(correctpw)) {
      logger.warn("test-auth: Bad passwd for '" + u + "'. "
              + "Got '" + p.getPassword() + "' expected '" + correctpw + "'");
      responseText(ctx, 401).end("Wrong username or password");
      return;
    }
    String tok;
    tok = token(p.getTenant(), p.getUsername());
    logger.info("test-auth: Ok login for " + u + ": " + tok);
    responseJson(ctx, 200).putHeader(XOkapiHeaders.TOKEN, tok).end(json);
  }


  /**
   * Fake some module permissions.
   * Generates silly tokens with the module name as the tenant, and a list
   * of permissions as the user. These are still valid tokens, although it is
   * not possible to extract the user or tenant from them.
   */
  private String moduleTokens(RoutingContext ctx) {
    String modPermJson = ctx.request().getHeader(XOkapiHeaders.MODULE_PERMISSIONS);
    logger.debug("test-auth: moduleTokens: trying to decode '" + modPermJson + "'");
    HashMap<String, String> tokens = new HashMap<>();
    if (modPermJson != null && !modPermJson.isEmpty()) {
      JsonObject jo = new JsonObject(modPermJson);
      String permstr = "";
      for (String mod : jo.fieldNames()) {
        JsonArray ja = jo.getJsonArray(mod);
        for ( int i = 0; i < ja.size(); i++) {
          String p = ja.getString(i);
          if (! permstr.isEmpty() )
            permstr += ",";
          permstr += p;
          }
        String tok = token(mod, permstr);
        tokens.put(mod, tok);
      }
    }
    if (!tokens.isEmpty()) { // return also a 'clean' token
      tokens.put("_", ctx.request().getHeader(XOkapiHeaders.TOKEN));
    }
    String alltokens = Json.encode(tokens);
    logger.debug("test-auth: module tokens for " + modPermJson + "  :  " + alltokens);
    return alltokens;
  }

  public void check(RoutingContext ctx) {
    String tok = ctx.request().getHeader(XOkapiHeaders.TOKEN);
    if (tok == null || tok.isEmpty()) {
      logger.warn("test-auth: check called without " + XOkapiHeaders.TOKEN);
      responseText(ctx, 401)
              .end("Auth.check called without " + XOkapiHeaders.TOKEN);
      return;
    }
    String tenant = ctx.request().getHeader(XOkapiHeaders.TENANT);
    if (tenant == null || tenant.isEmpty()) {
      responseText(ctx, 401)
              .end("Auth.check called without " + XOkapiHeaders.TENANT);
      return;
    }
    logger.debug("test-auth: check starting with tok " + tok + " and tenant " + tenant);

    String[] splitTok = tok.split("\\.");
    logger.debug("test-auth: check: split the jwt into " + splitTok.length
        + ": " + Json.encode(splitTok));
    if ( splitTok.length != 3) {
      logger.warn("test-auth: Bad JWT, can not split in three parts. '" + tok + "'");
      responseError(ctx, 400, "Auth.check: Bad JWT");
      return;
    }

    if (!"dummyJwt".equals(splitTok[0])) {
      logger.warn("test-auth: Bad dummy JWT, starts with '" + splitTok[0] + "', not 'dummyJwt'");
      responseError(ctx, 400, "Auth.check needs a dummyJwt");
      return;
    }
    String payload = splitTok[1];

    String decodedJson = new String(Base64.getDecoder().decode(payload));
    logger.debug("test-auth: check payload: " + decodedJson);

    // Fake some desired permissions
    String des = ctx.request().getHeader(XOkapiHeaders.PERMISSIONS_DESIRED);
    if ( des != null && ! des.isEmpty()) {
    ctx.response().headers()
      .add(XOkapiHeaders.PERMISSIONS, des);
    }
    // Fake some module tokens
    String modTok = moduleTokens(ctx);
    ctx.response().headers()
      .add(XOkapiHeaders.TOKEN, tok)
      .add(XOkapiHeaders.MODULE_TOKENS, modTok);
    responseText(ctx, 202); // Abusing 202 to say check OK
    echo(ctx);
  }

  private void echo(RoutingContext ctx) {
    ctx.response().setChunked(true);
    // todo: content-type copy from request?
    ctx.request().handler(x -> {
      ctx.response().write(x); // echo content
    });
    ctx.request().endHandler(x -> {
      ctx.response().end();
    });
  }

  /**
   * Accept a request. Gets called with anything else than a POST to "/authn/login".
   * These need to be accepted, so we can do a pre-check before the proper POST.
   *
   * @param ctx
   */
  public void accept(RoutingContext ctx) {
    logger.info("test-auth: Auth accept OK");
    responseText(ctx, 202);
    echo(ctx);
  }
}
