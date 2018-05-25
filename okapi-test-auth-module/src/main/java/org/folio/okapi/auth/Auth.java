package org.folio.okapi.auth;

import io.vertx.core.http.HttpMethod;
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
import io.vertx.ext.web.RoutingContext;
import org.folio.okapi.common.OkapiLogger;

/**
 * A dummy auth module. Provides a minimal authentication mechanism.
 Mostly for
 * testing Okapi itself.
 *
 * Does generate tokens for module permissions, but otherwise does not filter
 * permissions for anything, but does return X-Okapi-Permissions-Desired in
 * X-Okapi-Permissions, as if all desired permissions were granted.
 *
 * @author heikki
 *
 *
 *
 */
class Auth {

  private final Logger logger = OkapiLogger.get();

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
      StringBuilder permstr = new StringBuilder();
      for (String mod : jo.fieldNames()) {
        JsonArray ja = jo.getJsonArray(mod);
        for (int i = 0; i < ja.size(); i++) {
          String p = ja.getString(i);
          if (permstr.length() > 0) {
            permstr.append(",");
          }
          permstr.append(p);
        }
        tokens.put(mod, token(mod, permstr.toString()));
      }
    }
    if (!tokens.isEmpty()) { // return also a 'clean' token
      tokens.put("_", ctx.request().getHeader(XOkapiHeaders.TOKEN));
    }
    String alltokens = Json.encode(tokens);
    logger.debug("test-auth: module tokens for " + modPermJson + "  :  " + alltokens);
    return alltokens;
  }

  public void filter(RoutingContext ctx) {
    String phase = ctx.request().headers().get(XOkapiHeaders.FILTER);
    logger.debug("test-auth filter " + XOkapiHeaders.FILTER + ": '" + phase + "'");
    if (phase == null || phase.startsWith("auth")) {
      check(ctx);
      return;
    }
    ctx.response().putHeader("X-Auth-Filter-Phase", phase);
    echo(ctx);
  }

  public void check(RoutingContext ctx) {
    String tenant = ctx.request().getHeader(XOkapiHeaders.TENANT);
    if (tenant == null || tenant.isEmpty()) {
      responseText(ctx, 401)
        .end("test-auth: check called without " + XOkapiHeaders.TENANT);
      return;
    }
    String userId = "?";
    String tok = ctx.request().getHeader(XOkapiHeaders.TOKEN);
    if (tok == null || tok.isEmpty()) {
      logger.warn("test-auth: check called without " + XOkapiHeaders.TOKEN);
      tok = token(tenant, "-"); // create a dummy token without username
    } else {
      logger.debug("test-auth: check starting with tok " + tok + " and tenant " + tenant);

      String[] splitTok = tok.split("\\.");
      logger.debug("test-auth: check: split the jwt into " + splitTok.length
        + ": " + Json.encode(splitTok));
      if (splitTok.length != 3) {
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

      try {
        String decodedJson = new String(Base64.getDecoder().decode(payload));
        logger.debug("test-auth: check payload: " + decodedJson);
        JsonObject jtok = new JsonObject(decodedJson);
        userId = jtok.getString("sub", "");

      } catch (IllegalArgumentException e) {
        responseError(ctx, 400, "Bad Json payload " + payload);
        return;
      }
    }

    // Fail a call to /_/tenant that requires permissions (Okapi-538)
    if ("/_/tenant".equals(ctx.request().path())) {
      String preq = ctx.request().getHeader(XOkapiHeaders.PERMISSIONS_REQUIRED);
      if (preq != null && !preq.isEmpty()) {
        logger.warn("test-auth: Rejecting request to /_/tenant because of "
          + XOkapiHeaders.PERMISSIONS_REQUIRED + ": " + preq);
        responseError(ctx, 403, "/_/tenant can not require permissions");
        return;
      }
    }
    // Fake some desired permissions
    String des = ctx.request().getHeader(XOkapiHeaders.PERMISSIONS_DESIRED);
    String req = ctx.request().getHeader(XOkapiHeaders.PERMISSIONS_REQUIRED);
    if (des != null && !des.isEmpty()) {
    ctx.response().headers()
      .add(XOkapiHeaders.PERMISSIONS, des);
    }
    if (req != null)
      ctx.response().headers().add("X-Auth-Permissions-Required", req);
    if (des != null)
      ctx.response().headers().add("X-Auth-Permissions-Desired", des);
    // Fake some module tokens
    String modTok = moduleTokens(ctx);
    ctx.response().headers()
      .add(XOkapiHeaders.TOKEN, tok)
      .add(XOkapiHeaders.MODULE_TOKENS, modTok)
      .add(XOkapiHeaders.USER_ID, userId);
    responseText(ctx, 202); // Abusing 202 to say filter OK
    logger.debug("test-auth: returning 202 and " + Json.encode(ctx.response()));
    logger.debug("test-auth: req:  " + Json.encode(ctx.request()));
    logger.debug("test-auth: resp:  " + Json.encode(ctx.response()));

    if (ctx.request().method() == HttpMethod.HEAD) {
      ctx.response().headers().remove("Content-Length");
      ctx.response().setChunked(true);
      logger.debug("test-auth: Head request");
      //ctx.response().end("ACCEPTED"); // Dirty trick??
      ctx.response().write("Accpted");
      logger.debug("test-auth: Done with the HEAD response");
    } else {
      echo(ctx);
    }
  }

  private void echo(RoutingContext ctx) {
    logger.debug("test-auth: echo");
    ctx.response().setChunked(true);
    String ctype = ctx.request().headers().get("Content-Type");
    if (ctype != null && !ctype.isEmpty()) {
      ctx.response().headers().add("Content-type", ctype);
    }

    ctx.request().handler(x -> {
      logger.debug("test-auth: echoing " + x);
      ctx.response().write(x);
    });
    ctx.request().endHandler(x -> {
      logger.debug("test-auth: endhandler");
      ctx.response().end();
      logger.debug("test-auth: endhandler ended the response");
    });
  }

  /**
   * Accept a request. Gets called with anything else than a POST to "/authn/login".
 These need to be accepted, so we can do a pre-filter before
   * the proper POST.
   *
   * @param ctx
   */
  public void accept(RoutingContext ctx) {
    logger.info("test-auth: Auth accept OK");
    responseText(ctx, 202);
    echo(ctx);
  }
}
