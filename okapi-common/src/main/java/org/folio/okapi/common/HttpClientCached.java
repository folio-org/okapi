package org.folio.okapi.common;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;

public class HttpClientCached {

  private final Logger logger = OkapiLogger.get();

  private final HttpClient httpClient;

  private final List<HttpClientCacheEntry> cache = new LinkedList<>();

  public HttpClientCached(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public HttpClientRequest requestAbs(HttpMethod method, String absoluteUri,
    Handler<AsyncResult<HttpClientResponse>> hndlr) {

    return requestAbs(method, absoluteUri, absoluteUri, hndlr);
  }

  public HttpClientRequest requestAbs(HttpMethod method, String absoluteUri,
    String cacheUri, Handler<AsyncResult<HttpClientResponse>> hndlr) {

    if (method.equals(HttpMethod.GET) || method.equals(HttpMethod.HEAD)) {
      return new HttpClientRequestCached(this, httpClient, method,
        absoluteUri, cacheUri, hndlr);
    } else {
      return httpClient.requestAbs(method, absoluteUri, hndlr);
    }
  }

  HttpClientCacheEntry lookup(HttpClientCacheEntry l) {
    for (HttpClientCacheEntry e : cache) {
      if (l.method.equals(e.method) && l.cacheUri.equals(e.cacheUri)) {
        logger.debug("lookup found entry");
        return e;
      }
    }
    logger.debug("lookup found no entry");
    return null;
  }

  void add(HttpClientCacheEntry l) {
    logger.debug("adding entry");
    cache.add(l);
  }

  public void close() {
    httpClient.close();
  }
}
