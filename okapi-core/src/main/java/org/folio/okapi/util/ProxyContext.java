package org.folio.okapi.util;

import com.codahale.metrics.Timer;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.XOkapiHeaders;

/**
 * Helper for carrying around those things we need for proxying. Can also be
 * used for Okapi's own services, without the modList. Also has lots of helpers
 * for logging, in order to get the request-id in most log messages.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class ProxyContext {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private List<ModuleInstance> modList;
  private String reqId;
  private String tenant;
  private final RoutingContext ctx;
  private Timer.Context timer;

  /**
   * Constructor to be used from proxy. Does not log the request, as we do not
   * know the tenant yet.
   *
   * @param ctx - the request we are serving
   */
  public ProxyContext(RoutingContext ctx) {
    this.ctx = ctx;
    this.tenant = "-";
    this.modList = null;
    reqidHeader(ctx);
    timer = null;
  }

  public final void startTimer(String key) {
    closeTimer();
    timer = DropwizardHelper.getTimerContext(key);
  }

  public void closeTimer() {
    if (timer != null) {
      timer.close();
      timer = null;
    }
  }

  /**
   * Return the elapsed time since startTimer, in microseconds.
   *
   * @return
   */
  public String timeDiff() {
    if (timer != null) {
      return " " + (timer.stop() / 1000) + "us";
    } else {
      return "";
    }
  }

  /**
   * Pass the responde headers from an OkapiClient into the response of this
   * request. Only X-Something headers.
   *
   * @param ok OkapiClient to take resp headers from
   */
  public void passOkapiClientRespHeaders(OkapiClient ok) {
    MultiMap respH = ok.getRespHeaders();
    for (Map.Entry<String, String> e : respH.entries()) {
      if (e.getKey().startsWith("X-") || e.getKey().startsWith("x-")) {
        ctx.response().headers().add(e.getKey(), e.getValue());
      }
    }
  }

  public List<ModuleInstance> getModList() {
    return modList;
  }

  public void setModList(List<ModuleInstance> modList) {
    this.modList = modList;
  }

  public String getTenant() {
    return tenant;
  }

  public void setTenant(String tenant) {
    this.tenant = tenant;
  }

  public RoutingContext getCtx() {
    return ctx;
  }

  public String getReqId() {
    return reqId;
  }

  /**
   * Update or create the X-Okapi-Request-Id header. Save the id for future use.
   */
  private void reqidHeader(RoutingContext ctx) {
    String curid = ctx.request().getHeader(XOkapiHeaders.REQUEST_ID);
    String path = ctx.request().path();
    if (path == null) { // defensive coding, should always be there
      path = "";
    }
    path = path.replaceFirst("^(/_)?(/[^/?]+).*$", "$2");
      // when rerouting, the query appears as part of the path, so we kill it
    // here with the '?'.
    Random r = new Random();
    StringBuilder newid = new StringBuilder();
    newid.append(String.format("%06d", r.nextInt(1000000)));
    newid.append(path);
    if (curid == null || curid.isEmpty()) {
      reqId = newid.toString();
      ctx.request().headers().add(XOkapiHeaders.REQUEST_ID, reqId);
      this.debug("Assigned new reqId " + newid);
    } else {
      reqId = curid + ";" + newid.toString();
      ctx.request().headers().set(XOkapiHeaders.REQUEST_ID, reqId);
      this.debug("Appended a reqId " + newid);
    }
  }


  /* Helpers for logging and building responses */
  public final void logRequest(RoutingContext ctx, String tenant) {
    StringBuilder mods = new StringBuilder();
    if (modList != null && !modList.isEmpty()) {
      for (ModuleInstance mi : modList) {
        mods.append(" " + mi.getModuleDescriptor().getId());
      }
    }
    logger.info(reqId + " REQ "
      + ctx.request().remoteAddress()
      + " " + tenant + " " + ctx.request().method()
      + " " + ctx.request().path()
      + mods.toString());
  }

  public void logResponse(String module, String url, int statusCode) {
    logger.info(reqId
      + " RES " + statusCode + timeDiff() + " "
      + module + " " + url);
  }

  public void responseError(ErrorType t, Throwable cause) {
    responseError(ErrorType.httpCode(t), cause);
  }

  public void responseError(int code, Throwable cause) {
    if (cause != null && cause.getMessage() != null) {
      responseError(code, cause.getMessage());
    } else {
      responseError(code, "(null cause!!??)");
    }
  }

  public void responseError(int code, String msg) {
    logResponse("okapi", msg, code);
    HttpResponse.responseText(ctx, code).end(msg);
  }

  public void responseText(int code, String txt) {
    logResponse("okapi", txt, code);
    HttpResponse.responseText(ctx, code).end(txt);
  }

  public void addTraceHeaderLine(String h) {
    ctx.response().headers().add(XOkapiHeaders.TRACE, h);
  }

  public void error(String msg) {
    logger.error(getReqId() + " " + msg);
  }

  public void warn(String msg) {
    logger.warn(getReqId() + " " + msg);
  }

  public void warn(String msg, Throwable e) {
    logger.warn(getReqId() + " " + msg, e);
  }

  public void debug(String msg) {
    logger.debug(getReqId() + " " + msg);
  }

  public void trace(String msg) {
    logger.trace(getReqId() + " " + msg);
  }

}
