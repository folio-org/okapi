package org.folio.okapi.common;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.Map;
import static org.folio.okapi.common.ErrorType.*;

/**
 * Okapi client.
 * Makes requests to other Okapi modules, or Okapi itself. Handles all the
 * things we need with the headers etc. Note that the client keeps a list
 * of necessary headers (which it can get from the RoutingContext, or
 * separately), so it is bound to one request, or at least one tenant.
 * Your module should not just keep one client around for everything it
 * does.
 * @author heikki
 */
public class OkapiClient {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private RoutingContext ctx;
  private String okapiUrl;
  private HttpClient httpClient;
  private Vertx vertx;
  private Map<String,String> headers;
  // TODO Response headers: do we need a trace or something?
  // TODO Return type: Need a more complex container class with room for
  //   response headers, the whole response, and so on.
  // TODO Use this in the discovery-deployment communications
  private MultiMap respHeaders;
  private String reqId;
  private boolean logInfo; // t: log requests on INFO. f: on DEBUG
  private String responsebody;

  /**
   * Constructor from a vert.x ctx.
   * That ctx contains all the headers we need.
   * @param ctx
   */
  public OkapiClient(RoutingContext ctx) {
    init(ctx.vertx());
    this.ctx = ctx;
    this.okapiUrl = ctx.request().getHeader(XOkapiHeaders.URL);
    if (this.okapiUrl != null) {
      this.okapiUrl = okapiUrl.replaceAll("/+$", ""); // no trailing slash
    }
    for (String hdr : ctx.request().headers().names()) {
      if (hdr.startsWith(XOkapiHeaders.PREFIX)) {
        String hv = ctx.request().getHeader(hdr);
        headers.put(hdr, hv);
        if (hdr.equals(XOkapiHeaders.REQUEST_ID)) {
          reqId = hv;
        }
      }
    }
  }

  /** Explicit constructor.
   *
   * @param okapiUrl
   * @param vertx
   * @param headers may be null
   */
  public OkapiClient(String okapiUrl, Vertx vertx, Map<String,String> headers) {
    init(vertx);
    this.ctx = null;
    this.okapiUrl = okapiUrl.replaceAll("/+$", ""); // no trailing slash
    if (headers != null )
      this.headers.putAll(headers);
    if (this.headers.containsKey(XOkapiHeaders.REQUEST_ID)) {
      reqId = this.headers.get(XOkapiHeaders.REQUEST_ID);
    }
    respHeaders = null;
  }

  private void init(Vertx vertx) {
    this.vertx = vertx;
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
    int rnd = (int) (Math.random() * 1000000);
    String newId = String.format("%06d", rnd) + "/" + path;
    if (reqId.isEmpty()) {
      reqId = newId;
    } else {
      reqId = reqId + ";" + newId;
    }
    headers.put(XOkapiHeaders.REQUEST_ID, reqId);
  }

  /**
   * Make a request to Okapi.
   *
   * @param method GET or POST or such
   * @param path like "/foomodule/something"
   * @param data for the request. Most likely a JSON string.
   * @param fut callback when done. Most likely a JSON string if all went
   * well, or a plain text string in case of errors.
   */
  public void request( HttpMethod method, String path, String data,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (this.okapiUrl == null) {
      logger.error("OkapiClient: No OkapiUrl specified");
      fut.handle(new Failure<>(INTERNAL, "OkapiClient: No OkapiUrl specified"));
      return;
    }
    String url = this.okapiUrl + path;
    String tenant = "-";
    if (headers.containsKey(XOkapiHeaders.TENANT)) {
      tenant = headers.get(XOkapiHeaders.TENANT);
    }

    respHeaders = null;
    String logReqMsg = reqId + " REQ "        + "okapiClient " + tenant + " "
        + method.toString() + " " + url;
    if (logInfo) {
      logger.info(logReqMsg);
    } else {
      logger.debug(logReqMsg);
    }

    HttpClientRequest req = httpClient.requestAbs(method, url, postres -> {
      String logResMsg = reqId
        + " RES " + postres.statusCode() + " 0us " // TODO - get timing
        + "okapiClient " + url;
      if (logInfo) {
        logger.info(logResMsg);
      } else {
        logger.debug(logResMsg);
      }
      final Buffer buf = Buffer.buffer();
      respHeaders = postres.headers();
      postres.handler(b -> {
        logger.debug(reqId + " OkapiClient Buffering response " + b.toString());
        buf.appendBuffer(b);
      });
      postres.endHandler(e -> {
        responsebody = buf.toString();
        if (postres.statusCode() >= 200 && postres.statusCode() <= 299) {
          fut.handle(new Success<>(responsebody));
        } else {
          if (postres.statusCode() == 404) {
            fut.handle(new Failure<>(NOT_FOUND, "404 " + responsebody + ": " + url));
          } else if (postres.statusCode() == 400) {
            fut.handle(new Failure<>(USER, responsebody));
          } else {
            fut.handle(new Failure<>(INTERNAL, responsebody));
          }
        }
      });
      postres.exceptionHandler(e -> {
        fut.handle(new Failure<>(INTERNAL, e));
      });
    });
    req.exceptionHandler(x -> {
      String msg = x.getMessage();
      if ( msg == null || msg.isEmpty()) { // unresolved address results in no message
        msg = x.toString(); // so we use toString instead
      } // but not both, because connection error has identical string in both...
      logger.warn(reqId + " OkapiClient exception: "
        + x.toString() + ": " + x.getMessage(), x);
      fut.handle(new Failure<>(INTERNAL, msg));
    });
    for ( String hdr : headers.keySet()) {
      logger.debug(reqId + " OkapiClient: adding header " + hdr + ": " + headers.get(hdr));
    }
    req.headers().addAll(headers);
    req.end(data);
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
   * Get the Okapi authentication token. From the X-Okapi-Token header.
   *
   * @return the token, or null if not defined.
   */
  public String getOkapiToken() {
    return headers.get(XOkapiHeaders.TOKEN);
  }

  /** Set the Okapi authentication token.
   * Overrides the auth token. Should normally not be needed,
   * but can be used in some special cases.
   * @param token
   */
  public void setOkapiToken(String token) {
    headers.put(XOkapiHeaders.TOKEN, token);
  }


}
