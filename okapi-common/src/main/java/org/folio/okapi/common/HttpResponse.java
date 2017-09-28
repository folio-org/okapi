package org.folio.okapi.common;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * Helper to return HTTP responses. In most cases, the ProxyContext has the same
 * functionality, with added logging.
 */
public class HttpResponse {

  private HttpResponse() {
    throw new IllegalStateException("HttpResponse");
  }

  private static final Logger logger = LoggerFactory.getLogger("okapi");

  public static void responseError(RoutingContext ctx, ErrorType t, Throwable cause) {
    responseError(ctx, ErrorType.httpCode(t), cause);
  }

  public static void responseError(RoutingContext ctx, int code, Throwable cause) {
    responseError(ctx, code, cause.getMessage());
  }

  public static void responseError(RoutingContext ctx, int code, String msg) {
    String text = (msg == null) ? "(null)" : msg;
    if (code < 200 || code >= 300) {
      logger.error("HTTP response code=" + code + " msg=" + text);
    }
    responseText(ctx, code).end(text);
  }

  public static HttpServerResponse responseText(RoutingContext ctx, int code) {
    return ctx.response()
            .setStatusCode(code)
            .putHeader("Content-Type", "text/plain");
  }

  public static HttpServerResponse responseJson(RoutingContext ctx, int code) {
    return ctx.response()
            .setStatusCode(code)
            .putHeader("Content-Type", "application/json");
  }
}
