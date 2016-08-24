/*
 * Copyright (C) 2015 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.auth;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import static okapi.common.HttpResponse.*;
import java.util.HashMap;

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

  static final String OKAPITOKENHEADER = "X-Okapi-Token";
  static final String OKAPIMODPERMSHEADER = "X-Okapi-Module-Permissions";
  static final String OKAPIMODTOKENSHEADER = "X-Okapi-Module-Tokens";
  static final String OKAPIPERMISSIONSREQUIRED = "X-Okapi-Permissions-Required";
  static final String OKAPIPERMISSIONSDESIRED = "X-Okapi-Permissions-Desired";
  static final String OKAPIPERMISSIONSHEADER = "X-Okapi-Permissions";

  private final Logger logger = LoggerFactory.getLogger("okapi-test-auth-module");

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
    String salt = "salt"; // A real-life toke would use something from a date too.
      // We don't, since we want our unit tests to be reproducible.
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
    if (json.length() == 0) {
      logger.info("Auth accept OK in login");
      responseText(ctx, 202).end("Auth accept in /login");
      return;
    }
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


  /**
   * Fake some module permissions.
   * Generates silly tokens with the module name as the tenant, and a list
   * of permissions as the user. These are still valid tokens, although it is
   * not possible to extract the user or tenant from them.
   */
  private String moduleTokens(RoutingContext ctx) {
    String modPermJson = ctx.request().getHeader(OKAPIMODPERMSHEADER);
    logger.debug("moduleTokens: trying to decode '" + modPermJson + "'");
    HashMap<String, String> tokens = new HashMap<>();
    try {
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
    } catch (NoSuchAlgorithmException ex) {
      logger.error("no such algorithm: " + ex.getMessage());
    }
    if (!tokens.isEmpty()) { // return also a 'clean' token
      tokens.put("_", ctx.request().getHeader(OKAPITOKENHEADER));
    }
    String alltokens = Json.encode(tokens);
    logger.debug("auth: module tokens for " + modPermJson + "  :  " + alltokens);
    return alltokens;
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
      responseText(ctx, 500).end(ex.getMessage());
      return;
    }
    // Fake some desired permissions
    String des = ctx.request().getHeader(OKAPIPERMISSIONSDESIRED);
    if ( des != null && ! des.isEmpty()) {
    ctx.response().headers()
      .add(OKAPIPERMISSIONSDESIRED, des);
    }
    // Fake some module tokens
    String modTok = moduleTokens(ctx);
    ctx.response().headers()
      .add(OKAPITOKENHEADER, tok)
      .add(OKAPIMODTOKENSHEADER, modTok);
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
