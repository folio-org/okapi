/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.auth;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A dummy auth module. Provides a minimal authentication mechanism.
 *
 * @author heikki
 *
 * TODO: Check the X-Okapi-Tenant header matches the tenant parameter, or use
 * that one instead of the parameter. TODO: Separate the headers so that -
 * X-Okapi-Tenant is the tenant - X-Okapi-User is the user - X-Okapi-token is
 * the crypto token OKAPI needs to get hold of the tenant already before a
 * login, so it should be separate.
 *
 *
 * TODO: Add a time stamp and some salt to the crypto.
 *
 * TODO: Accept also the previous token, so sessions don't die at turnover.
 */
public class Auth {

  static final String OKAPITOKENHEADER = "X-Okapi-Token";

  private final Logger logger = LoggerFactory.getLogger("okapi-auth");

  private HttpServerResponse responseText(RoutingContext ctx, int code) {
    return ctx.response().setStatusCode(code).putHeader("Content-Type", "text/plain");
  }

  private HttpServerResponse responseJson(RoutingContext ctx, int code) {
    return ctx.response().setStatusCode(code).putHeader("Content-Type", "application/json");
  }

  /**
   * Calculate a token from tenant and username. The token is like ttt:uuu:ccc,
   * where ttt is the tenant, uuu is the user, and ccc is a crypto thing that
   * depends on those two.
   *
   * @param tenant
   * @param user
   * @return the token
   * @throws NoSuchAlgorithmException which should never happen
   */
  private String token(String tenant, String user) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("MD5");
    String salt = "salt"; // TODO - Add something from the current date
    md.update(salt.getBytes());
    md.update(tenant.getBytes());
    md.update(user.getBytes());
    byte[] mdbytes = md.digest();

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < mdbytes.length; i++) {
      sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
    }
    return "" + tenant + ":" + user + ":" + sb.toString();
  }

  ;

  public void login(RoutingContext ctx) {
    final String json = ctx.getBodyAsString();
    LoginParameters p;
    try {
      p = Json.decodeValue(json, LoginParameters.class);
    } catch (DecodeException ex) {
      responseText(ctx, 400).end("Error in decoding parameters: " + ex);
      return;
    }

    // Simple password validation: "peter" has a password "peter-password", etc
    String u = p.getUsername();
    String correctpw = u + "-password";
    if (!p.getPassword().equals(correctpw)) {
      logger.info("Bad passwd for '" + u + "'. "
              + "Got '" + p.getPassword() + "' expected '" + correctpw + "'");
      responseText(ctx, 401).end("Wrong username or password");
      return;

    }
    String tok;
    try {
      tok = token(p.getTenant(), p.getUsername());
    } catch (NoSuchAlgorithmException ex) {
      responseText(ctx, 500).end("Error in invoking MD5sum: " + ex);
      return;
    }
    logger.info("Ok login for " + u + ": " + tok);
    responseJson(ctx, 200).putHeader(OKAPITOKENHEADER, tok).end(json);
  }

  public void check(RoutingContext ctx) {
    String tok = ctx.request().getHeader(OKAPITOKENHEADER);
    if (tok == null || tok.isEmpty()) {
      logger.info("Auth.check called without " + OKAPITOKENHEADER);
      responseText(ctx, 401)
              .end("Auth.check called without " + OKAPITOKENHEADER);
      return;
    }
    // Do some magic
    String[] split = tok.split(":", 3);
    String properToken = "???";
    try {
      if (split.length == 3) {
        properToken = token(split[0], split[1]);
      }
      if (!tok.equals(properToken)) {
        logger.info("Invalid token. "
                + "Got '" + tok + "' Expected '" + properToken + "'");
        responseText(ctx, 401).end("Invalid token");
        return;
      }
    } catch (NoSuchAlgorithmException ex) {
      logger.error("no such algorithm: " + ex.getMessage());
    }
    ctx.response()
            .headers().add(OKAPITOKENHEADER, tok);
    responseText(ctx, 202);
    echo(ctx);
    // signal to the conduit that we want to continue the module chain
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
   * Accept a request. Gets called with anything else than a POST to /login.
   * These need to be accepted, so we can do a pre-check before the proper POST
   *
   * @param ctx
   */
  public void accept(RoutingContext ctx) {
    logger.info("Auth accept OK");
    responseText(ctx, 202);
    echo(ctx);
  }
}
