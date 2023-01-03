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

  static Client createLoginClient(ClientOptions clientOptions, TenantUserCache tokenCache,
      String tenant, String username, Supplier<Future<String>> getPasswordSupplier) {
    return new LoginClient(clientOptions, tokenCache, tenant, username, getPasswordSupplier);
  }

  static Client createRefreshClient(ClientOptions clientOptions, RefreshTokenCache accessTokenCache,
      String tenant, String refreshToken) {
    return new RefreshClient(clientOptions, accessTokenCache, tenant, refreshToken);
  }

  /**
   * Get access token.
   *
   * <p>Use normally for each outgoing request.
   * @return token value or null if none could be obtained.
   */
  Future<String> getToken();

  /**
   * Populate access token for WebClient request.
   *
   * <p>Normally used for each outgoing request.
   * @param request the value that is returned for WebClient,getAbs and others.
   * @return request future result
   */
  Future<HttpRequest<Buffer>> getToken(HttpRequest<Buffer> request);

}
