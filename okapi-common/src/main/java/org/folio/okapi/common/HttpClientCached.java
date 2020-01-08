package org.folio.okapi.common;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.Logger;

public class HttpClientCached {

  private final Logger logger = OkapiLogger.get();

  private final HttpClient httpClient;

  private final Map<String,HttpClientCacheEntry> cache = new HashMap<>();
  private Set<String> cacheIgnoreHeaders = new TreeSet<>();

  private static final int DEFAULT_MAX_BODY_SIZE = 8192;

  private int maxBodySize = DEFAULT_MAX_BODY_SIZE;

  public HttpClientCached(HttpClient httpClient) {
    cacheIgnoreHeaders.add("date");
    this.httpClient = httpClient;
  }

  HttpClientCached ignoreHeader(String h) {
    if (h.startsWith("-")) {
      this.cacheIgnoreHeaders.remove(h.substring(1).toLowerCase());
    } else {
      this.cacheIgnoreHeaders.add(h.toLowerCase());
    }
    return this;
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

  private String genKey(HttpClientCacheEntry l) {
    List<String> strings = new LinkedList<>();
    for (Entry<String, String> e : l.requestHeaders.entries()) {
      String k = e.getKey().toLowerCase();
      if (!this.cacheIgnoreHeaders.contains(k)) {
        strings.add(k + ":" + e.getValue());
      }
    }
    Collections.sort(strings);
    StringBuilder k = new StringBuilder();
    k.append(l.method.name()).append(',').append(l.cacheUri);
    for (String s : strings) {
      k.append(',').append(s);
    }
    return k.toString();
  }

  HttpClientCacheEntry lookup(HttpClientCacheEntry l) {
    String key = genKey(l);
    HttpClientCacheEntry e = cache.get(key);
    if (e != null) {
      logger.debug("lookup found entry key={}", key);
      return e;
    }
    logger.debug("lookup found no entry key={}", key);
    return null;
  }

  void add(HttpClientCacheEntry l) {
    logger.debug("adding entry");
    cache.put(genKey(l), l);
  }

  public int getMaxBodySize() {
    return maxBodySize;
  }

  public void setMaxBodySize(int maxBodySize) {
    this.maxBodySize = maxBodySize;
  }

  public void close() {
    httpClient.close();
  }
}
