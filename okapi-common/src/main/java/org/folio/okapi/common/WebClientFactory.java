package org.folio.okapi.common;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for WebClient instances to avoid web socket leaks.
 */
public final class WebClientFactory {
  private static final Map<Vertx, WebClient> clients = new ConcurrentHashMap<>();

  private WebClientFactory() {
    throw new UnsupportedOperationException("Utility classes cannot be instantiated");
  }

  /**
   * Get a WebClient, returns the same instance for the same Vertx instance.
   *
   * <p>The webClientOptions parameter is only used when creating the WebClient,
   * the options of an existing WebClient are not changed.
   *
   * <p>It uses {@code HttpClientFactory} for the underlying {@code HttpClient}.
   */
  public static WebClient getWebClient(Vertx vertx, WebClientOptions webClientOptions) {
    var httpClient = HttpClientFactory.getHttpClient(vertx, webClientOptions);
    return clients.computeIfAbsent(vertx, x -> WebClient.wrap(httpClient, webClientOptions));
  }

  /**
   * Get a WebClient, returns the same instance for the same Vertx instance.
   *
   * <p>It doesn't reset WebClientOptions when returning an existing WebClient.
   *
   * <p>It uses {@code HttpClientFactory} for the underlying {@code HttpClient}.
   */
  public static WebClient getWebClient(Vertx vertx) {
    return getWebClient(vertx, new WebClientOptions());
  }
}

