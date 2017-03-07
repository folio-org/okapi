package org.folio.okapi.common;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class HttpResponse {

  private final static Logger logger = LoggerFactory.getLogger("okapi");

  static public void responseError(RoutingContext ctx, ErrorType t, Throwable cause) {
    int code = 500;
    switch (t) {
      case OK:
        code = 200;
        break;
      case INTERNAL:
        code = 500;
        break;
      case USER:
        code = 400;
        break;
      case NOT_FOUND:
        code = 404;
        break;
      case ANY:
        code = 500;
        break;
    }
    responseError(ctx, code, cause);
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
