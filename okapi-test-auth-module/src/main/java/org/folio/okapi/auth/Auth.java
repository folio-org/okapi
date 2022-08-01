package org.folio.okapi.auth;

import io.vertx.core.MultiMap;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.OkapiStringUtil;
import org.folio.okapi.common.XOkapiHeaders;

/**
 * A dummy auth module. Provides a minimal authentication mechanism.
 * Mostly for testing Okapi itself.
 * Does generate tokens for module permissions, but otherwise does not filter
 * permissions for anything, but does return X-Okapi-Permissions-Desired in
 * X-Okapi-Permissions, as if all desired permissions were granted.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
class Auth {

  private final Logger logger = OkapiLogger.get();

  private List<String> tenantsInitialized = new LinkedList<>();

  /**
   * Calculate a token from tenant and username. Fakes a JWT token, almost
   * but not quite completely unlike the one that a real auth module would create.
   * The important point is that it is a JWT, and that it has a tenant in it,
   * so Okapi can recover it in case it does not see a X-Okapi-Tenant header,
   * as happens in some unit tests.
   *
   * @param tenant tenant string
   * @param user user string
   * @return the token
   */
  private String token(String tenant, String user, String [] permissions)  {

    // Create a dummy JWT token with the correct tenant
    JsonObject payload = new JsonObject()
        .put("sub", user)
        .put("tenant", tenant)
        .put("iat", Instant.now().getEpochSecond());;
    if (permissions != null) {
      payload.put("permissions", permissions);
    }

    String encodedpl = payload.encode();
    logger.debug("test-auth: payload: {}", encodedpl);
    byte[] bytes = encodedpl.getBytes();
    byte[] pl64bytes = Base64.getEncoder().encode(bytes);
    String pl64 = new String(pl64bytes);
    String token = "dummyJwt." + pl64 + ".sig";
    logger.debug("test-auth: token: {}", token);
    return token;
  }

  public void listTenants(RoutingContext ctx) {
    ctx.response().setStatusCode(200);
    ctx.response().putHeader("Content-Type", "application/json");
    ctx.response().end(new JsonArray(tenantsInitialized).encodePrettily());
  }

  public void tenantOp(RoutingContext ctx) {
    MultiMap headers = ctx.request().headers();
    String permissions = headers.get(XOkapiHeaders.PERMISSIONS);
    String tenant = headers.get(XOkapiHeaders.TENANT);
    if ("magic".equals(permissions)) {
      tenantsInitialized.add(tenant);
      ctx.response().setStatusCode(200);
      ctx.response().putHeader("Content-Type", "application/json");
      ctx.response().end("{}");
    } else {
      filter(ctx);
    }
  }

  public void login(RoutingContext ctx) {
    final String json = ctx.body().asString();
    if (json == null || json.length() == 0) {
      logger.info("test-auth: accept OK in login");
      HttpResponse.responseText(ctx, 202).end("Auth accept in /authn/login");
      return;
    }
    LoginParameters p;
    try {
      p = Json.decodeValue(json, LoginParameters.class);
    } catch (DecodeException ex) {
      HttpResponse.responseText(ctx, 400).end("Error in decoding parameters: " + ex);
      return;
    }

    // Simple password validation: "peter" has a password "peter-password", etc.
    String u = p.getUsername();
    String correctpw = u + "-password";
    if (!p.getPassword().equals(correctpw)) {
      logger.warn("test-auth: Bad passwd for '{}'. Got '{}' expected '{}",
          u, p.getPassword(), correctpw);
      HttpResponse.responseText(ctx, 401).end("Wrong username or password");
      return;
    }
    String tok;
    tok = token(p.getTenant(), p.getUsername(), p.getPermissions());
    logger.info("test-auth: Ok login for {}: {}", u, tok);
    HttpResponse.responseJson(ctx, 200).putHeader(XOkapiHeaders.TOKEN, tok).end(json);
  }

  /**
   * Fake some module permissions.
   * Generates silly tokens with the module name as the tenant, and a list
   * of permissions as the user. These are still valid tokens, although it is
   * not possible to extract the user or tenant from them.
   */
  private String moduleTokens(RoutingContext ctx) {
    String modPermJson = ctx.request().getHeader(XOkapiHeaders.MODULE_PERMISSIONS);
    logger.debug("test-auth: moduleTokens: trying to decode '{}'", modPermJson);
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
        tokens.put(mod, token(mod, permstr.toString(), null));
      }
    }
    tokens.put("_", ctx.request().getHeader(XOkapiHeaders.TOKEN));

    String alltokens = Json.encode(tokens);
    logger.debug("test-auth: module tokens for {}: {}", modPermJson, alltokens);
    return alltokens;
  }

  public void filter(RoutingContext ctx) {
    String phase = ctx.request().headers().get(XOkapiHeaders.FILTER);
    logger.debug("test-auth filter {}: '{}'", XOkapiHeaders.FILTER, phase);
    if (phase == null || phase.startsWith("auth")) {
      check(ctx);
      return;
    }
    ctx.response().putHeader("X-Auth-Filter-Phase", phase);
    // Hack to test return codes on various filter phases
    phase = phase.split(" ")[0];
    String phaseHeader = ctx.request().headers().get("X-Filter-" + phase);
    logger.debug("filter: 'X-Filter-{}': {}", phase, phaseHeader);
    if (phaseHeader != null) {
      ctx.response().setStatusCode(Integer.parseInt(phaseHeader));
    }

    // Hack to test pre/post filter returns error
    if (ctx.request().headers().contains("X-filter-" + phase + "-error")) {
      ctx.response().setStatusCode(500);
    }

    // Hack to test pre/post filter can see request headers
    if (ctx.request().headers().contains("X-request-" + phase + "-error")
        && ctx.request().headers().contains(XOkapiHeaders.REQUEST_IP)
        && ctx.request().headers().contains(XOkapiHeaders.REQUEST_TIMESTAMP)
        && ctx.request().headers().contains(XOkapiHeaders.REQUEST_METHOD)) {
      ctx.response().setStatusCode(500);
    }
    echo(ctx);
  }

  public void check(RoutingContext ctx) {
    MultiMap headers = ctx.request().headers();
    final String req = headers.get(XOkapiHeaders.PERMISSIONS_REQUIRED);
    String tenant = headers.get(XOkapiHeaders.TENANT);
    List<String> permissions = null;
    if (tenant == null) {
      tenant = "supertenant";
    }
    String userId = "?";
    String tok = headers.get(XOkapiHeaders.TOKEN);
    if (tok == null || tok.isEmpty()) {
      // Only make a token if no permissions are required
      if (req != null && !req.isEmpty()) {
        HttpResponse.responseError(ctx, 401, "Permissions required: " + req);
        return;
      }
      tok = token(tenant, "-", null); // create a dummy token without username
      // We call /_/tenant and /_/tenantPermissions in our tests without a token.
      // In real life, this is more complex, mod-authtoken creates a non-
      // login token, possibly with modulePermissions, and then checks that
      // against the permissions required for the tenant interface...
    } else {
      logger.debug("test-auth: check starting with tok {} and tenant {}", tok, tenant);

      String[] splitTok = tok.split("\\.");
      if (splitTok.length != 3) {
        String tmp = tok;
        logger.warn("test-auth: Bad JWT, can not split in three parts. '{}",
            () -> OkapiStringUtil.removeLogCharacters(tmp));
        HttpResponse.responseError(ctx, 400, "Auth.check: Bad JWT");
        return;
      }

      if (!"dummyJwt".equals(splitTok[0])) {
        logger.warn("test-auth: Bad dummy JWT, starts with '{}', not 'dummyJwt'", splitTok[0]);
        HttpResponse.responseError(ctx, 400, "Auth.check needs a dummyJwt");
        return;
      }
      String payload = splitTok[1];

      try {
        String decodedJson = new String(Base64.getDecoder().decode(payload));
        logger.debug("test-auth: check payload: {}", decodedJson);
        JsonObject jtok = new JsonObject(decodedJson);
        userId = jtok.getString("sub", "");
        JsonArray jsonArray = jtok.getJsonArray("permissions");
        if (jsonArray != null) {
          permissions = jsonArray.getList();
        }
      } catch (IllegalArgumentException e) {
        HttpResponse.responseError(ctx, 400, "Bad Json payload " + payload);
        return;
      }
      final String ovTok = headers.get(XOkapiHeaders.ADDITIONAL_TOKEN);
      logger.info("ovTok={}", () -> OkapiStringUtil.removeLogCharacters(ovTok));
      if (ovTok != null && !"dummyJwt".equals(ovTok)) {
        HttpResponse.responseError(ctx, 400, "Bad additonal token: " + ovTok);
        return;
      }
    }
    // Fail a call to /_/tenant that requires permissions (Okapi-538)
    if ("/_/tenant".equals(ctx.request().path()) && req != null) {
      logger.warn("test-auth: Rejecting request to /_/tenant because of {}: {}",
          () -> XOkapiHeaders.PERMISSIONS_REQUIRED, () -> OkapiStringUtil.removeLogCharacters(req));
      HttpResponse.responseError(ctx, 403, "/_/tenant can not require permissions");
      return;
    }
    // Fake some desired permissions
    String des = headers.get(XOkapiHeaders.PERMISSIONS_DESIRED);
    if (des != null) {
      ctx.response().headers().add(XOkapiHeaders.PERMISSIONS, des);
    } else if (ctx.request().path().equals("/_/tenant")) {
      ctx.response().headers().add(XOkapiHeaders.PERMISSIONS, "magic");
    }
    String modTok = moduleTokens(ctx);
    if (req != null) {
      ctx.response().headers().add("X-Auth-Permissions-Required", req);
      if (permissions != null) {
        String[] reqList = req.split(",");
        for (String r : reqList) {
          if (!permissions.contains(r)) {
            // auth should NOT be returning module tokens in case of errors, but mod-authtoken
            // does that at the moment MODAT-107
            ctx.response().headers().add(XOkapiHeaders.MODULE_TOKENS, modTok);
            HttpResponse.responseError(ctx, 403, "Call requires permission " + r);
            return;
          }
        }
      }
    }
    if (des != null) {
      ctx.response().headers().add("X-Auth-Permissions-Desired", des);
    }
    // Fake some module tokens
    ctx.response().headers()
        .add(XOkapiHeaders.TOKEN, tok)
        .add(XOkapiHeaders.MODULE_TOKENS, modTok)
        .add(XOkapiHeaders.USER_ID, userId);
    HttpResponse.responseText(ctx, 202); // Abusing 202 to say filter OK
    echo(ctx);
  }

  private void echo(RoutingContext ctx) {
    logger.debug("test-auth: echo");
    ctx.response().setChunked(true);
    String ctype = ctx.request().headers().get("Content-Type");
    if (ctype != null && !ctype.isEmpty()) {
      ctx.response().headers().set("Content-type", ctype);
    }

    ctx.request().handler(x -> {
      logger.debug("test-auth: echoing {}", x);
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
   * These need to be accepted, so we can do a pre-filter before
   * the proper POST.
   *
   * @param ctx Routing context
   */
  public void accept(RoutingContext ctx) {
    logger.info("test-auth: Auth accept OK");
    HttpResponse.responseText(ctx, 202);
    echo(ctx);
  }
}
