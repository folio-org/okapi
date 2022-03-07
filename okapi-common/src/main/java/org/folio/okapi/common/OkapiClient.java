package org.folio.okapi.common;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/**
 * Okapi client. Makes requests to other Okapi modules, or Okapi itself. Handles
 * all the things we need with the headers etc. Note that the client keeps a
 * list of necessary headers (which it can get from the RoutingContext, or
 * separately), so it is bound to one request, or at least one tenant. Your
 * module should not just keep one client around for everything it does.
 */
// S2245: Using pseudorandom number generators (PRNGs) is security-sensitive
@java.lang.SuppressWarnings({"squid:S2245"})
public class OkapiClient {

  private final Logger logger = OkapiLogger.get();

  private String okapiUrl;
  private WebClient webClient;
  private Map<String, String> headers;
  private int statusCode;
  private MultiMap respHeaders;
  private String reqId;
  private boolean logInfo; // t: log requests on INFO. f: on DEBUG
  private String responsebody;
  private int retryClosedCount;
  private int retryClosedWait;
  private Vertx vertx;
  private static Random rand = new Random();

  /**
   * Constructor from a vert.x ctx. That ctx contains all the headers we need.
   *
   * @param ctx routing context (using some headers from it)
   */
  public OkapiClient(RoutingContext ctx) {
    this(WebClientFactory.getWebClient(ctx.vertx()), ctx);
  }

  /**
   * Constructor from a vert.x ctx. That ctx contains all the headers we need.
   *
   * @param httpClient the client to re-use for pooling and pipe-lining
   * @param ctx routing context (using some headers from it)
   */
  public OkapiClient(HttpClient httpClient, RoutingContext ctx) {
    this(WebClient.wrap(httpClient), ctx);
  }

  /**
   * Constructor from a vert.x ctx. That ctx contains all the headers we need.
   *
   * @param webClient the client to re-use for pooling and pipe-lining
   * @param ctx routing context (using some headers from it)
   */
  public OkapiClient(WebClient webClient, RoutingContext ctx) {
    init(ctx.vertx(), webClient);
    this.okapiUrl = OkapiStringUtil.trimTrailingSlashes(
        OkapiStringUtil.removeLogCharacters(ctx.request().getHeader(XOkapiHeaders.URL)));
    for (String hdr : ctx.request().headers().names()) {
      if (hdr.startsWith(XOkapiHeaders.PREFIX)
          || hdr.equals("Accept")) {
        String hv = ctx.request().getHeader(hdr);
        headers.put(hdr, hv);
        if (hdr.equals(XOkapiHeaders.REQUEST_ID)) {
          reqId = hv;
        }
      }
    }
  }

  /**
   * Specify HTTP headers.
   *
   * @param headers headers; a value of empty removes the header
   */
  public void setHeaders(Map<String, String> headers) {
    this.headers.clear();
    if (headers != null) {
      for (Entry<String, String> e : headers.entrySet()) {
        if (e.getValue().isEmpty()) {
          this.headers.remove(e.getKey());  // We may override headers with ""
        } else {
          this.headers.put(e.getKey(), e.getValue());
        }
      }
    }
    if (this.headers.containsKey(XOkapiHeaders.REQUEST_ID)) {
      reqId = this.headers.get(XOkapiHeaders.REQUEST_ID);
    }
  }

  /**
   * Returns a read only view of the headers.
   */
  public Map<String, String> getHeaders() {
    return Collections.unmodifiableMap(headers);
  }

  /**
   * Specify OKAPI-URL for the client to use.
   *
   * @param okapiUrl URL string (such as http://localhost:1234)
   */
  public void setOkapiUrl(String okapiUrl) {
    if (okapiUrl == null) {
      throw new NullPointerException("okapiUrl");
    }
    this.okapiUrl = OkapiStringUtil.trimTrailingSlashes(okapiUrl);
  }

  /**
   * Explicit constructor.
   *
   * @param okapiUrl OKAPI URL
   * @param vertx Vert.x handle
   * @param headers may be null
   */
  public OkapiClient(String okapiUrl, Vertx vertx, Map<String, String> headers) {
    this(WebClientFactory.getWebClient(vertx), okapiUrl, vertx, headers);
  }

  /**
   * Explicit constructor.
   *
   * @param httpClient client to use
   * @param okapiUrl OKAPI URL
   * @param vertx Vert.x handle
   * @param headers may be null
   */
  public OkapiClient(HttpClient httpClient, String okapiUrl,
                     Vertx vertx, Map<String, String> headers) {
    this(WebClient.wrap(httpClient), okapiUrl, vertx, headers);
  }

  /**
   * Explicit constructor.
   *
   * @param webClient client to use
   * @param okapiUrl OKAPI URL
   * @param vertx Vert.x handle
   * @param headers may be null
   */
  public OkapiClient(WebClient webClient, String okapiUrl,
                     Vertx vertx, Map<String, String> headers) {
    init(vertx, webClient);
    setOkapiUrl(okapiUrl);
    setHeaders(headers);
  }

  private void init(Vertx vertx, WebClient webClient) {
    this.vertx = vertx;
    this.retryClosedCount = 0;
    this.retryClosedWait = 0;
    this.webClient = webClient;
    this.headers = new HashMap<>();
    respHeaders = null;
    reqId = "";
    logInfo = false;
  }

  /**
   * Enable logging of request on INFO level. Normally not the case, since Okapi
   * will log the incoming request anyway. Useful with Okapi's own requests to
   * modules, etc.
   */
  public void enableInfoLog() {
    logInfo = true;
  }

  /**
   * Disable request logging on INFO. They will still be logged on DEBUG.
   */
  public void disableInfoLog() {
    logInfo = false;
  }

  /**
   * Set up a new request-Id. Used internally, when Okapi itself makes a new
   * request to the modules, like the tenant interface.
   */
  public void newReqId(String path) {
    String newId = String.format("%06d", rand.nextInt(1000000)) + "/" + path;
    if (reqId.isEmpty()) {
      reqId = newId;
    } else {
      reqId = reqId + ";" + newId;
    }
    headers.put(XOkapiHeaders.REQUEST_ID, reqId);
  }

  /**
   * Send HTTP request.
   *
   * @param method HTTP method
   * @param path URI path
   * @param data request data (null or "" for empty)
   */
  public Future<String> request(HttpMethod method, String path, String data) {
    return request(method, path, Buffer.buffer(data == null ? "" : data));
  }

  /**
   * Send HTTP request.
   *
   * @param method HTTP method
   * @param path URI path
   * @param data request data (null or "" for empty)
   * @param fut future with response as string if successful
   */
  public void request(HttpMethod method, String path, String data,
                      Handler<ExtendedAsyncResult<String>> fut) {

    request(method, path, Buffer.buffer(data == null ? "" : data), fut);
  }

  /**
   * Send HTTP request.
   *
   * @param method HTTP method
   * @param path URI path
   * @param data request data (null or "" for empty)
   * @param fut future with response as string if successful
   */
  public void request(HttpMethod method, String path, Buffer data,
                      Handler<ExtendedAsyncResult<String>> fut) {

    request(method, path, data)
        .onComplete(result -> fut.handle(ExtendedAsyncResult.from(result)));
  }

  /**
   * Send HTTP request.
   *
   * @param method HTTP method
   * @param path URI path
   * @param data request data
   */
  public Future<String> request(HttpMethod method, String path, Buffer data) {
    if (this.okapiUrl == null) {
      return Future.failedFuture(
          new ErrorTypeException(ErrorType.INTERNAL, "OkapiClient: No OkapiUrl specified"));
    }
    return Future.future(promise -> request1(method, path, data, promise));
  }

  private void request1(HttpMethod method, String path, Buffer data, Promise<String> promise) {
    request2(method, path, data)
        .onSuccess(s -> promise.tryComplete(s))
        .onFailure(e -> {
          if (e.getCause() == null) {
            promise.tryFail(e);
            return;
          }
          if (retryClosedCount <= 0) {
            promise.tryFail(new ErrorTypeException(ErrorType.INTERNAL, e.getCause()));
            return;
          }
          retryClosedCount--;
          vertx.setTimer(retryClosedWait, x -> request1(method, path, data, promise));
        });
  }

  private Future<String> request2(HttpMethod method, String path, Buffer data) {
    String url = this.okapiUrl + path;
    String tenant = headers.getOrDefault(XOkapiHeaders.TENANT, "-");
    respHeaders = null;
    logger.log(logInfo ? Level.INFO : Level.DEBUG,
        () -> reqId + " REQ okapiClient " + tenant + " " + method.toString() + " " + url);
    long t1 = logger.isInfoEnabled() ? System.nanoTime() : 0;
    HttpRequest<Buffer> bufferHttpRequest = webClient.requestAbs(method, url);
    bufferHttpRequest.headers().addAll(headers);
    return bufferHttpRequest.sendBuffer(data)
        .recover(e -> {
          return Future.failedFuture(new ErrorTypeException(ErrorType.ANY, e));
        }).compose(response -> {
          statusCode = response.statusCode();
          if (logger.isInfoEnabled()) {
            long ns = System.nanoTime() - t1;
            String logResMsg = reqId
                + " RES " + statusCode + " " + ns / 1000 + "us "
                + "okapiClient " + url;
            logger.log(logInfo ? Level.INFO : Level.DEBUG, logResMsg);
          }
          responsebody = response.bodyAsString();
          respHeaders = response.headers();
          if (statusCode >= 200 && statusCode <= 299) {
            return Future.succeededFuture(responsebody);
          }
          ErrorType errorType;
          if (statusCode == 404) {
            errorType = ErrorType.NOT_FOUND;
          } else if (statusCode == 403) {
            errorType = ErrorType.FORBIDDEN;
          } else if (statusCode >= 500) {
            errorType = ErrorType.INTERNAL;
          } else {
            errorType = ErrorType.USER;
          }
          Exception e = new ErrorTypeException(errorType, statusCode + ": " + responsebody);
          return Future.failedFuture(e);
        });
  }

  public Future<String> post(String path, String data) {
    return request(HttpMethod.POST, path, data);
  }

  public void post(String path, String data,
      Handler<ExtendedAsyncResult<String>> fut) {
    request(HttpMethod.POST, path, data, fut);
  }

  public Future<String> get(String path) {
    return request(HttpMethod.GET, path, Buffer.buffer());
  }

  public void get(String path,
                  Handler<ExtendedAsyncResult<String>> fut) {
    request(HttpMethod.GET, path, "", fut);
  }

  public Future<String> delete(String path) {
    return request(HttpMethod.DELETE, path, Buffer.buffer());
  }

  public void delete(String path,
                     Handler<ExtendedAsyncResult<String>> fut) {
    request(HttpMethod.DELETE, path, "", fut);
  }

  public Future<String> head(String path) {
    return request(HttpMethod.HEAD, path, Buffer.buffer());
  }

  public void head(String path,
      Handler<ExtendedAsyncResult<String>> fut) {
    request(HttpMethod.HEAD, path, "", fut);
  }

  public String getOkapiUrl() {
    return okapiUrl;
  }

  /**
   * Get the response headers. May be null
   */
  public MultiMap getRespHeaders() {
    return respHeaders;
  }

  /**
   * Get the response body. Same string as returned in the callback from
   * request().
   */
  public String getResponsebody() {
    return responsebody;
  }


  /**
   * Returns the HTTP status code of last request.
   *
   * @return HTTP status
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * Get the Okapi authentication token. From the X-Okapi-Token header.
   *
   * @return the token, or null if not defined.
   */
  public String getOkapiToken() {
    return headers.get(XOkapiHeaders.TOKEN);
  }

  public void setClosedRetry(int msecs) {
    retryClosedCount = msecs > 0 ? 10 : 0;
    retryClosedWait = msecs / 10;
  }

  /**
   * Set the Okapi authentication token. Overrides the auth token.
   * Should normally not be needed, but can be used in some special cases.
   *
   * @param token value to be used in the HTTP X-Okapi-Token header
   */
  public void setOkapiToken(String token) {
    headers.put(XOkapiHeaders.TOKEN, token);
  }
}
