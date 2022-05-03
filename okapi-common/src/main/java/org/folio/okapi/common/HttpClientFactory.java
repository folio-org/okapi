package org.folio.okapi.common;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for HttpClient instances to avoid web socket leaks.
 */
public final class HttpClientFactory {
  private static final Map<Vertx, HttpClient> clients = new ConcurrentHashMap<>();

  private HttpClientFactory() {
    throw new UnsupportedOperationException("Utility classes cannot be instantiated");
  }

  /**
   * Get a WebClient, returns the same instance for the same Vertx instance.
   *
   * <p>The webClientOptions parameter is only used when creating the WebClient,
   * the options of an existing WebClient are not changed.
   */
  public static HttpClient getHttpClient(Vertx vertx, HttpClientOptions httpClientOptions) {
    return clients.computeIfAbsent(vertx, x -> vertx.createHttpClient(httpClientOptions));
  }

  /**
   * Get a WebClient, returns the same instance for the same Vertx instance.
   *
   * <p>It doesn't reset WebClientOptions when returning an existing WebClient.
   */
  public static HttpClient getHttpClient(Vertx vertx) {
    return getHttpClient(vertx, new HttpClientOptions());
  }
}
