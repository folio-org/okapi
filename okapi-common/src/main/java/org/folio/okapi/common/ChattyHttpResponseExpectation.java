package org.folio.okapi.common;

import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpResponseExpectation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The same as {@link HttpResponseExpectation} but with response body included
 * in the Exception message.
 *
 * <p>The body length is limited to 500 Unicode code units by removing characters in the middle.
 *
 * <p>Use with {@link Future#expecting(Expectation)} when Future is a
 * {@code Future<HttpResponse<Buffer>>}, for examples see
 * <a href="https://vertx.io/docs/vertx-web-client/java/#http-response-expectations">
 * Vert.x Web Client Response Expectations</a>.
 */
public interface ChattyHttpResponseExpectation
    extends Expectation<io.vertx.ext.web.client.HttpResponse<Buffer>> {

  int MAX_BODY_LENGTH = 500;

  /**
   * Any 1XX informational response.
   */
  ChattyHttpResponseExpectation SC_INFORMATIONAL_RESPONSE = status(100, 200);

  /**
   * 100 Continue.
   */
  ChattyHttpResponseExpectation SC_CONTINUE = status(100);

  /**
   * 101 Switching Protocols.
   */
  ChattyHttpResponseExpectation SC_SWITCHING_PROTOCOLS = status(101);

  /**
   * 102 Processing (WebDAV, RFC2518).
   */
  ChattyHttpResponseExpectation SC_PROCESSING = status(102);

  /**
   * 103 Early Hints.
   */
  ChattyHttpResponseExpectation SC_EARLY_HINTS = status(103);

  /**
   * Any 2XX success.
   */
  ChattyHttpResponseExpectation SC_SUCCESS = status(200, 300);

  /**
   * 200 OK.
   */
  ChattyHttpResponseExpectation SC_OK = status(200);

  /**
   * 201 Created.
   */
  ChattyHttpResponseExpectation SC_CREATED = status(201);

  /**
   * 202 Accepted.
   */
  ChattyHttpResponseExpectation SC_ACCEPTED = status(202);

  /**
   * 203 Non-Authoritative Information (since HTTP/1.1).
   */
  ChattyHttpResponseExpectation SC_NON_AUTHORITATIVE_INFORMATION = status(203);

  /**
   * 204 No Content.
   */
  ChattyHttpResponseExpectation SC_NO_CONTENT = status(204);

  /**
   * 205 Reset Content.
   */
  ChattyHttpResponseExpectation SC_RESET_CONTENT = status(205);

  /**
   * 206 Partial Content.
   */
  ChattyHttpResponseExpectation SC_PARTIAL_CONTENT = status(206);

  /**
   * 207 Multi-Status (WebDAV, RFC2518).
   */
  ChattyHttpResponseExpectation SC_MULTI_STATUS = status(207);

  /**
   * Any 3XX redirection.
   */
  ChattyHttpResponseExpectation SC_REDIRECTION = status(300, 400);

  /**
   * 300 Multiple Choices.
   */
  ChattyHttpResponseExpectation SC_MULTIPLE_CHOICES = status(300);

  /**
   * 301 Moved Permanently.
   */
  ChattyHttpResponseExpectation SC_MOVED_PERMANENTLY = status(301);

  /**
   * 302 Found.
   */
  ChattyHttpResponseExpectation SC_FOUND = status(302);

  /**
   * 303 See Other (since HTTP/1.1).
   */
  ChattyHttpResponseExpectation SC_SEE_OTHER = status(303);

  /**
   * 304 Not Modified.
   */
  ChattyHttpResponseExpectation SC_NOT_MODIFIED = status(304);

  /**
   * 305 Use Proxy (since HTTP/1.1).
   */
  ChattyHttpResponseExpectation SC_USE_PROXY = status(305);

  /**
   * 307 Temporary Redirect (since HTTP/1.1).
   */
  ChattyHttpResponseExpectation SC_TEMPORARY_REDIRECT = status(307);

  /**
   * 308 Permanent Redirect (RFC7538).
   */
  ChattyHttpResponseExpectation SC_PERMANENT_REDIRECT = status(308);

  /**
   * Any 4XX client error.
   */
  ChattyHttpResponseExpectation SC_CLIENT_ERRORS = status(400, 500);

  /**
   * 400 Bad Request.
   */
  ChattyHttpResponseExpectation SC_BAD_REQUEST = status(400);

  /**
   * 401 Unauthorized.
   */
  ChattyHttpResponseExpectation SC_UNAUTHORIZED = status(401);

  /**
   * 402 Payment Required.
   */
  ChattyHttpResponseExpectation SC_PAYMENT_REQUIRED = status(402);

  /**
   * 403 Forbidden.
   */
  ChattyHttpResponseExpectation SC_FORBIDDEN = status(403);

  /**
   * 404 Not Found.
   */
  ChattyHttpResponseExpectation SC_NOT_FOUND = status(404);

  /**
   * 405 Method Not Allowed.
   */
  ChattyHttpResponseExpectation SC_METHOD_NOT_ALLOWED = status(405);

  /**
   * 406 Not Acceptable.
   */
  ChattyHttpResponseExpectation SC_NOT_ACCEPTABLE = status(406);

  /**
   * 407 Proxy Authentication Required.
   */
  ChattyHttpResponseExpectation SC_PROXY_AUTHENTICATION_REQUIRED = status(407);

  /**
   * 408 Request Timeout.
   */
  ChattyHttpResponseExpectation SC_REQUEST_TIMEOUT = status(408);

  /**
   * 409 Conflict.
   */
  ChattyHttpResponseExpectation SC_CONFLICT = status(409);

  /**
   * 410 Gone.
   */
  ChattyHttpResponseExpectation SC_GONE = status(410);

  /**
   * 411 Length Required.
   */
  ChattyHttpResponseExpectation SC_LENGTH_REQUIRED = status(411);

  /**
   * 412 Precondition Failed.
   */
  ChattyHttpResponseExpectation SC_PRECONDITION_FAILED = status(412);

  /**
   * 413 Request Entity Too Large.
   */
  ChattyHttpResponseExpectation SC_REQUEST_ENTITY_TOO_LARGE = status(413);

  /**
   * 414 Request-URI Too Long.
   */
  ChattyHttpResponseExpectation SC_REQUEST_URI_TOO_LONG = status(414);

  /**
   * 415 Unsupported Media Type.
   */
  ChattyHttpResponseExpectation SC_UNSUPPORTED_MEDIA_TYPE = status(415);

  /**
   * 416 Requested Range Not Satisfiable.
   */
  ChattyHttpResponseExpectation SC_REQUESTED_RANGE_NOT_SATISFIABLE = status(416);

  /**
   * 417 Expectation Failed.
   */
  ChattyHttpResponseExpectation SC_EXPECTATION_FAILED = status(417);

  /**
   * 421 Misdirected Request.
   */
  ChattyHttpResponseExpectation SC_MISDIRECTED_REQUEST = status(421);

  /**
   * 422 Unprocessable Entity (WebDAV, RFC4918).
   */
  ChattyHttpResponseExpectation SC_UNPROCESSABLE_ENTITY = status(422);

  /**
   * 423 Locked (WebDAV, RFC4918).
   */
  ChattyHttpResponseExpectation SC_LOCKED = status(423);

  /**
   * 424 Failed Dependency (WebDAV, RFC4918).
   */
  ChattyHttpResponseExpectation SC_FAILED_DEPENDENCY = status(424);

  /**
   * 425 Unordered Collection (WebDAV, RFC3648).
   */
  ChattyHttpResponseExpectation SC_UNORDERED_COLLECTION = status(425);

  /**
   * 426 Upgrade Required (RFC2817).
   */
  ChattyHttpResponseExpectation SC_UPGRADE_REQUIRED = status(426);

  /**
   * 428 Precondition Required (RFC6585).
   */
  ChattyHttpResponseExpectation SC_PRECONDITION_REQUIRED = status(428);

  /**
   * 429 Too Many Requests (RFC6585).
   */
  ChattyHttpResponseExpectation SC_TOO_MANY_REQUESTS = status(429);

  /**
   * 431 Request Header Fields Too Large (RFC6585).
   */
  ChattyHttpResponseExpectation SC_REQUEST_HEADER_FIELDS_TOO_LARGE = status(431);

  /**
   * Any 5XX server error.
   */
  ChattyHttpResponseExpectation SC_SERVER_ERRORS = status(500, 600);

  /**
   * 500 Internal Server Error.
   */
  ChattyHttpResponseExpectation SC_INTERNAL_SERVER_ERROR = status(500);

  /**
   * 501 Not Implemented.
   */
  ChattyHttpResponseExpectation SC_NOT_IMPLEMENTED = status(501);

  /**
   * 502 Bad Gateway.
   */
  ChattyHttpResponseExpectation SC_BAD_GATEWAY = status(502);

  /**
   * 503 Service Unavailable.
   */
  ChattyHttpResponseExpectation SC_SERVICE_UNAVAILABLE = status(503);

  /**
   * 504 Gateway Timeout.
   */
  ChattyHttpResponseExpectation SC_GATEWAY_TIMEOUT = status(504);

  /**
   * 505 HTTP Version Not Supported.
   */
  ChattyHttpResponseExpectation SC_HTTP_VERSION_NOT_SUPPORTED = status(505);

  /**
   * 506 Variant Also Negotiates (RFC2295).
   */
  ChattyHttpResponseExpectation SC_VARIANT_ALSO_NEGOTIATES = status(506);

  /**
   * 507 Insufficient Storage (WebDAV, RFC4918).
   */
  ChattyHttpResponseExpectation SC_INSUFFICIENT_STORAGE = status(507);

  /**
   * 510 Not Extended (RFC2774).
   */
  ChattyHttpResponseExpectation SC_NOT_EXTENDED = status(510);

  /**
   * 511 Network Authentication Required (RFC6585).
   */
  ChattyHttpResponseExpectation SC_NETWORK_AUTHENTICATION_REQUIRED = status(511);

  /**
   * Return the body trimmed to {@link #MAX_BODY_LENGTH} Unicode characters by removing
   * characters in the middle.
   */
  @SuppressWarnings({
      "java:S109",  // suppress "Assign this magic number 2 to a well-named constant
      // and use the constant instead." because / 2 indicates a half.
  })
  static String trimmedBody(io.vertx.ext.web.client.HttpResponse<Buffer> httpResponse) {
    var body = httpResponse.bodyAsString();
    if (body != null && body.length() > MAX_BODY_LENGTH) {
      body = body.substring(0, MAX_BODY_LENGTH / 2) + "…"
          + body.substring(body.length() - (MAX_BODY_LENGTH - 1) / 2);
    }
    return body;
  }

  /**
   * Creates an expectation asserting that the status response code is equal to {@code statusCode}.
   *
   * @param statusCode the expected status code
   */
  static ChattyHttpResponseExpectation status(int statusCode) {
    return status(statusCode, statusCode + 1);
  }

  /**
   * Creates an expectation asserting that the status response code is equal to
   * or greater than {@code min} and less than {@code max}.
   *
   * @param min the minimal expected status code
   * @param max the maximal status code the response status code is expected to be smaller than
   */
  static ChattyHttpResponseExpectation status(int min, int max) {
    return new ChattyHttpResponseExpectation() {
      @Override
      public boolean test(io.vertx.ext.web.client.HttpResponse<Buffer> value) {
        int statusCode = value.statusCode();
        return statusCode >= min && statusCode < max;
      }

      @Override
      public Exception describe(io.vertx.ext.web.client.HttpResponse<Buffer> httpResponse) {
        if (max - min == 1) {
          return new VertxException("Response status code " + httpResponse.statusCode()
              + " is not equal to " + min + ": " + trimmedBody(httpResponse), true);
        }
        return new VertxException("Response status code " + httpResponse.statusCode()
            + " is not between " + min + " and " + max + ": " + trimmedBody(httpResponse), true);
      }
    };
  }

  /**
   * Creates an expectation validating the response {@code content-type}
   * is {@code application/json}.
   */
  ChattyHttpResponseExpectation JSON = contentType("application/json");

  /**
   * Creates an expectation validating the response has a {@code content-type} header
   * matching the {@code mimeType}.
   *
   * @param mimeType the mime type
   */
  static ChattyHttpResponseExpectation contentType(String mimeType) {
    return contentType(Collections.singletonList(mimeType));
  }

  /**
   * Creates an expectation validating the response has a {@code content-type} header
   * matching one of the {@code mimeTypes}.
   *
   * @param mimeTypes the list of mime types
   */
  @SuppressWarnings({
      "S923",  // suppress "Do not use varargs", we need to provide a replacement for
      // Vert.x' HttpResponseExpectation contentType method that also uses varargs.
  })
  static ChattyHttpResponseExpectation contentType(String... mimeTypes) {
    return contentType(Arrays.asList(mimeTypes));
  }

  /**
   * Creates an expectation validating the response has a {@code content-type} header
   * matching one of the {@code mimeTypes}.
   *
   * @param mimeTypes the list of mime types
   */
  @SuppressWarnings({
      "S1188",  // suppress "Anonymous classes should not have too many lines" because
      // this is still easy to understand and parallels Vert.x original implementation.
  })
  static ChattyHttpResponseExpectation contentType(List<String> mimeTypes) {
    return new ChattyHttpResponseExpectation() {
      @Override
      public boolean test(io.vertx.ext.web.client.HttpResponse<Buffer> value) {
        String contentType = value.headers().get(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
          return false;
        }
        int paramIdx = contentType.indexOf(';');
        String mediaType = paramIdx != -1 ? contentType.substring(0, paramIdx) : contentType;

        for (String mimeType : mimeTypes) {
          if (mediaType.equalsIgnoreCase(mimeType)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public Exception describe(io.vertx.ext.web.client.HttpResponse<Buffer> httpResponse) {
        String contentType = httpResponse.headers().get(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
          return new VertxException("Missing response content type", true);
        }
        StringBuilder sb = new StringBuilder("Expect content type ")
            .append(contentType).append(" to be one of ");
        var first = true;
        for (String mimeType : mimeTypes) {
          if (!first) {
            sb.append(", ");
          }
          first = false;
          sb.append(mimeType);
        }
        return new VertxException(sb.toString() + ": " + trimmedBody(httpResponse), true);
      }
    };
  }
}
