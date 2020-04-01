package org.folio.okapi.common;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
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
  private HttpClient httpClient;
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
    init(ctx.vertx(), ctx.vertx().createHttpClient());
    this.okapiUrl = ctx.request().getHeader(XOkapiHeaders.URL);
    this.okapiUrl = OkapiStringUtil.trimTrailingSlashes(this.okapiUrl);
    for (String hdr : ctx.request().headers().names()) {
      if (hdr.startsWith(XOkapiHeaders.PREFIX)
          || hdr.startsWith("Accept")) {
        String hv = ctx.request().getHeader(hdr);
        headers.put(hdr, hv);
        if (hdr.equals(XOkapiHeaders.REQUEST_ID)) {
          reqId = hv;
        }
      }
    }
  }

  /**
   * specify HTTP headers.
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
   * Specify OKAPI-URL for the client to use.
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
    this(vertx.createHttpClient(), okapiUrl, vertx, headers);
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
    init(vertx, httpClient);
    setOkapiUrl(okapiUrl);
    setHeaders(headers);
  }

  private void init(Vertx vertx, HttpClient httpClient) {
    this.vertx = vertx;
    this.retryClosedCount = 0;
    this.retryClosedWait = 0;
    this.httpClient = httpClient;
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
   * @param method HTTP method
   * @param path URI path
   * @param data request data
   * @param fut future with response as string is successful
   */
  public void request(HttpMethod method, String path, Buffer data,
                      Handler<ExtendedAsyncResult<String>> fut) {

    if (this.okapiUrl == null) {
      fut.handle(new Failure<>(ErrorType.INTERNAL, "OkapiClient: No OkapiUrl specified"));
      return;
    }
    HttpClientRequest req = request1(method, path, res -> {
      if (res.failed() && res.getType() == ErrorType.ANY) {
        if (retryClosedCount > 0) {
          retryClosedCount--;
          vertx.setTimer(retryClosedWait, res1
              -> request(method, path, data, fut));
        } else {
          fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        }
      } else {
        fut.handle(res);
      }
    });
    req.end(data);
  }

  private HttpClientRequest request1(HttpMethod method, String path,
                                     Handler<ExtendedAsyncResult<String>> fut) {

    String url = this.okapiUrl + path;
    String tenant = "-";
    if (headers.containsKey(XOkapiHeaders.TENANT)) {
      tenant = headers.get(XOkapiHeaders.TENANT);
    }

    respHeaders = null;
    String logReqMsg = reqId + " REQ " + "okapiClient " + tenant + " "
        + method.toString() + " " + url;
    if (logInfo) {
      logger.info(logReqMsg);
    } else {
      logger.debug(logReqMsg);
    }
    long t1 = System.nanoTime();
    HttpClientRequest req = httpClient.requestAbs(method, url, req1 -> {
      if (req1.failed()) {
        fut.handle(new Failure<>(ErrorType.ANY, req1.cause()));
        return;
      }
      HttpClientResponse reqres = req1.result();
      statusCode = reqres.statusCode();
      long ns = System.nanoTime() - t1;
      String logResMsg = reqId
          + " RES " + statusCode + " " + ns / 1000 + "us "
          + "okapiClient " + url;
      if (logInfo) {
        logger.info(logResMsg);
      } else {
        logger.debug(logResMsg);
      }
      final Buffer buf = Buffer.buffer();
      respHeaders = reqres.headers();
      reqres.handler(b -> {
        logger.debug("{} OkapiClient Buffering response {}", reqId, b);
        buf.appendBuffer(b);
      });
      reqres.endHandler(e -> {
        responsebody = buf.toString();
        if (statusCode >= 200 && statusCode <= 299) {
          fut.handle(new Success<>(responsebody));
        } else {
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
          fut.handle(new Failure<>(errorType, Integer.toString(statusCode) + ": " + responsebody));
        }
      });
      reqres.exceptionHandler(e -> {
        logger.warn("{} OkapiClient exception 1 :", reqId, e);
        fut.handle(new Failure<>(ErrorType.INTERNAL, e.getMessage()));
      });
    });
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      logger.debug("{} OkapiClient: adding header {}: {}", reqId, entry.getKey(), entry.getValue());
    }
    req.headers().addAll(headers);
    return req;
  }

  public void post(String path, String data,
                   Handler<ExtendedAsyncResult<String>> fut) {
    request(HttpMethod.POST, path, data, fut);
  }

  public void get(String path,
                  Handler<ExtendedAsyncResult<String>> fut) {
    request(HttpMethod.GET, path, "", fut);
  }

  public void delete(String path,
                     Handler<ExtendedAsyncResult<String>> fut) {
    request(HttpMethod.DELETE, path, "", fut);
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
   *
   * @return
   */
  public MultiMap getRespHeaders() {
    return respHeaders;
  }

  /**
   * Get the response body. Same string as returned in the callback from
   * request().
   *
   * @return
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

  /**
   * Close HTTP connection for client.
   */
  public void close() {
    if (httpClient != null) {
      httpClient.close();
      httpClient = null;
    }
  }
}
