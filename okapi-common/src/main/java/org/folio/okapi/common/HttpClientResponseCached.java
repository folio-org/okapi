package org.folio.okapi.common;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import java.util.List;
import org.apache.logging.log4j.Logger;

class HttpClientResponseCached implements HttpClientResponse {

  private final Logger logger = OkapiLogger.get();

  final HttpClientCacheEntry cacheEntry;
  final HttpClientRequest request;
  final MultiMap responseHeaders;
  boolean paused;
  Handler<Buffer> handler;
  Handler<Void> endHandler;
  Promise<Buffer> bodyPromise;
  final Buffer fullBody;

  HttpClientResponseCached(HttpClientCacheEntry ce, HttpClientRequest httpClientRequest) {
    logger.debug("ce={} ce.statusCode={}", ce, ce.statusCode);
    cacheEntry = ce;
    responseHeaders = MultiMap.caseInsensitiveMultiMap();
    responseHeaders.addAll(ce.responseHeaders);
    responseHeaders.set(XOkapiHeaders.CACHE, "HIT: " + ce.hitCount);
    paused = false;
    this.fullBody = ce.responseBody != null ? ce.responseBody : Buffer.buffer();
    this.request = httpClientRequest;
  }

  @Override
  public HttpClientResponse fetch(long l) {
    return this;
  }

  @Override
  public HttpClientResponse resume() {
    paused = false;
    if (handler != null) {
      if (cacheEntry.responseBody != null) {
        handler.handle(cacheEntry.responseBody);
      }
      handler = null;
    }
    if (bodyPromise != null) {
      bodyPromise.complete(fullBody);
      bodyPromise = null;
    }
    if (endHandler != null) {
      endHandler.handle(null);
      endHandler = null;
    }
    return this;
  }

  @Override
  public HttpClientResponse exceptionHandler(Handler<Throwable> hndlr) {
    return this;
  }

  @Override
  public HttpClientResponse handler(Handler<Buffer> hndlr) {
    if (paused) {
      handler = hndlr;
    } else {
      if (cacheEntry.responseBody != null) {
        hndlr.handle(cacheEntry.responseBody);
      }
    }
    return this;
  }

  @Override
  public HttpClientResponse pause() {
    paused = true;
    return this;
  }

  @Override
  public HttpClientResponse endHandler(Handler<Void> hndlr) {
    bodyPromise = null;
    if (paused) {
      endHandler = hndlr;
    } else {
      hndlr.handle(null);
    }
    return this;
  }

  @Override
  public HttpVersion version() {
    return cacheEntry.httpVersion;
  }

  @Override
  public int statusCode() {
    return cacheEntry.statusCode;
  }

  @Override
  public String statusMessage() {
    return cacheEntry.statusMessage;
  }

  @Override
  public MultiMap headers() {
    return responseHeaders;
  }

  @Override
  public String getHeader(String string) {
    return responseHeaders.get(string);
  }

  @Override
  public String getHeader(CharSequence cs) {
    return responseHeaders.get(cs);
  }

  @Override
  public String getTrailer(String string) {
    return cacheEntry.trailers.get(string);
  }

  @Override
  public MultiMap trailers() {
    return cacheEntry.trailers;
  }

  @Override
  public List<String> cookies() {
    return cacheEntry.cookies;
  }

  @Override
  public HttpClientResponse bodyHandler(Handler<Buffer> hndlr) {
    endHandler = null;
    handler = null;
    if (paused) {
      bodyPromise = Promise.promise();
      bodyPromise.future().onSuccess(hndlr);
    } else {
      hndlr.handle(fullBody);
    }
    return this;
  }

  @Override
  public Future<Buffer> body() {
    endHandler = null;
    handler = null;
    if (paused) {
      bodyPromise = Promise.promise();
      return bodyPromise.future();
    } else {
      return Future.succeededFuture(fullBody);
    }
  }

  @Override
  public HttpClientResponse customFrameHandler(Handler<HttpFrame> hndlr) {
    throw new VertxException("HttpClientCached: customFrameHandler not implemented");
  }

  @Override
  public HttpClientRequest request() {
    return request;
  }

  @Override
  public HttpClientResponse streamPriorityHandler(Handler<StreamPriority> hndlr) {
    throw new VertxException("HttpClientCached: steamPriorityHandler not implemented");
  }

}
