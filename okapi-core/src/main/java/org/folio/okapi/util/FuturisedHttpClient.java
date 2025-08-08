package org.folio.okapi.util;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import org.folio.okapi.ConfNames;
import org.folio.okapi.common.Config;

/**
 * Like {@link HttpClient} but methods catch each {@link Throwable} and pass
 * it as a failed {@link Future}.
 *
 * <p>Missing methods can be added when needed.
 */
public class FuturisedHttpClient {
  HttpClient httpClient;

  /**
   * Create a HTTP client for proxy outgoing requests.
   * @param vertx Vert.x handle to use for client
   * @param config Configuration for Vert.x
   * @return
   */
  public static FuturisedHttpClient getProxyClient(Vertx vertx, JsonObject config) {
    int httpProxySize = Config.getSysConfInteger(ConfNames.HTTP_MAX_SIZE_PROXY,
        ConfNames.HTTP_MAX_SIZE_PROXY_DEFAULT, config);
    return new FuturisedHttpClient(vertx, httpProxySize);
  }

  /**
   * Create a HTTP client for system requests.
   * @param vertx Vert.x handle to use for client
   * @param config Configuration for Vert.x
   * @return
   */
  public static FuturisedHttpClient getSystemClient(Vertx vertx, JsonObject config) {
    int httpSystemSize = Config.getSysConfInteger(ConfNames.HTTP_MAX_SIZE_SYSTEM,
        PoolOptions.DEFAULT_MAX_POOL_SIZE, config);
    return new FuturisedHttpClient(vertx, httpSystemSize);
  }

  public FuturisedHttpClient(Vertx vertx, HttpClientOptions httpClientOptions,
      PoolOptions poolOptions) {
    httpClient = vertx.createHttpClient(httpClientOptions, poolOptions);
  }

  FuturisedHttpClient(Vertx vertx, int size) {
    this(vertx, new HttpClientOptions(), new PoolOptions().setHttp1MaxSize(size));
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

  public HttpClient getHttpClient() {
    return httpClient;
  }
}
