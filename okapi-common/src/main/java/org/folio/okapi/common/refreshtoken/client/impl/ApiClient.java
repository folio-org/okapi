package org.folio.okapi.common.refreshtoken.client.impl;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import java.util.function.Supplier;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.refreshtoken.client.Client;

public class ApiClient implements Client {

  private final Supplier<Future<String>> apiTokenSupplier;

  /**
   * Client that gets API token from the outside.
   * @param apiTokenSupplier supplier for API tokens.
   */
  public ApiClient(Supplier<Future<String>> apiTokenSupplier) {
    this.apiTokenSupplier = apiTokenSupplier;
  }

  @Override
  public Future<String> getToken() {
    return apiTokenSupplier.get();
  }

  @Override
  public Future<HttpRequest<Buffer>> getToken(HttpRequest<Buffer> request) {
    return apiTokenSupplier.get().map(apiToken -> {
      request.putHeader(XOkapiHeaders.TOKEN, apiToken);
      return request;
    });
  }
}
