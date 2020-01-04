package org.folio.okapi.common;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import java.util.List;

class HttpClientCacheEntry {

  final HttpMethod method;
  final String host;
  final String url;
  final MultiMap requestHeaders;
  int statusCode;
  MultiMap responseHeaders;
  MultiMap trailers;
  final Buffer responseBody;
  List<String> cookies;
  HttpVersion httpVersion;

  HttpClientCacheEntry(HttpMethod method, String vHost, String url, MultiMap requestHeaders) {
    this.method = method;
    this.host = vHost;
    this.url = url;
    this.requestHeaders = requestHeaders;
    this.responseBody = Buffer.buffer();
  }

}
