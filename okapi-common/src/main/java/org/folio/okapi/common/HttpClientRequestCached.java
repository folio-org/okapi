package org.folio.okapi.common;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import org.apache.logging.log4j.Logger;

class HttpClientRequestCached implements HttpClientRequest {

  private final Logger logger = OkapiLogger.get();

  HttpClientRequest httpClientRequest;

  HttpClientResponse response;

  final Handler<AsyncResult<HttpClientResponse>> hndlr;

  final HttpClientCached httpClientCached;
  final String uri;
  final HttpMethod method;
  final HttpClient httpClient;
  final MultiMap headers;

  Handler<Throwable> exceptionHandler;
  Handler<Void> drainHandler;
  Handler<Void> continueHandler;
  Handler<HttpClientRequest> pushHandler;
  Boolean chunked;
  String rawMethod;
  String host;
  boolean cached;

  HttpClientRequestCached(HttpClientCached cached, HttpClient httpClient, HttpMethod method,
    String uri, Handler<AsyncResult<HttpClientResponse>> hndlr) {

    this.httpClientCached = cached;
    this.httpClient = httpClient;
    this.method = method;
    this.uri = uri;
    this.hndlr = hndlr;
    this.headers = MultiMap.caseInsensitiveMultiMap();
    this.cached = false;
  }

  private HttpClientRequest cli() {
    return cli(false);
  }

  private HttpClientRequest cli(boolean save) {
    createClientRequest(save);
    return httpClientRequest;
  }

  private void createClientRequest(boolean save) {
    logger.debug("createClientRequest save={}", save);
    if (httpClientRequest != null || cached) {
      return;
    }
    HttpClientCacheEntry ce = new HttpClientCacheEntry(method, "localhost", uri, headers);
    if (save) {
      HttpClientCacheEntry l = httpClientCached.lookup(ce);
      if (l != null) {
        cached = true;
        response = new HttpClientResponseCached(l, this);
        hndlr.handle(Future.succeededFuture(response));
        return;
      }
    }
    httpClientRequest = httpClient.requestAbs(method, uri, res -> {
      if (res.failed()) {
        hndlr.handle(res);
        return;
      }
      HttpClientResponse res1 = res.result();
      ce.responseHeaders = res1.headers();
      ce.statusCode = res1.statusCode();
      logger.debug("saving ce={} statusCode={}", ce, ce.statusCode);
      ce.cookies = res1.cookies();
      ce.httpVersion = res1.version();
      if (save) {
        ce.responseHeaders.set("X-Cache", "MISS"); // indicate cache miss
      }
      response = new HttpClientResponseSave(httpClientCached, res1, this, save ? ce : null);
      hndlr.handle(Future.succeededFuture(response));
    });
    if (host != null) {
      httpClientRequest.setHost(host);
    }
    if (chunked != null) {
      httpClientRequest.setChunked(chunked);
    }
    if (rawMethod != null) {
      httpClientRequest.setRawMethod(rawMethod);
    }
    if (exceptionHandler != null) {
      httpClientRequest.exceptionHandler(exceptionHandler);
    }
    if (drainHandler != null) {
      httpClientRequest.drainHandler(drainHandler);
    }
    if (continueHandler != null) {
      httpClientRequest.continueHandler(continueHandler);
    }
    if (pushHandler != null) {
      httpClientRequest.pushHandler(pushHandler);
    }
    httpClientRequest.headers().addAll(headers);
  }

  @Override
  public HttpClientRequest exceptionHandler(Handler<Throwable> hndlr) {
    exceptionHandler = hndlr;
    return this;
  }

  @Override
  public HttpClientRequest setWriteQueueMaxSize(int i) {
    cli().setWriteQueueMaxSize(i);
    return this;
  }

  @Override
  public HttpClientRequest drainHandler(Handler<Void> hndlr) {
    drainHandler = hndlr;
    return this;
  }

  @Override
  public HttpClientRequest setFollowRedirects(boolean bln) {
    cli().setFollowRedirects(bln);
    return this;
  }

  @Override
  public HttpClientRequest setMaxRedirects(int i) {
    cli().setMaxRedirects(i);
    return this;
  }

  @Override
  public HttpClientRequest setChunked(boolean bln) {
    chunked = bln;
    return this;
  }

  @Override
  public boolean isChunked() {
    return cli().isChunked();
  }

  @Override
  public HttpMethod method() {
    return method;
  }

  @Override
  public String getRawMethod() {
    return rawMethod;
  }

  @Override
  public HttpClientRequest setRawMethod(String string) {
    rawMethod = string;
    return this;
  }

  @Override
  public String absoluteURI() {
    return cli().absoluteURI();
  }

  @Override
  public String uri() {
    return cli().uri();
  }

  @Override
  public String path() {
    return cli().path();
  }

  @Override
  public String query() {
    return cli().query();
  }

  @Override
  public HttpClientRequest setHost(String string) {
    host = string;
    return this;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public HttpClientRequest putHeader(String string, String string1) {
    headers.add(string, string1);
    return this;
  }

  @Override
  public HttpClientRequest putHeader(CharSequence cs, CharSequence cs1) {
    headers.add(cs1, cs1);
    return this;
  }

  @Override
  public HttpClientRequest putHeader(String string, Iterable<String> itrbl) {
    headers.add(string, itrbl);
    return this;
  }

  @Override
  public HttpClientRequest putHeader(CharSequence cs, Iterable<CharSequence> itrbl) {
    headers.add(cs, itrbl);
    return this;
  }

  @Override
  public Future<Void> write(String string) {
    return cli().write(string);
  }

  @Override
  public void write(String string, Handler<AsyncResult<Void>> hndlr) {
    cli().write(string, hndlr);
  }

  @Override
  public Future<Void> write(String string, String string1) {
    return cli().write(string, string1);
  }

  @Override
  public void write(String string, String string1, Handler<AsyncResult<Void>> hndlr) {
    cli().write(string, string1, hndlr);
  }

  @Override
  public HttpClientRequest continueHandler(Handler<Void> hndlr) {
    continueHandler = hndlr;
    return this;
  }

  @Override
  public Future<HttpVersion> sendHead() {
    return cli().sendHead();
  }

  @Override
  public HttpClientRequest sendHead(Handler<AsyncResult<HttpVersion>> hndlr) {
    cli().sendHead(hndlr);
    return this;
  }

  @Override
  public Future<Void> end(String string) {
    HttpClientRequest client = cli(string.isEmpty());
    if (client == null) {
      return Future.succeededFuture();
    }
    return client.end(string);
  }

  @Override
  public void end(String string, Handler<AsyncResult<Void>> hndlr) {
    HttpClientRequest client = cli(string.isEmpty());
    if (client == null) {
      hndlr.handle(Future.succeededFuture());
      return;
    }
    client.end(string, hndlr);
  }

  @Override
  public Future<Void> end(String string, String string1) {
    return cli().end(string, string1);
  }

  @Override
  public void end(String string, String string1, Handler<AsyncResult<Void>> hndlr) {
    cli().end(string, string1, hndlr);
  }

  @Override
  public Future<Void> end(Buffer buffer) {
    HttpClientRequest client = cli(buffer.length() == 0);
    if (client == null) {
      return Future.succeededFuture();
    }
    return client.end(buffer);
  }

  @Override
  public void end(Buffer buffer, Handler<AsyncResult<Void>> hndlr) {
    cli().end(buffer, hndlr);
  }

  @Override
  public Future<Void> end() {
    HttpClientRequest client = cli(true);
    if (client == null) {
      return Future.succeededFuture();
    }
    return client.end();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> hndlr) {
    cli().end(hndlr);
  }

  @Override
  public HttpClientRequest setTimeout(long l) {
    cli().setTimeout(l);
    return this;
  }

  @Override
  public HttpClientRequest pushHandler(Handler<HttpClientRequest> hndlr) {
    pushHandler = hndlr;
    return this;
  }

  @Override
  public boolean reset(long l) {
    return cli().reset(l);
  }

  @Override
  public HttpConnection connection() {
    return cli().connection();
  }

  @Override
  public HttpClientRequest writeCustomFrame(int i, int i1, Buffer buffer) {
    cli().writeCustomFrame(i, i1, buffer);
    return this;
  }

  @Override
  public StreamPriority getStreamPriority() {
    return cli().getStreamPriority();
  }

  @Override
  public Future<Void> write(Buffer t) {
    return cli().write(t);
  }

  @Override
  public void write(Buffer t, Handler<AsyncResult<Void>> hndlr) {
    cli().write(t, hndlr);
  }

  @Override
  public boolean writeQueueFull() {
    return cli().writeQueueFull();
  }

  @Override
  public boolean isComplete() {
    return cli().isComplete();
  }

  @Override
  public Future<HttpClientResponse> onComplete(Handler<AsyncResult<HttpClientResponse>> hndlr) {
    return cli().onComplete(hndlr);
  }

  @Override
  public Handler<AsyncResult<HttpClientResponse>> getHandler() {
    return cli().getHandler();
  }

  @Override
  public HttpClientResponse result() {
    return response;
  }

  @Override
  public Throwable cause() {
    return cli().cause();
  }

  @Override
  public boolean succeeded() {
    return cli().succeeded();
  }

  @Override
  public boolean failed() {
    return cli().failed();
  }

}
