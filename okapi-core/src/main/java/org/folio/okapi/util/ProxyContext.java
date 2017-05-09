package org.folio.okapi.util;

import com.codahale.metrics.Timer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.HttpResponse;
import static org.folio.okapi.common.HttpResponse.responseError;
import static org.folio.okapi.common.HttpResponse.responseText;
import org.folio.okapi.common.XOkapiHeaders;

/**
 * Helper for carrying around those things we need for proxying. Can also be
 * used for Okapi's own services, without the modList.
 */
public class ProxyContext {

  private Logger logger = LoggerFactory.getLogger("okapi");
  private final HttpClient httpClient;
  private List<ModuleInstance> modList;
  private List<String> traceHeaders;
  private String reqId;
  private String tenant;
  private RoutingContext ctx;
  /**
   * Constructor to be used from proxy
   *
   * @param ml - module list, used by proxy itself
   * @param vertx - to create a httpClient, used by proxy
   * @param ctx - the request we are serving
   * @param tenant - tenant id for later logging
   */
  public ProxyContext(List<ModuleInstance> ml, Vertx vertx,
    RoutingContext ctx, String tenant) {
    this.ctx = ctx;
    this.tenant = tenant;
    this.modList = ml;
    traceHeaders = new ArrayList<>();
    httpClient = vertx.createHttpClient();
    this.reqId = reqId;
    reqidHeader(ctx);
    logRequest(ctx, tenant);
  }

  public ProxyContext(RoutingContext ctx) {
    this.ctx = ctx;
    modList = null;
    traceHeaders = new ArrayList<>();
    httpClient = null;
    this.reqId = reqId;
    reqidHeader(ctx);
    logRequest(ctx, "-");
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }

  public List<ModuleInstance> getModList() {
    return modList;
  }

  public String getReqId() {
    return reqId;
  }

  /**
   * Update or create the X-Okapi-Request-Id header. Save the id for future use.
   */
  private void reqidHeader(RoutingContext ctx) {
    String reqid = ctx.request().getHeader(XOkapiHeaders.REQUEST_ID);
    String path = ctx.request().path();
    if (path == null) { // defensive coding, should always be there
      path = "";
    }
    path = path.replaceFirst("(^/[^/]+).*$", "$1");
    int rnd = (int) (Math.random() * 1000000);
    String newid = String.format("%06d", rnd);
    newid += path;
    if (reqid == null || reqid.isEmpty()) {
      ctx.request().headers().add(XOkapiHeaders.REQUEST_ID, newid);
      logger.debug("Assigned new reqId " + newid);
    } else {
      newid = reqid + ";" + newid;
      ctx.request().headers().set(XOkapiHeaders.REQUEST_ID, newid);
      ctx.request().headers().add(XOkapiHeaders.REQUEST_ID, newid);
      logger.debug("Appended a reqId " + newid);
    }
    reqId = newid;
  }


  /* Helpers for logging and building responses */
  public void logRequest(RoutingContext ctx, String tenant) {
    logger.info(reqId + " REQ "
      + ctx.request().remoteAddress()
      + " " + tenant + " " + ctx.request().method()
      + " " + ctx.request().path());
  }

  public void logResponse(String module, String url, int statusCode, long timeDiff) {
    logger.info(reqId
      + " RES " + statusCode + " " + timeDiff + "us "
      + module + " " + url);
  }

  public void logResponse(HttpServerResponse response, String msg) {
    int code = response.getStatusCode();
    String text = (msg == null) ? "(null)" : msg;
    text = text.substring(0, 80);
    logResponse("okapi", text, code, 0);
  }

  public void responseError(ErrorType t, Throwable cause) {
    responseError(ErrorType.httpCode(t), cause);
  }

  public void responseError(int code, Throwable cause) {
    responseError(code, cause.getMessage());
  }

  public void responseError(int code, String msg) {
    logResponse("okapi", msg, code, 0);
    responseText(code).end(msg);
  }

  public HttpServerResponse responseText(int code) {
    logResponse("okapi", "", code, 0);
    return HttpResponse.responseText(ctx, code);
  }

  public void responseText(int code, String txt) {
    logResponse("okapi", txt, code, 0);
    HttpResponse.responseText(ctx, code).end(txt);
  }

  public HttpServerResponse responseJson(int code) {
    logResponse("okapi", "", code, 0);
    return HttpResponse.responseJson(ctx, code);
  }
  public void responseJson(int code, String json) {
    logResponse("okapi", "", code, 0);
    HttpResponse.responseJson(ctx, code).end(json);
  }
  public void responseJson(int code, String json, String location) {
    logResponse("okapi", "", code, 0);
    HttpResponse.responseJson(ctx, code)
      .putHeader("Location", location)
      .end(json);
  }

  /**
   * Add the trace headers to the response.
   */
  public void addTraceHeaders(RoutingContext ctx) {
    for (String th : traceHeaders) {
      ctx.response().headers().add(XOkapiHeaders.TRACE, th);
    }
  }

  public void addTraceHeaderLine(String h) {
    traceHeaders.add(h);
  }

}
