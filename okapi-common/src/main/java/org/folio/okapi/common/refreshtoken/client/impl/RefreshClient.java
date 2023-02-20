package org.folio.okapi.common.refreshtoken.client.impl;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import org.folio.okapi.common.Constants;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.refreshtoken.client.Client;
import org.folio.okapi.common.refreshtoken.client.ClientException;
import org.folio.okapi.common.refreshtoken.client.ClientOptions;
import org.folio.okapi.common.refreshtoken.tokencache.RefreshTokenCache;

@java.lang.SuppressWarnings({"squid:S1075"}) // URIs should not be hardcoded
public class RefreshClient implements Client {

  private static final String REFRESH_PATH = "/authn/refresh";

  private final ClientOptions clientOptions;

  private final RefreshTokenCache cache;

  private final String refreshToken;

  private final String tenant;

  /**
   * Create client that gets access token from refresh token.
   * @param clientOptions common options
   * @param cache access token cache for storing access tokens
   * @param tenant the value passed in X-Okapi-Tenant
   * @param refreshToken the refresh token is used to obtain access token
   */
  public RefreshClient(
      ClientOptions clientOptions, RefreshTokenCache cache,
      String tenant, String refreshToken) {
    this.clientOptions = clientOptions;
    this.cache = cache;
    this.tenant = tenant;
    this.refreshToken = refreshToken;
  }

  @Override
  public Future<String> getToken() {
    try {
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
          .map(this::tokenResponse);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<HttpRequest<Buffer>> getToken(HttpRequest<Buffer> request) {
    return getToken().map(token -> {
      request.putHeader(XOkapiHeaders.TOKEN, token);
      return request;
    });
  }

  String tokenResponse(HttpResponse<Buffer> res) {
    if (res.statusCode() != 201) {
      throw new ClientException("POST " + REFRESH_PATH + " returned status "
          + res.statusCode() + ": " + res.bodyAsString());
    }
    for (String v: res.cookies()) {
      io.netty.handler.codec.http.cookie.Cookie cookie = ClientCookieDecoder.STRICT.decode(v);
      if (Constants.COOKIE_ACCESS_TOKEN.equals(cookie.name())) {
        if (cache != null) {
          long age = cookie.maxAge() / 2;
          cache.put(refreshToken, cookie.value(),
              System.currentTimeMillis() + age * 1000);
        }
        return cookie.value();
      }
    }
    throw new ClientException(REFRESH_PATH + " did not return access token");
  }

}
