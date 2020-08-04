package org.folio.okapi.common;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;

public class HttpClientLegacy {
  private HttpClientLegacy() {
    throw new IllegalStateException("HttpClientLegacy");
  }

  /**
   * Send HTTP request with style ala Vert.x 3.
   * @param client HTTP client
   * @param method HTTP method
   * @param socketAddress socket address
   * @param url Full URL
   * @param response response handler
   */
  public static void requestAbs(HttpClient client, HttpMethod method,
                                SocketAddress socketAddress, String url,
                                Handler<AsyncResult<HttpClientRequest>> response) {
    client.request(new RequestOptions()
        .setMethod(method)
        .setAbsoluteURI(url)
        .setServer(socketAddress))
        .onComplete(response);
  }

  /**
   * Send HTTP request with style ala Vert.x 3.
   * @param client HTTP client
   * @param method HTTP method
   * @param url Full URL
   * @param response response handler
   */
  public static void requestAbs(HttpClient client, HttpMethod method,
                                String url, Handler<AsyncResult<HttpClientRequest>> response) {
    client.request(new RequestOptions()
        .setMethod(method)
        .setAbsoluteURI(url))
        .onComplete(response);
  }

  /**
   * Send HTTP request with style ala Vert.x 3.
   * @param client HTTP client
   * @param method HTTP method
   * @param port server port
   * @param host server host
   * @param response response handler
   */
  public static void request(HttpClient client, HttpMethod method, int port,
                             String host, String uri,
                             Handler<AsyncResult<HttpClientRequest>> response) {
    client.request(
        new RequestOptions().setMethod(method).setHost(host).setPort(port).setURI(uri))
        .onComplete(response);
  }

  /**
   * Send HTTP POST request with style ala Vert.x 3.
   * @param client HTTP client
   * @param port server port
   * @param host server host
   * @param response response handler
   */
  public static void post(HttpClient client, int port,
                          String host, String uri,
                          Handler<AsyncResult<HttpClientRequest>> response) {
    request(client, HttpMethod.POST, port, host, uri, response);
  }

  /**
   * Send HTTP GET request with style ala Vert.x 3.
   * @param client HTTP client
   * @param port server port
   * @param host server host
   * @param response response handler
   */
  public static void get(HttpClient client, int port,
                         String host, String uri,
                         Handler<AsyncResult<HttpClientRequest>> response) {
    request(client, HttpMethod.GET, port, host, uri, response);
  }

  /**
   * Send HTTP DELETE request with style ala Vert.x 3.
   * @param client HTTP client
   * @param port server port
   * @param host server host
   * @param response response handler
   */
  public static void delete(HttpClient client, int port,
                            String host, String uri,
                            Handler<AsyncResult<HttpClientRequest>> response) {
    request(client, HttpMethod.DELETE, port, host, uri, response);
  }
}
