package org.folio.okapi.common.refreshtoken.client.impl;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import java.util.function.Supplier;
import org.folio.okapi.common.Constants;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.refreshtoken.client.Client;
import org.folio.okapi.common.refreshtoken.client.ClientException;
import org.folio.okapi.common.refreshtoken.client.ClientOptions;
import org.folio.okapi.common.refreshtoken.tokencache.TenantUserCache;

@java.lang.SuppressWarnings({"squid:S1075"}) // URIs should not be hardcoded
public class LoginClient implements Client {

  private static final String LOGIN_EXPIRY_PATH = "/authn/login-with-expiry";

  private static final String LOGIN_LEGACY_PATH = "/authn/login";

  private final TenantUserCache cache;

  private final ClientOptions clientOptions;

  private final String tenant;

  private final String username;

  private final Supplier<Future<String>> getPasswordSupplier;

  /**
   * Refresh legacy tokens older than this.
   */
  private static final long AGE_LEGACY_TOKEN = 86400L;

  /**
   * Subtract this many seconds of age, before considering expired.
   */
  private static final long AGE_DIFF_TOKEN = 10L;

  /**
   * Construct login client. Used normally for each incoming request.
   * @param clientOptions common options.
   * @param cache access token cache; maybe null for no cache (testing ONLY)
   * @param tenant Okapi tenant
   * @param username username to use for getting token
   * @param getPasswordSupplier for providing the password
   */
  public LoginClient(
      ClientOptions clientOptions, TenantUserCache cache, String tenant,
      String username, Supplier<Future<String>> getPasswordSupplier) {
    this.clientOptions = clientOptions;
    this.cache = cache;
    this.tenant = tenant;
    this.username = username;
    this.getPasswordSupplier = getPasswordSupplier;
  }

  Future<String> getTokenLegacy(JsonObject payload) {
    try {
      return clientOptions.getWebClient()
          .postAbs(clientOptions.getOkapiUrl() + LOGIN_LEGACY_PATH)
          .putHeader(HttpHeaders.ACCEPT.toString(), "*/*")
          .putHeader(XOkapiHeaders.TENANT, tenant)
          .sendJsonObject(payload).map(res -> {
            if (res.statusCode() != 201) {
              throw new ClientException(res.bodyAsString());
            }
            String token = res.getHeader(XOkapiHeaders.TOKEN);
            if (token == null) {
              throw new ClientException(LOGIN_LEGACY_PATH + " did not return token");
            }
            if (cache != null) {
              cache.put(tenant, username, token,
                  System.currentTimeMillis() + AGE_LEGACY_TOKEN * 1000);
            }
            return token;
          });
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  Future<String> getTokenWithExpiry(JsonObject payload) {
    try {
      return clientOptions.getWebClient()
          .postAbs(clientOptions.getOkapiUrl() + LOGIN_EXPIRY_PATH)
          .putHeader(HttpHeaders.ACCEPT.toString(), "*/*")
          .putHeader(XOkapiHeaders.TENANT, tenant)
          .sendJsonObject(payload).map(res -> {
            if (res.statusCode() == 404) {
              return null;
            } else if (res.statusCode() != 201) {
              throw new ClientException(res.bodyAsString());
            }
            for (String v : res.cookies()) {
              Cookie cookie = ClientCookieDecoder.STRICT.decode(v);
              if (Constants.COOKIE_ACCESS_TOKEN.equals(cookie.name())) {
                long age = cookie.maxAge() - AGE_DIFF_TOKEN;
                if (age < 0L) {
                  age = 0L;
                }
                if (cache != null) {
                  cache.put(tenant, username, cookie.value(),
                      System.currentTimeMillis() + age * 1000);
                }
                return cookie.value();
              }
            }
            throw new ClientException(LOGIN_EXPIRY_PATH + " did not return access token");
          });
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<String> getToken() {
    if (cache != null) {
      String cacheValue = cache.get(tenant, username);
      if (cacheValue != null) {
        return Future.succeededFuture(cacheValue);
      }
    }
    return getPasswordSupplier.get().compose(password -> {
      JsonObject payload = new JsonObject()
              .put("username", username)
              .put("password", password);
      return getTokenWithExpiry(payload)
              .compose(res -> res != null ? Future.succeededFuture(res) : getTokenLegacy(payload));
    });
  }

  @Override
  public Future<HttpRequest<Buffer>> getToken(HttpRequest<Buffer> request) {
    return getToken().map(token -> {
      request.putHeader(XOkapiHeaders.TOKEN, token);
      return request;
    });
  }
}
