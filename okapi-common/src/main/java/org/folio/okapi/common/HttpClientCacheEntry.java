package org.folio.okapi.common;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import java.time.Instant;
import java.util.List;

class HttpClientCacheEntry {

  final HttpMethod method;
  final String cacheUri;
  final MultiMap requestHeaders;
  String statusMessage;
  int statusCode;
  MultiMap responseHeaders;
  MultiMap trailers;
  Buffer responseBody;
  List<String> cookies;
  HttpVersion httpVersion;
  Instant expiry;
  int hitCount;

  HttpClientCacheEntry(HttpMethod method, String cacheUri,
    MultiMap requestHeaders) {

    this.method = method;
    this.cacheUri = cacheUri;
    this.requestHeaders = requestHeaders;
    this.responseBody = null;
    this.hitCount = 0;
  }

}
