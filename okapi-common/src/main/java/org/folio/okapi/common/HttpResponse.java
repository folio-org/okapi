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

  private final static Logger logger = LoggerFactory.getLogger("okapi");

  static public void responseError(RoutingContext ctx, ErrorType t, Throwable cause) {
    responseError(ctx, ErrorType.httpCode(t), cause);
  }

  static public void responseError(RoutingContext ctx, int code, Throwable cause) {
    responseError(ctx, code, cause.getMessage());
  }

  static public void responseError(RoutingContext ctx, int code, String msg) {
    String text = (msg == null) ? "(null)" : msg;
    if (code < 200 || code >= 300) {
      logger.error("HTTP response code=" + code + " msg=" + text);
    }
    responseText(ctx, code).end(text);
    //throw new Error();
  }

  static public HttpServerResponse responseText(RoutingContext ctx, int code) {
    return ctx.response()
            .setStatusCode(code)
            .putHeader("Content-Type", "text/plain");
  }

  static public HttpServerResponse responseJson(RoutingContext ctx, int code) {
    return ctx.response()
            .setStatusCode(code)
            .putHeader("Content-Type", "application/json");
  }
}
