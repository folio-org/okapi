package org.folio.okapi.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.RequestOptions;

/**
 * Like {@link HttpClient} but methods catch each {@link Throwable} and pass
 * it as a failed {@link Future}.
 *
 * <p>Missing methods can be added when needed.
 */
public class FuturisedHttpClient {
  HttpClient httpClient;

  public FuturisedHttpClient(Vertx vertx, HttpClientOptions httpClientOptions) {
    httpClient = vertx.createHttpClient(httpClientOptions);
  }

  public FuturisedHttpClient(Vertx vertx) {
    this(vertx, new HttpClientOptions());
  }

  /**
   * Create an HTTP request to send to the server.
   *
   * @return the HttpClientRequest on success, a Throwable if any was thrown or on failure
   */
  public Future<HttpClientRequest> request(RequestOptions options) {
    try {
      return httpClient.request(options);
    } catch (Throwable t) {
      return Future.failedFuture(t);
    }
  }

  /**
   * Create an HTTP request to send to the server.
   *
   * @param handler the HttpClientRequest on success, a Throwable if any was thrown or on failure
   */
  public void request(RequestOptions options,
                      Handler<AsyncResult<HttpClientRequest>> handler) {
    request(options).onComplete(handler);
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }
}
