package org.folio.okapi.common;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
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
   */
  public static WebClient getWebClient(Vertx vertx) {
    return clients.computeIfAbsent(vertx, x -> WebClient.create(vertx));
  }
}

