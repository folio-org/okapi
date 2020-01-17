package org.folio.okapi.common;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.logging.log4j.Logger;

class HttpClientRequestCached implements HttpClientRequest {

  private final Logger logger = OkapiLogger.get();

  HttpClientRequest httpClientRequest;

  HttpClientResponse response;

  final Handler<AsyncResult<HttpClientResponse>> hndlr;

  final HttpClientCached httpClientCached;
  final String absoluteUri;
  final HttpMethod method;
  final HttpClient httpClient;
  final MultiMap headers;

  Handler<Throwable> exceptionHandler;
  Handler<Void> drainHandler;
  Handler<Void> continueHandler;
  Handler<HttpClientRequest> pushHandler;
  boolean chunked;
  String rawMethod;
  String cacheUri;
  String host;
  int port;
  boolean cached;
  boolean succeeded;
  Throwable cause;
  Long timeout;
  Integer writeQueueMaxSize;
  Integer maxRedirects;
  Boolean followRedirects;
  final URL url;

  HttpClientRequestCached(HttpClientCached cached, HttpClient httpClient, HttpMethod method,
    String absoluteUri, String cacheUri, Handler<AsyncResult<HttpClientResponse>> hndlr) {

    this.httpClientCached = cached;
    this.httpClient = httpClient;
    this.method = method;
    this.absoluteUri = absoluteUri;

    try {
      this.url = new URL(absoluteUri);
    } catch (MalformedURLException ex) {
      throw new VertxException("bad URL " + absoluteUri + ": " + ex.getMessage());
    }
    this.cacheUri = cacheUri;
    this.hndlr = hndlr;
    this.headers = MultiMap.caseInsensitiveMultiMap();
    this.cached = false;
    this.succeeded = true;
    this.chunked = false;
  }

  private HttpClientRequest cli() {
    return cli(false);
  }

  private HttpClientRequest cli(boolean save) {
    // no revalidation so it means no save/lookup
    if (httpClientCached.lookupCacheControl(headers, "no-cache") != null
      || httpClientCached.lookupCacheControl(headers, "no-store") != null) {
      save = false;
    }
    createClientRequest(save);
    return httpClientRequest;
  }

  private void createClientRequest(boolean save) {
    logger.debug("createClientRequest save={}", save);
    if (httpClientRequest != null) {
      return;
    }
    HttpClientCacheEntry ce = new HttpClientCacheEntry(method, cacheUri, headers);
    if (save) {
      HttpClientCacheEntry l = httpClientCached.lookup(ce);
      if (l != null) {
        cached = true;
        response = new HttpClientResponseCached(l, this);
        hndlr.handle(Future.succeededFuture(response));
        return;
      }
    }
    httpClientRequest = httpClient.requestAbs(method, absoluteUri, res -> {
      boolean store = save;
      succeeded = res.succeeded();
      if (res.failed()) {
        cause = res.cause();
        hndlr.handle(res);
        return;
      }
      HttpClientResponse res1 = res.result();
      ce.responseHeaders = res1.headers();
      ce.statusCode = res1.statusCode();
      if (!httpClientCached.cacheStatuses().contains(ce.statusCode)) {
        store = false;
      }
      logger.debug("saving ce={} statusCode={}", ce, ce.statusCode);
      ce.cookies = res1.cookies();
      ce.httpVersion = res1.version();
      ce.statusMessage = res1.statusMessage();
      if (store && (httpClientCached.lookupCacheControl(res1.headers(), "no-cache") != null
        || httpClientCached.lookupCacheControl(res1.headers(), "no-store") != null)) {
        store = false;
      }
      if (store) {
        ce.responseHeaders.set("X-Cache", "MISS"); // indicate cache in use and MISS
      }
      response = new HttpClientResponseSave(httpClientCached, res1, this, store ? ce : null);
      hndlr.handle(Future.succeededFuture(response));
    });
    httpClientRequest.headers().addAll(headers);
    if (timeout != null) {
      httpClientRequest.setTimeout(timeout);
    }
    if (maxRedirects != null) {
      httpClientRequest.setMaxRedirects(maxRedirects);
    }
    if (followRedirects != null) {
      httpClientRequest.setFollowRedirects(followRedirects);
    }
    if (writeQueueMaxSize != null) {
      httpClientRequest.setWriteQueueMaxSize(writeQueueMaxSize);
    }
    if (host != null) {
      httpClientRequest.setHost(host);
    }
    httpClientRequest.setChunked(chunked);
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
  }

  @Override
  public HttpClientRequest exceptionHandler(Handler<Throwable> hndlr) {
    exceptionHandler = hndlr;
    return this;
  }

  @Override
  public HttpClientRequest setWriteQueueMaxSize(int i) {
    writeQueueMaxSize = i;
    return this;
  }

  @Override
  public HttpClientRequest drainHandler(Handler<Void> hndlr) {
    drainHandler = hndlr;
    return this;
  }

  @Override
  public HttpClientRequest setFollowRedirects(boolean bln) {
    followRedirects = bln;
    return this;
  }

  @Override
  public HttpClientRequest setMaxRedirects(int i) {
    maxRedirects = i;
    return this;
  }

  @Override
  public HttpClientRequest setChunked(boolean bln) {
    chunked = bln;
    return this;
  }

  @Override
  public boolean isChunked() {
    return chunked;
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
    return absoluteUri;
  }

  @Override
  public String uri() {
    String uri = url.getPath();
    if (url.getQuery() != null) {
      return uri + "?" + url.getQuery();
    }
    return uri;
  }

  @Override
  public String path() {
    return url.getPath();
  }

  @Override
  public String query() {
    return url.getQuery();
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
  public HttpClientRequest putHeader(String name, String value) {
    headers.add(name, value);
    return this;
  }

  @Override
  public HttpClientRequest putHeader(CharSequence name, CharSequence value) {
    headers.add(name, value);
    return this;
  }

  @Override
  public HttpClientRequest putHeader(String name, Iterable<String> values) {
    headers.add(name, values);
    return this;
  }

  @Override
  public HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values) {
    headers.add(name, values);
    return this;
  }

  @Override
  public Future<Void> write(String chunk) {
    return cli().write(chunk);
  }

  @Override
  public void write(String string, Handler<AsyncResult<Void>> hndlr) {
    cli().write(string, hndlr);
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    return cli().write(chunk, enc);
  }

  @Override
  public void write(String chunk, String enc, Handler<AsyncResult<Void>> hndlr) {
    cli().write(chunk, enc, hndlr);
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
  public Future<Void> end(String chunk) {
    HttpClientRequest client = cli(chunk.isEmpty());
    if (client == null) {
      return Future.succeededFuture();
    }
    return client.end(chunk);
  }

  @Override
  public void end(String chunk, Handler<AsyncResult<Void>> hndlr) {
    HttpClientRequest client = cli(chunk.isEmpty());
    if (client == null) {
      hndlr.handle(Future.succeededFuture());
      return;
    }
    client.end(chunk, hndlr);
  }

  @Override
  public Future<Void> end(String chunk, String enc) {
    return cli().end(chunk, enc);
  }

  @Override
  public void end(String chunk, String enc, Handler<AsyncResult<Void>> hndlr) {
    cli().end(chunk, enc, hndlr);
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
    HttpClientRequest client = cli(true);
    if (client == null) {
      hndlr.handle(Future.succeededFuture());
      return;
    }
    client.end(hndlr);
  }

  @Override
  public HttpClientRequest setTimeout(long l) {
    timeout = l;
    return this;
  }

  @Override
  public HttpClientRequest pushHandler(Handler<HttpClientRequest> hndlr) {
    pushHandler = hndlr;
    return this;
  }

  @Override
  public boolean reset(long l) {
    throw new VertxException("HttpClientCached: reset not implemented");
  }

  @Override
  public HttpConnection connection() {
    if (httpClientRequest == null) {
      return null;
    }
    return httpClientRequest.connection();
  }

  @Override
  public HttpClientRequest writeCustomFrame(int i, int i1, Buffer buffer) {
    throw new VertxException("HttpClientCached: writeCustomFrame not implemented");
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
    if (httpClientRequest == null) {
      return false;
    }
    return cli().writeQueueFull();
  }

  @Override
  public boolean isComplete() {
    return httpClientRequest != null;
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
    return cause;
  }

  @Override
  public boolean succeeded() {
    return succeeded;
  }

  @Override
  public boolean failed() {
    return !succeeded;
  }

}
