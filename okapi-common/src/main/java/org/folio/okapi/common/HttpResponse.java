package org.folio.okapi.common;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.Logger;

/**
 * Helper to return HTTP responses. In most cases, the ProxyContext has the same
 * functionality, with added logging.
 */
public class HttpResponse {

  private HttpResponse() {
    throw new IllegalStateException("HttpResponse");
  }

  private static final Logger logger = OkapiLogger.get();

  /**
   * Produce HTTP response based on exception cause.
   * @param ctx routing context from where HTTP response is generated
   * @param t error type which maps to HTTP status
   * @param cause cause for error
   */
  public static void responseError(RoutingContext ctx, ErrorType t, Throwable cause) {
    responseError(ctx, ErrorType.httpCode(t), cause);
  }

  private static void responseError(RoutingContext ctx, int code, Throwable cause) {
    responseError(ctx, code, cause.getMessage());
  }

  /**
   * Produce HTTP response with status code and text/plain message.
   * @param ctx routing context from where HTTP response is generated
   * @param code status code
   * @param msg message to be part of HTTP response
   */
  public static void responseError(RoutingContext ctx, int code, String msg) {
    String text = (msg == null) ? "(null)" : msg;
    if (code < 200 || code >= 300) {
      logger.error("HTTP response code={} msg={}", code, text);
    }
    HttpServerResponse res = responseText(ctx, code);
    if (!res.closed()) {
      res.end(text);
    }
  }

  /**
   * Produce HTTP response with status code and text/plain header.
   * @param ctx routing context from where HTTP response is generated
   * @param code status code
   * @return HTTP server response
   */
  public static HttpServerResponse responseText(RoutingContext ctx, int code) {
    HttpServerResponse res = ctx.response();
    if (!res.closed()) {
      res.setStatusCode(code).putHeader("Content-Type", "text/plain");
    }
    return res;
  }

  /**
   * Produce HTTP response with status code and application/json header.
   * @param ctx routing context from where HTTP response is generated
   * @param code status code
   * @return HTTP server response
   */
  public static HttpServerResponse responseJson(RoutingContext ctx, int code) {
    HttpServerResponse res = ctx.response();
    if (!res.closed()) {
      res.setStatusCode(code).putHeader("Content-Type", "application/json");
    }
    return res;
  }
}
