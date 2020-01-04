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

  public HttpClientRequest requestAbs(HttpMethod method, String url, Handler<AsyncResult<HttpClientResponse>> hndlr) {
    if (method.equals(HttpMethod.GET) || method.equals(HttpMethod.HEAD)) {
      return new HttpClientRequestCached(this, httpClient, method, url, hndlr);
    } else {
      return httpClient.requestAbs(method, url, hndlr);
    }
  }

  HttpClientCacheEntry lookup(HttpClientCacheEntry l) {
    for (HttpClientCacheEntry e : cache) {
      if (l.method.equals(e.method)
        && l.host.equals(e.host)
        && l.url.equals(e.url)) {
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
