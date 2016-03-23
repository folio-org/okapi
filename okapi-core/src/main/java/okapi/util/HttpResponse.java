/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class HttpResponse {

  static public void responseError(RoutingContext ctx, int code, Throwable cause) {
    responseText(ctx, code).end(cause.getMessage());
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
