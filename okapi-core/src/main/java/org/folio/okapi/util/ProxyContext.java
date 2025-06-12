package org.folio.okapi.util;

import io.micrometer.core.instrument.Timer;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.XOkapiHeaders;

/**
 * Helper for carrying around those things we need for proxying. Can also be
 * used for Okapi's own services, without the modList. Also has lots of helpers
 * for logging, in order to get the request-id in most log messages.
 */
// S1192: String literals should not be duplicated
// S2245: Using pseudorandom number generators (PRNGs) is security-sensitive
@SuppressWarnings({"squid:S1192", "squid:S2245"})
public class ProxyContext {
  private static final Logger logger = OkapiLogger.get(); // logger name "okapi"
  private static final Logger fullLogger = OkapiLogger.get("full");

  private List<ModuleInstance> modList;
  private final String reqId;
  private String tenant;
  private final RoutingContext ctx;
  private long nanoTimeStart; // = 0 for no start time
  private Long timerId;
  private final int waitMs;

  // store auth filter response status code, headers, and body
  private int authRes;
  private final MultiMap authHeaders = MultiMap.caseInsensitiveMultiMap();
  private Buffer authResBody = Buffer.buffer();
  // store handler response status code and headers
  private int handlerRes;
  private final MultiMap handlerHeaders = MultiMap.caseInsensitiveMultiMap();

  private final Messages messages = Messages.getInstance();
  private String userId;

  private final Timer.Sample sample;
  private ModuleInstance handlerModuleInstance;

  public ModuleInstance getHandlerModuleInstance() {
    return handlerModuleInstance;
  }

  public void setHandlerModuleInstance(ModuleInstance handlerModuleInstance) {
    this.handlerModuleInstance = handlerModuleInstance;
  }

  /**
   * Constructor to be used from proxy. Does not log the request, as we do not
   * know the tenant yet.
   *
   * @param ctx - the request we are serving
   */
  public ProxyContext(RoutingContext ctx, int waitMs) {
    this.ctx = ctx;
    this.waitMs = waitMs;
    this.tenant = "-";
    this.modList = null;
    String path = ctx.request().path();
    if (path == null) { // defensive coding, should always be there
      path = "";
    }

    StringBuilder newid = new StringBuilder();
    // Not used for security, just for request id uniqueness
    Random r = new Random();
    newid.append(String.format("%06d", r.nextInt(1000000)));

    int start = 0;
    if (path.startsWith("/_/")) {
      start = 2;
    }
    int end = start + 1;
    while (end < path.length() && path.charAt(end) != '/' && path.charAt(end) != '?') {
      end++;
    }
    newid.append(path, start, end);
    String curid = ctx.request().getHeader(XOkapiHeaders.REQUEST_ID);
    if (curid == null || curid.isEmpty()) {
      reqId = newid.toString();
      ctx.request().headers().add(XOkapiHeaders.REQUEST_ID, reqId);
    } else {
      reqId = curid + ";" + newid;
      ctx.request().headers().set(XOkapiHeaders.REQUEST_ID, reqId);
    }
    nanoTimeStart = 0;
    timerId = null;
    handlerRes = 0;
    this.sample = MetricsHelper.getTimerSample();
  }

  /**
   * Start timer.
   */
  public final void startTimer() {
    closeTimer();
    nanoTimeStart = System.nanoTime();
    if (waitMs > 0) {
      timerId = ctx.vertx().setPeriodic(waitMs, res -> {
            String mods = "";
            if (modList != null) {
              mods = modList.stream().map(x -> x.getModuleDescriptor().getId())
                  .collect(Collectors.joining(" "));
            }
            var elapsedSeconds = (System.nanoTime() - nanoTimeStart) / 1000000000.0;
            OkapiMapMessage msg = new OkapiMapMessage(reqId, tenant, userId, mods,
                String.format(Locale.ROOT, "%s WAIT %s %s %s %s %.3fs %s", reqId,
                    ctx.request().remoteAddress(), tenant, ctx.request().method(),
                    ctx.request().path(), elapsedSeconds, mods));
            fullLogger.info(msg);
          }
      );
    }
  }

  /**
   * Stop timer.
   */
  public void closeTimer() {
    if (timerId != null) {
      ctx.vertx().cancelTimer(timerId);
      timerId = null;
    }
    nanoTimeStart = 0;
  }

  /**
   * Return the elapsed time since startTimer, in microseconds.
   */
  public String timeDiff() {
    if (nanoTimeStart != 0) {
      return ((System.nanoTime() - nanoTimeStart) / 1000) + "us";
    } else {
      return "-";
    }
  }

  /**
   * Pass the response headers from an OkapiClient into the response of this
   * request. Only selected X-Something headers: X-Okapi-Trace
   *
   * @param ok OkapiClient to take resp headers from
   */
  public void passOkapiTraceHeaders(OkapiClient ok) {
    MultiMap respH = ok.getRespHeaders();
    for (Map.Entry<String, String> e : respH.entries()) {
      if (XOkapiHeaders.TRACE.equals(e.getKey())) {
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

  public int getAuthRes() {
    return authRes;
  }

  public void setAuthRes(int authRes) {
    this.authRes = authRes;
  }

  public MultiMap getAuthHeaders() {
    return authHeaders;
  }

  public Buffer getAuthResBody() {
    return authResBody;
  }

  public void setAuthResBody(Buffer authResBody) {
    this.authResBody = authResBody;
  }

  public int getHandlerRes() {
    return handlerRes;
  }

  public void setHandlerRes(int handlerRes) {
    this.handlerRes = handlerRes;
  }

  /**
   * Return handler headers.
   * @return headers
   */
  public MultiMap getHandlerHeaders() {
    return handlerHeaders;
  }

  /**
   * Log that HTTP request has been received.
   * @param ctx routing context
   */
  public final void logRequest(RoutingContext ctx) {
    Timer.Sample sample = MetricsHelper.getTimerSample();
    if (fullLogger.isInfoEnabled()) {
      String mods = "";
      if (modList != null) {
        mods = modList.stream().map(x -> x.getModuleDescriptor().getId())
            .collect(Collectors.joining(" "));
      }
      OkapiMapMessage msg = new OkapiMapMessage(reqId, tenant, userId, mods,
          String.format("%s REQ %s %s %s %s %s", reqId,
              ctx.request().remoteAddress(), tenant, ctx.request().method(),
              ctx.request().path(), mods));
      fullLogger.info(msg);
    }
    MetricsHelper.recordCodeExecutionTime(sample, "ProxyContext.logRequest");
  }

  /**
   * Log that a HTTP response has been received.
   * @param module where HTTP response was recevied
   * @param url URL for request
   * @param statusCode HTTP status
   */
  public void logResponse(String module, String url, int statusCode) {
    Timer.Sample sample = MetricsHelper.getTimerSample();
    if (fullLogger.isInfoEnabled()) {
      OkapiMapMessage msg = new OkapiMapMessage(reqId, tenant, userId, module,
          String.format("%s RES %s %s %s %s", reqId,
              statusCode, timeDiff(), module, url));
      fullLogger.info(msg);
    }
    MetricsHelper.recordCodeExecutionTime(sample, "ProxyContext.logResponse");
  }

  public void responseError(ErrorType t, Throwable cause) {
    responseError(ErrorType.httpCode(t), cause);
  }

  private void responseError(int code, Throwable cause) {
    String msg = (cause != null && cause.getMessage() != null)
        ? cause.getMessage() : messages.getMessage("10300");
    if (code == 500) {
      logger.warn(msg, cause);
    }
    responseError(code, msg);
  }

  /**
   * Log that a HTTP response was received with error status.
   *
   * @param code HTTP status for the HTTP response
   * @param logMsg message for the log
   * @param responseMsg message for the HTTP response
   */
  public void responseError(int code, String logMsg, String responseMsg) {
    logResponse("okapi", logMsg, code);
    closeTimer();
    MetricsHelper.recordHttpServerProcessingTime(this.sample, this.tenant, code,
        this.ctx.request().method().name(), this.handlerModuleInstance);
    HttpResponse.responseError(ctx, code, responseMsg);
  }

  /**
   * Log that a HTTP response was received with error status.
   * @param code HTTP status
   * @param msg message to go along with it
   */
  public void responseError(int code, String msg) {
    responseError(code, msg, msg);
  }

  public void addTraceHeaderLine(String h) {
    ctx.response().headers().add(XOkapiHeaders.TRACE, h);
  }

  public Timer.Sample getSample() {
    return sample;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }
}
