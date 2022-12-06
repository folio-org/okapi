package org.folio.okapi.common.refreshtoken.client;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.refreshtoken.tokencache.TokenCache;

public class TokenClient {
  private static final Logger log = LogManager.getLogger(TokenClient.class);

  private final TokenCache cache;
  private final WebClient client;
  private final String okapiUrl;
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
   * Construct client. Used normally for each incoming request.
   * @param okapiUrl OKAPI URL for to use for getting token
   * @param client WebClient
   * @param cache token cache; maybe null for no cache (testing ONLY)
   * @param tenant Okapi tenant
   * @param username username to use for getting token
   * @param getPasswordSupplier for providing the password
   */
  public TokenClient(String okapiUrl, WebClient client, TokenCache cache, String tenant,
      String username, Supplier<Future<String>> getPasswordSupplier) {
    this.cache = cache;
    this.client = client;
    this.okapiUrl = okapiUrl;
    this.tenant = tenant;
    this.username = username;
    this.getPasswordSupplier = getPasswordSupplier;
  }

  Future<String> getTokenLegacy(JsonObject payload) {
    return client.postAbs(okapiUrl + "/authn/login")
        .putHeader("Accept", "*/*")
        .putHeader(XOkapiHeaders.TENANT, tenant)
        .sendJsonObject(payload).map(res -> {
          if (res.statusCode() != 201) {
            throw new TokenClientException(res.bodyAsString());
          }
          String token = res.getHeader(XOkapiHeaders.TOKEN);
          if (cache != null) {
            cache.put(tenant, username, token,
                System.currentTimeMillis() + AGE_LEGACY_TOKEN * 1000);
          }
          return token;
        });
  }

  Future<String> getTokenWithExpiry(JsonObject payload) {
    return client.postAbs(okapiUrl + "/authn/login-with-expiry")
        .putHeader("Accept", "*/*")
        .putHeader(XOkapiHeaders.TENANT, tenant)
        .sendJsonObject(payload).map(res -> {
          if (res.statusCode() == 201) {
            for (Map.Entry<String,String> entry : res.headers().entries()) {
              if ("Set-Cookie".equals(entry.getKey())) {
                Cookie cookie = ClientCookieDecoder.STRICT.decode(entry.getValue());
                if (XOkapiHeaders.COOKIE_ACCESS_TOKEN.equals(cookie.name())) {
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
            }
            return null;
          } else if (res.statusCode() == 404) {
            return null;
          } else {
            throw new TokenClientException(res.bodyAsString());
          }
        });
  }

  /**
   * Get token. Use normally for each outgoing request.
   * @return token value or null if none could be obtained.
   */
  public Future<String> getToken() {
    if (cache != null) {
      String cacheValue;
      try {
        cacheValue = cache.get(tenant, username);
      } catch (Exception e) {
        log.warn("Failed to access TokenCache {}", e.getMessage(), e);
        return Future.failedFuture("Failed to access TokenCache");
      }
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

  /**
   * Populate token for WebClient request. Normally used for each outgoing request.
   * @param request the value that is returned for WebClient,getAbs and others.
   * @return request future result
   */
  public Future<HttpRequest<Buffer>> getToken(HttpRequest<Buffer> request) {
    return getToken().map(token -> {
      if (token != null) {
        request.putHeader(XOkapiHeaders.TOKEN, token);
      }
      return request;
    });
  }
}
