package org.folio.okapi.common.refreshtoken.client.impl;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpRequest;
import org.folio.okapi.common.Constants;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.refreshtoken.client.Client;
import org.folio.okapi.common.refreshtoken.client.ClientException;
import org.folio.okapi.common.refreshtoken.client.ClientOptions;
import org.folio.okapi.common.refreshtoken.tokencache.RefreshTokenCache;

@java.lang.SuppressWarnings({"squid:S1075"}) // URIs should not be hardcoded
public class RefreshClient implements Client {

  private static final String REFRESH_PATH = "/authn/refresh";

  /**
   * Subtract this many seconds of age, before considering expired.
   */
  private static final long AGE_DIFF_TOKEN = 10L;

  private final ClientOptions clientOptions;

  private final RefreshTokenCache cache;

  private final String refreshToken;

  private final String tenant;

  /**
   * Create client that gets access token from given refresh token.
   * @param clientOptions common options
   * @param refreshTokenCache cache for storing access tokens
   * @param tenant the value passed in X-Okapi-Tenant
   * @param refreshToken the refresh token is used to obtain access token
   */
  public RefreshClient(
      ClientOptions clientOptions, RefreshTokenCache refreshTokenCache,
      String tenant, String refreshToken) {
    this.clientOptions = clientOptions;
    this.cache = refreshTokenCache;
    this.tenant = tenant;
    this.refreshToken = refreshToken;
  }

  @Override
  public Future<String> getToken() {
    if (cache != null) {
      String cacheValue = cache.get(refreshToken);
      if (cacheValue != null) {
        return Future.succeededFuture(cacheValue);
      }
    }
    return clientOptions.getWebClient()
        .postAbs(clientOptions.getOkapiUrl() + REFRESH_PATH)
        .putHeader(HttpHeaders.ACCEPT.toString(), "*/*")
        .putHeader(XOkapiHeaders.TENANT, tenant)
        .putHeader(HttpHeaders.COOKIE.toString(),
            Cookie.cookie(Constants.COOKIE_REFRESH_TOKEN, refreshToken).encode())
        .send()
        .map(res -> {
          if (res.statusCode() != 201) {
            throw new ClientException(res.bodyAsString());
          }
          for (String v: res.cookies()) {
            io.netty.handler.codec.http.cookie.Cookie cookie = ClientCookieDecoder.STRICT.decode(v);
            if (Constants.COOKIE_ACCESS_TOKEN.equals(cookie.name())) {
              long age = cookie.maxAge() - AGE_DIFF_TOKEN;
              if (age < 0L) {
                age = 0L;
              }
              if (cache != null) {
                cache.put(refreshToken, cookie.value(),
                    System.currentTimeMillis() + age * 1000);
              }
              return cookie.value();
            }
          }
          throw new ClientException(REFRESH_PATH + " did not return access token");
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
