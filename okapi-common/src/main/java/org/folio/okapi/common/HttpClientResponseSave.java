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

class HttpClientResponseSave implements HttpClientResponse {
  private final Logger logger = OkapiLogger.get();

  final HttpClientResponse response;
  final HttpClientRequest request;

  HttpClientCacheEntry cacheEntry; // null if not saving
  Handler<Buffer> handler;
  Handler<Void> endHandler;
  Promise<Buffer> bodyPromise;
  Buffer responseBody;

  HttpClientResponseSave(HttpClientCached httpClientCached,
    HttpClientResponse httpClientResponse, HttpClientRequest httpClientRequest,
    HttpClientCacheEntry ce) {
    response = httpClientResponse;
    request = httpClientRequest;
    cacheEntry = ce;
    response.handler(res -> {
      if (responseBody == null) {
        responseBody = Buffer.buffer();
      }
      if (bodyPromise != null) {
        // if body / bodyHandler save all in cache
        responseBody.appendBuffer(res);
        // otherwise only save up to maxBodySize
      } else if (responseBody.length() + res.length() < httpClientCached.getMaxBodySize()) {
        responseBody.appendBuffer(res);
      } else {
        // too large buffer.. disable cache saving
        cacheEntry = null;
      }

      if (handler != null) {
        handler.handle(res);
      }
    });
    response.endHandler(res -> {
      if (cacheEntry != null) {
        logger.debug("saving entry statusCode={} responseBody={}", ce.statusCode, responseBody);
        cacheEntry.responseBody = responseBody;
        cacheEntry.trailers = response.trailers();
        httpClientCached.add(cacheEntry);
      }
      if (bodyPromise != null && responseBody != null) {
        bodyPromise.complete(responseBody);
      }
      if (endHandler != null) {
        endHandler.handle(res);
      }
    });
  }

  @Override
  public HttpClientResponse fetch(long l) {
    response.fetch(l);
    return this;
  }

  @Override
  public HttpClientResponse resume() {
    response.resume();
    return this;
  }

  @Override
  public HttpClientResponse exceptionHandler(Handler<Throwable> hndlr) {
    response.exceptionHandler(hndlr);
    return this;
  }

  @Override
  public HttpClientResponse handler(Handler<Buffer> hndlr) {
    handler = hndlr;
    return this;
  }

  @Override
  public HttpClientResponse pause() {
    response.pause();
    return this;
  }

  @Override
  public HttpClientResponse endHandler(Handler<Void> hndlr) {
    endHandler = hndlr;
    bodyPromise = null;
    return this;
  }

  @Override
  public HttpVersion version() {
    return response.version();
  }

  @Override
  public int statusCode() {
    return response.statusCode();
  }

  @Override
  public String statusMessage() {
    return response.statusMessage();
  }

  @Override
  public MultiMap headers() {
    return response.headers();
  }

  @Override
  public String getHeader(String string) {
    return response.getHeader(string);
  }

  @Override
  public String getHeader(CharSequence cs) {
    return response.getHeader(cs);
  }

  @Override
  public String getTrailer(String string) {
    return response.getTrailer(string);
  }

  @Override
  public MultiMap trailers() {
    return response.trailers();
  }

  @Override
  public List<String> cookies() {
    return response.cookies();
  }

  @Override
  public HttpClientResponse bodyHandler(Handler<Buffer> hndlr) {
    bodyPromise = Promise.promise();
    bodyPromise.future().onSuccess(hndlr);

    endHandler = null;
    handler = null;
    return this;
  }

  @Override
  public Future<Buffer> body() {
    bodyPromise = Promise.promise();
    endHandler = null;
    handler = null;
    return bodyPromise.future();
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
