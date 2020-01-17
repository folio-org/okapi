package org.folio.okapi.common;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.Logger;

/**
 * HTTP client with cache .. Can be used in most case as a replacement of Vert.x
 * {@link io.vertx.core.http.HttpClient}
 *
 * The following conditions must be met for a request/response to be saved:
 *
 * <ul>
 * <li>Method is one of <pre>GET</pre>, <pre>HEAD</pre> {@link #cacheMethods() }</li>
 * <li>Request header cache-control does not hold <pre>no-store</pre> / <pre>no-cache</pre></li>
 * <li>Request body is empty</li>
 * <li>Response status is 200 or 202 (currently can not be configured)</li>
 * <li>Response body is less than 8K {@link #setMaxBodySize(int)</li>
 * <li>Response header cache-control does not hold no-store / no-cache</li>
 * </ul>
 *
 * If a request is used for cache lookup , the response header
 * X-Cache is returned as part of the response. It is either
 * <pre>MISS</pre> or <pre>HIT: n</pre> where n denotes the number of hits.
 *
 * Cached results are mached against cacheUri (which defaults to absoluteUri)
 * and all headers, except those that are ignored . By default only "Date" is
 * ignored. A header to be ignored can be added with {@link #addIgnoreHeader(java.lang.String) and removed with
 * {@link #removeIgnoreHeader(java.lang.String)}.
 *
 * Expiry.. The cache-control <pre>max-age</pre> primarily controls how long a cached
 * entry should be cached.. If that is not present, the <pre>Expires</pre> header from the
 * response is used. If none of those are present, the default max-age takes
 * effect. In all cases, to avoid extremely long caching, the setting
 * globalMaxAge serves as a max-age.. By default, this is 3600 seconds and
 * can be adjusted with {@link #globalMaxAge(long) }.
 *
 * The client does not perform validation with ETags. So in cases where a client
 * would otherwise perform validation, the HTTP request will be re-executed.
 * For this reason no-store and no-cache are treated the same way.
 *
 * {@linktourl https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html}
 *
 */
public class HttpClientCached {

  private final Logger logger = OkapiLogger.get();

  private final HttpClient httpClient;

  private List<HttpMethod> cacheMethods = new LinkedList<>();

  private final Map<String, HttpClientCacheEntry> cache = new HashMap<>();
  private Set<String> cacheIgnoreHeaders = new TreeSet<>();

  private static final int DEFAULT_MAX_BODY_SIZE = 8192;

  private int maxBodySize = DEFAULT_MAX_BODY_SIZE;

  private long globalMaxAge = 3600;
  private long defaultMaxAge = 60;

  /**
   * Create a client with caching. This serves as a factory of requests Each
   * client has its own cache (NOT static)
   *
   * @param httpClient Vert.x httpClient
   */
  public HttpClientCached(HttpClient httpClient) {
    cacheIgnoreHeaders.add("date");
    this.httpClient = httpClient;
    this.cacheMethods.add(HttpMethod.GET);
    this.cacheMethods.add(HttpMethod.HEAD);
  }

  public List<HttpMethod> cacheMethods() {
    return this.cacheMethods;
  }

  /**
   * Sets a an upper limit on how long the client will cache results.
   * Default is 1 hour / 3600 seconds.
   *
   * @param seconds age in seconds
   * @return client (fluent)
   */
  public HttpClientCached globalMaxAge(long seconds) {
    globalMaxAge = seconds;
    return this;
  }

  /**
   * Sets default age for responses .. Used if neither the client , nor the
   * server specifies max-age. Default is 60 seconds.
   *
   * @param seconds age in seconds
   * @return client (fluent)
   */
  public HttpClientCached defaultMaxAge(long seconds) {
    defaultMaxAge = seconds;
    return this;
  }

  /**
   * Specifies a request header that will be ignored in the cache match. By
   * default only "Date" is ignored, but more may be added.
   *
   * @param h the header name
   * @return client (fluent)
   */
  public HttpClientCached addIgnoreHeader(String h) {
    this.cacheIgnoreHeaders.add(h.toLowerCase());
    return this;
  }

  /**
   * Specifies a request header that will not be ignored in the cache match. By
   * default only "Date" is ignored, but more may be added.
   *
   * @param h the header name
   * @return client (fluent)
   */
  public HttpClientCached removeIgnoreHeader(String h) {
    this.cacheIgnoreHeaders.remove(h.toLowerCase());
    return this;
  }

  /**
   * Return maximum number of response body bytes save for a cache entry
   * @return bytes
   */
  public int getMaxBodySize() {
    return maxBodySize;
  }

  /**
   * Sets maximum number of response body bytes save for a cache entry
   * @return client (fluent)
   */
  public HttpClientCached setMaxBodySize(int maxBodySize) {
    this.maxBodySize = maxBodySize;
    return this;
  }

  /**
   * Calls Just like {@link io.vertx.core.http.HttpClient#close}
   */
  public void close() {
    httpClient.close();
  }

  /**
   * Just like {@link io.vertx.core.http.HttpClient#requestAbs}
   *
   * @param method method for request
   * @param absoluteUri full URI
   * @param hndlr handler to be called with response
   * @return HttpClientRequest
   */
  public HttpClientRequest requestAbs(HttpMethod method, String absoluteUri,
    Handler<AsyncResult<HttpClientResponse>> hndlr) {

    return requestAbs(method, absoluteUri, absoluteUri, hndlr);
  }

  /**
   * Just like {@link io.vertx.core.http.HttpClient#requestAbs} but with a
   * cache key for the absolute URI .. This is useful if request is known to
   * produce same result on different URIs.. This is case for Okapi where
   * module instances may be located on different hosts.
   *
   * @param method method for request
   * @param absoluteUri full URI
   * @param cacheUri the cache URI
   * @param hndlr handler to be called with response
   * @return HttpClientRequest
   */
  public HttpClientRequest requestAbs(HttpMethod method, String absoluteUri,
    String cacheUri, Handler<AsyncResult<HttpClientResponse>> hndlr) {

    if (cacheMethods.contains(method)) {
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
    for (String v : headers.getAll("Cache-Control")) {
      String l = lookupCacheControl(v, component);
      if (l != null) {
        return l;
      }
    }
    return null;
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

    // consider response max-age first
    String tmp = lookupCacheControl(l.responseHeaders, "max-age");
    if (tmp != null) {
      try {
        age = Long.parseLong(tmp);
      } catch (NumberFormatException ex) {
        logger.warn("ignoring bad max-age: {}", tmp);
      }
    } else {
      // then consider request max-age
      tmp = lookupCacheControl(l.requestHeaders, "max-age");
      if (tmp != null) {
        try {
          age = Long.parseLong(tmp);
        } catch (NumberFormatException ex) {
          logger.warn("ignoring bad max-age: {}", tmp);
        }
      } else {
        // if no max-age, consider Expires
        tmp = l.responseHeaders.get("Expires");
        if (tmp != null) {
          try {
            ZonedDateTime zdt = ZonedDateTime.parse(tmp, DateTimeFormatter.RFC_1123_DATE_TIME);
            Instant expire = zdt.toInstant();
            Instant now = Instant.now();
            age = expire.getEpochSecond() - now.getEpochSecond();
            if (age < 0) {
              age = 0;
            }
          } catch (DateTimeParseException ex) {
            logger.warn("ignoring bad Expires: {}", tmp);
          }
        }
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

}
