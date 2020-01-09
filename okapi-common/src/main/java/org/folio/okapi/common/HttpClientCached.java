package org.folio.okapi.common;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import java.time.Instant;
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

  private final Map<String, HttpClientCacheEntry> cache = new HashMap<>();
  private Set<String> cacheIgnoreHeaders = new TreeSet<>();

  private static final int DEFAULT_MAX_BODY_SIZE = 8192;

  private int maxBodySize = DEFAULT_MAX_BODY_SIZE;

  private long globalMaxAge = 600;
  private long defaultMaxAge = 60;

  public HttpClientCached(HttpClient httpClient) {
    cacheIgnoreHeaders.add("date");
    this.httpClient = httpClient;
  }

  HttpClientCached globalMaxAge(long seconds) {
    globalMaxAge = seconds;
    return this;
  }

  HttpClientCached defaultMaxAge(long seconds) {
    defaultMaxAge = seconds;
    return this;
  }

  HttpClientCached addIgnoreHeader(String h) {
    this.cacheIgnoreHeaders.add(h.toLowerCase());
    return this;
  }

  HttpClientCached removeIgnoreHeader(String h) {
    this.cacheIgnoreHeaders.remove(h.toLowerCase());
    return this;
  }

  public HttpClientRequest requestAbs(HttpMethod method, String absoluteUri,
    Handler<AsyncResult<HttpClientResponse>> hndlr) {

    return requestAbs(method, absoluteUri, absoluteUri, hndlr);
  }

  public HttpClientRequest requestAbs(HttpMethod method, String absoluteUri,
    String cacheUri, Handler<AsyncResult<HttpClientResponse>> hndlr) {

    if (method.equals(HttpMethod.GET) || method.equals(HttpMethod.HEAD)) {
      expire();
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
      e.hitCount++;
      logger.debug("lookup found entry key={}", key);
      return e;
    }
    logger.debug("lookup found no entry key={}", key);
    return null;
  }

  String lookupCacheControl(MultiMap headers, String component) {
    String v = headers.get("Cache-Control");
    if (v == null) {
      return null;
    }
    return lookupCacheControl(v, component);
  }

  String lookupCacheControl(String v, String component) {
    v = v.toLowerCase();
    int off = v.indexOf(component);
    if (off == -1) {
      return null;
    }
    off += component.length();
    while (off < v.length() && Character.isWhitespace(v.charAt(off))) {
      off++;
    }
    if (off < v.length() && v.charAt(off) == '=') {
      off++;
      while (off < v.length() && Character.isWhitespace(v.charAt(off))) {
        off++;
      }
      int start = off;
      while (off < v.length() && Character.isLetterOrDigit(v.charAt(off))) {
        off++;
      }
      return v.substring(start, off);
    }
    return "";
  }

  void add(HttpClientCacheEntry l) {
    long age = defaultMaxAge;
    String ageStr = lookupCacheControl(l.requestHeaders, "max-age");
    if (ageStr != null) {
      try {
        age = Long.parseLong(ageStr);
      } catch (NumberFormatException ex) {
        logger.warn("ignoring bad max-age: " + ageStr);
      }

    }
    ageStr = lookupCacheControl(l.responseHeaders, "max-age");
    if (ageStr != null) {
      try {
        age = Long.parseLong(ageStr);
      } catch (NumberFormatException ex) {
        logger.warn("ignoring bad max-age: " + ageStr);
      }

    }
    if (age > globalMaxAge) {
      age = globalMaxAge;
    }
    l.expiry = Instant.now().plusSeconds(age);
    logger.debug("adding entry expiry={}", l.expiry);
    cache.put(genKey(l), l);
  }

  private void expire() {
    Instant now = Instant.now();

    for (Entry<String, HttpClientCacheEntry> entry : cache.entrySet()) {
      logger.debug("test Expire now={} this={}", now, entry.getValue().expiry);
      if (now.isAfter(entry.getValue().expiry)) {
        cache.remove(entry.getKey());
      }
    }
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
