/*
 * Copyright (C) 2016 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.common;

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
    if (code < 200 || code >= 300) {
      logger.error("HTTP response code=" + code + " msg=" + msg);
    }
    responseText(ctx, code).end(msg);
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
