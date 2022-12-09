package org.folio.okapi.common.refreshtoken.client.impl;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import java.util.function.Supplier;
import org.folio.okapi.common.refreshtoken.client.Client;
import org.folio.okapi.common.refreshtoken.client.ClientOptions;
import org.folio.okapi.common.refreshtoken.tokencache.RefreshTokenCache;

public class RefreshClient implements Client {

  private final ClientOptions clientOptions;

  private final RefreshTokenCache refreshTokenCache;

  private final Supplier<Future<String>> getRefreshTokenSupplier;

  /**
   * Create client that gets access token from given refresh token.
   * @param clientOptions common options
   * @param refreshTokenCache cache for storing access tokens
   * @param getRefreshTokenSupplier supplier for refresh token
   */
  public RefreshClient(
      ClientOptions clientOptions, RefreshTokenCache refreshTokenCache,
      Supplier<Future<String>> getRefreshTokenSupplier) {
    this.clientOptions = clientOptions;
    this.refreshTokenCache = refreshTokenCache;
    this.getRefreshTokenSupplier = getRefreshTokenSupplier;
  }

  @Override
  public Future<String> getToken() {
    return Future.failedFuture("Not implemented");
  }

  @Override
  public Future<HttpRequest<Buffer>> getToken(HttpRequest<Buffer> request) {
    return Future.failedFuture("Not implemented");
  }
}
