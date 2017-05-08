package org.folio.okapi.util;

import com.codahale.metrics.Timer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.common.XOkapiHeaders;

/**
 * Helper for carrying around those things we need for proxying. Can also be
 * used for Okapi's own services, without the modList.
 */
public class ProxyContext {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  final HttpClient httpClient;
  List<ModuleInstance> modList;
  List<String> traceHeaders;
  String reqId;

  public HttpClient getHttpClient() {
    return httpClient;
  }

  public List<ModuleInstance> getModList() {
    return modList;
  }

  public String getReqId() {
    return reqId;
  }

  public ProxyContext(List<ModuleInstance> ml, Vertx vertx, String reqId) {
    this.modList = ml;
    traceHeaders = new ArrayList<>();
    httpClient = vertx.createHttpClient();
    this.reqId = reqId;
  }

  public ProxyContext(String reqId) {
    modList = null;
    traceHeaders = new ArrayList<>();
    httpClient = null;
    this.reqId = reqId;
  }

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
