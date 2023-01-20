package org.folio.okapi.common.refreshtoken.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import java.util.function.Supplier;
import org.folio.okapi.common.refreshtoken.client.impl.LoginClient;
import org.folio.okapi.common.refreshtoken.client.impl.RefreshClient;
import org.folio.okapi.common.refreshtoken.tokencache.RefreshTokenCache;
import org.folio.okapi.common.refreshtoken.tokencache.TenantUserCache;

public interface Client {

  /**
   * Construct login client. Used normally for each incoming request.
   * @param clientOptions common options.
   * @param cache access token cache; maybe null for no cache (testing ONLY)
   * @param tenant Okapi tenant
   * @param username username to use for getting token
   * @param getPasswordSupplier for providing the password
   */
  static Client createLoginClient(ClientOptions clientOptions, TenantUserCache cache,
      String tenant, String username, Supplier<Future<String>> getPasswordSupplier) {
    return new LoginClient(clientOptions, cache, tenant, username, getPasswordSupplier);
  }

  /**
   * Create client that gets access token from refresh token.
   * @param clientOptions common options
   * @param cache access token cache for storing access tokens
   * @param tenant the value passed in X-Okapi-Tenant
   * @param refreshToken the refresh token is used to obtain access token
   */
  static Client createRefreshClient(ClientOptions clientOptions, RefreshTokenCache cache,
      String tenant, String refreshToken) {
    return new RefreshClient(clientOptions, cache, tenant, refreshToken);
  }

  /**
   * Get access token.
   *
   * <p>Use normally for each outgoing request.
   * @return token value
   * @throws ClientException if token could not be obtained.
   */
  Future<String> getToken();

  /**
   * Get access token and put it into request as X-Okapi-Token header.
   *
   * <p>Normally used for each outgoing request.
   * @param request the value that is returned for WebClient,getAbs and others.
   * @return request with token header
   */
  Future<HttpRequest<Buffer>> getToken(HttpRequest<Buffer> request);

}
