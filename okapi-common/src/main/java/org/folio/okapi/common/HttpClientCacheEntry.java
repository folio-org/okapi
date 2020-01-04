package org.folio.okapi.common;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import java.util.List;

public class HttpClientCacheEntry {

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

  HttpClientCacheEntry(HttpMethod method, String host, String url, MultiMap requestHeaders) {
    this.method = method;
    this.host = host;
    this.url = url;
    this.requestHeaders = requestHeaders;
    this.responseBody = Buffer.buffer();
  }

}
