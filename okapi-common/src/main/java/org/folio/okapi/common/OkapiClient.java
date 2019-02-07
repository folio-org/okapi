package org.folio.okapi.common;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import static org.folio.okapi.common.ErrorType.*;

/**
 * Okapi client. Makes requests to other Okapi modules, or Okapi itself. Handles
 * all the things we need with the headers etc. Note that the client keeps a
 * list of necessary headers (which it can get from the RoutingContext, or
 * separately), so it is bound to one request, or at least one tenant. Your
 * module should not just keep one client around for everything it does.
 *
 * @author heikki
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
   * @param ctx
   */
  public OkapiClient(RoutingContext ctx) {
    init(ctx.vertx());
    this.okapiUrl = ctx.request().getHeader(XOkapiHeaders.URL);
    if (this.okapiUrl != null) {
      this.okapiUrl = okapiUrl.replaceAll("/+$", ""); // no trailing slash
    }
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

  public void setOkapiUrl(String okapiUrl) {
    this.okapiUrl = okapiUrl.replaceAll("/+$", ""); // no trailing slash
  }

  /**
   * Explicit constructor.
   *
   * @param okapiUrl
   * @param vertx
   * @param headers may be null
   */
  public OkapiClient(String okapiUrl, Vertx vertx, Map<String, String> headers) {
    init(vertx);
    setOkapiUrl(okapiUrl);
    setHeaders(headers);
    respHeaders = null;
  }

  private void init(Vertx vertx) {
    this.vertx = vertx;
    this.retryClosedCount = 0;
    this.retryClosedWait = 0;
    this.httpClient = vertx.createHttpClient();
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

  public void request(HttpMethod method, String path, String data,
    Handler<ExtendedAsyncResult<String>> fut) {

    request(method, path, Buffer.buffer(data == null ? "" : data), fut);
  }

  public void request(HttpMethod method, String path, Buffer data,
    Handler<ExtendedAsyncResult<String>> fut) {

    if (this.okapiUrl == null) {
      fut.handle(new Failure<>(INTERNAL, "OkapiClient: No OkapiUrl specified"));
      return;
    }
    HttpClientRequest req = request1(method, path, res -> {
      if (res.failed() && res.getType() == ANY) {
        if (retryClosedCount > 0) {
          retryClosedCount--;
          vertx.setTimer(retryClosedWait, res1
            -> request(method, path, data, fut));
        } else {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
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
    HttpClientRequest req = httpClient.requestAbs(method, url, reqres -> {
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
        logger.debug(reqId + " OkapiClient Buffering response " + b.toString());
        buf.appendBuffer(b);
      });
      reqres.endHandler(e -> {
        responsebody = buf.toString();
        if (statusCode >= 200 && statusCode <= 299) {
          fut.handle(new Success<>(responsebody));
        } else {
          if (statusCode== 404) {
            fut.handle(new Failure<>(NOT_FOUND, "404 " + responsebody + ": " + url));
          } else if (statusCode == 403) {
            fut.handle(new Failure<>(FORBIDDEN, "403 " + responsebody + ": " + url));
          } else if (statusCode == 400) {
            fut.handle(new Failure<>(USER, responsebody));
          } else {
            fut.handle(new Failure<>(INTERNAL, responsebody));
          }
        }
      });
      reqres.exceptionHandler(e -> {
        logger.warn("OkapiClient exception 1 :", e);
        fut.handle(new Failure<>(INTERNAL, e));
      });
    });
    req.exceptionHandler((Throwable x) -> {
      String msg = x.getMessage();
      logger.warn(reqId + " OkapiClient exception 2: " + msg);
      // Connection gets closed. No idea why !!???
      if (x.getCause() != null) {
        logger.debug("   cause: " + x.getCause().getMessage());
      }
      fut.handle(new Failure<>(ANY, msg));
    });
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      logger.debug(reqId + " OkapiClient: adding header " + entry.getKey() + ": " + entry.getValue());
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
   * Get the HTTP status code of last request
   *
   * @return
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
   * Set the Okapi authentication token. Overrides the auth token. Should
   * normally not be needed, but can be used in some special cases.
   *
   * @param token
   */
  public void setOkapiToken(String token) {
    headers.put(XOkapiHeaders.TOKEN, token);
  }

  public void close() {
    if (httpClient != null) {
      httpClient.close();
      httpClient = null;
    }
  }
}
