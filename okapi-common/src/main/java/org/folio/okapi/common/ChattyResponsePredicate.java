package org.folio.okapi.common;

import static io.vertx.ext.web.client.predicate.ResponsePredicate.create;

import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.ext.web.client.predicate.ErrorConverter;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import java.util.List;

/**
 * The same as {@link ResponsePredicate} but with response body included in the Exception message.
 *
 * <p>The body length is limited to 500 Unicode code units by removing characters in the middle.
 */
public final class ChattyResponsePredicate {
  private static final int MAX_LENGTH = 500;

  /**
   * Concatenate the result message and the response body, and return an Exception with
   * the concatenation result as message.
   *
   * @see ResponsePredicate#create(java.util.Function, ErrorConverter)
   */
  public static final ErrorConverter CONVERTER = ErrorConverter.createFullBody(result -> {
    var body = result.response().bodyAsString();
    if (body != null && body.length() > MAX_LENGTH) {
      body = body.substring(0, MAX_LENGTH / 2) + "…"
          + body.substring(body.length() - (MAX_LENGTH - 1) / 2);
    }
    String message = result.message() + " - " + body;
    return new NoStackTraceThrowable(message);
  });

  /**
   * Any 1XX informational response.
   */
  public static final ResponsePredicate SC_INFORMATIONAL_RESPONSE =
      create(ResponsePredicate.SC_INFORMATIONAL_RESPONSE, CONVERTER);

  /**
   * 100 Continue.
   */
  public static final ResponsePredicate SC_CONTINUE =
      create(ResponsePredicate.SC_CONTINUE, CONVERTER);

  /**
   * 101 Switching Protocols.
   */
  public static final ResponsePredicate SC_SWITCHING_PROTOCOLS =
      create(ResponsePredicate.SC_SWITCHING_PROTOCOLS, CONVERTER);

  /**
   * 102 Processing (WebDAV, RFC2518).
   */
  public static final ResponsePredicate SC_PROCESSING =
      create(ResponsePredicate.SC_PROCESSING, CONVERTER);

  /**
   * 103 Early Hints.
   */
  public static final ResponsePredicate SC_EARLY_HINTS =
      create(ResponsePredicate.SC_EARLY_HINTS, CONVERTER);

  /**
   * Any 2XX success.
   */
  public static final ResponsePredicate SC_SUCCESS =
      create(ResponsePredicate.SC_SUCCESS, CONVERTER);

  /**
   * 200 OK.
   */
  public static final ResponsePredicate SC_OK =
      create(ResponsePredicate.SC_OK, CONVERTER);

  /**
   * 201 Created.
   */
  public static final ResponsePredicate SC_CREATED =
      create(ResponsePredicate.SC_CREATED, CONVERTER);

  /**
   * 202 Accepted.
   */
  public static final ResponsePredicate SC_ACCEPTED =
      create(ResponsePredicate.SC_ACCEPTED, CONVERTER);

  /**
   * 203 Non-Authoritative Information (since HTTP/1.1).
   */
  public static final ResponsePredicate SC_NON_AUTHORITATIVE_INFORMATION =
      create(ResponsePredicate.SC_NON_AUTHORITATIVE_INFORMATION, CONVERTER);

  /**
   * 204 No Content.
   */
  public static final ResponsePredicate SC_NO_CONTENT =
      create(ResponsePredicate.SC_NO_CONTENT, CONVERTER);

  /**
   * 205 Reset Content.
   */
  public static final ResponsePredicate SC_RESET_CONTENT =
      create(ResponsePredicate.SC_RESET_CONTENT, CONVERTER);

  /**
   * 206 Partial Content.
   */
  public static final ResponsePredicate SC_PARTIAL_CONTENT =
      create(ResponsePredicate.SC_PARTIAL_CONTENT, CONVERTER);

  /**
   * 207 Multi-Status (WebDAV, RFC2518).
   */
  public static final ResponsePredicate SC_MULTI_STATUS =
      create(ResponsePredicate.SC_MULTI_STATUS, CONVERTER);

  /**
   * Any 3XX redirection.
   */
  public static final ResponsePredicate SC_REDIRECTION =
      create(ResponsePredicate.SC_REDIRECTION, CONVERTER);

  /**
   * 300 Multiple Choices.
   */
  public static final ResponsePredicate SC_MULTIPLE_CHOICES =
      create(ResponsePredicate.SC_MULTIPLE_CHOICES, CONVERTER);

  /**
   * 301 Moved Permanently.
   */
  public static final ResponsePredicate SC_MOVED_PERMANENTLY =
      create(ResponsePredicate.SC_MOVED_PERMANENTLY, CONVERTER);

  /**
   * 302 Found.
   */
  public static final ResponsePredicate SC_FOUND =
      create(ResponsePredicate.SC_FOUND, CONVERTER);

  /**
   * 303 See Other (since HTTP/1.1).
   */
  public static final ResponsePredicate SC_SEE_OTHER =
      create(ResponsePredicate.SC_SEE_OTHER, CONVERTER);

  /**
   * 304 Not Modified.
   */
  public static final ResponsePredicate SC_NOT_MODIFIED =
      create(ResponsePredicate.SC_NOT_MODIFIED, CONVERTER);

  /**
   * 305 Use Proxy (since HTTP/1.1).
   */
  public static final ResponsePredicate SC_USE_PROXY =
      create(ResponsePredicate.SC_USE_PROXY, CONVERTER);

  /**
   * 307 Temporary Redirect (since HTTP/1.1).
   */
  public static final ResponsePredicate SC_TEMPORARY_REDIRECT =
      create(ResponsePredicate.SC_TEMPORARY_REDIRECT, CONVERTER);

  /**
   * 308 Permanent Redirect (RFC7538).
   */
  public static final ResponsePredicate SC_PERMANENT_REDIRECT =
      create(ResponsePredicate.SC_PERMANENT_REDIRECT, CONVERTER);

  /**
   * Any 4XX client error.
   */
  public static final ResponsePredicate SC_CLIENT_ERRORS =
      create(ResponsePredicate.SC_CLIENT_ERRORS, CONVERTER);

  /**
   * 400 Bad Request.
   */
  public static final ResponsePredicate SC_BAD_REQUEST =
      create(ResponsePredicate.SC_BAD_REQUEST, CONVERTER);

  /**
   * 401 Unauthorized.
   */
  public static final ResponsePredicate SC_UNAUTHORIZED =
      create(ResponsePredicate.SC_UNAUTHORIZED, CONVERTER);

  /**
   * 402 Payment Required.
   */
  public static final ResponsePredicate SC_PAYMENT_REQUIRED =
      create(ResponsePredicate.SC_PAYMENT_REQUIRED, CONVERTER);

  /**
   * 403 Forbidden.
   */
  public static final ResponsePredicate SC_FORBIDDEN =
      create(ResponsePredicate.SC_FORBIDDEN, CONVERTER);

  /**
   * 404 Not Found.
   */
  public static final ResponsePredicate SC_NOT_FOUND =
      create(ResponsePredicate.SC_NOT_FOUND, CONVERTER);

  /**
   * 405 Method Not Allowed.
   */
  public static final ResponsePredicate SC_METHOD_NOT_ALLOWED =
      create(ResponsePredicate.SC_METHOD_NOT_ALLOWED, CONVERTER);

  /**
   * 406 Not Acceptable.
   */
  public static final ResponsePredicate SC_NOT_ACCEPTABLE =
      create(ResponsePredicate.SC_NOT_ACCEPTABLE, CONVERTER);

  /**
   * 407 Proxy Authentication Required.
   */
  public static final ResponsePredicate SC_PROXY_AUTHENTICATION_REQUIRED =
      create(ResponsePredicate.SC_PROXY_AUTHENTICATION_REQUIRED, CONVERTER);

  /**
   * 408 Request Timeout.
   */
  public static final ResponsePredicate SC_REQUEST_TIMEOUT =
      create(ResponsePredicate.SC_REQUEST_TIMEOUT, CONVERTER);

  /**
   * 409 Conflict.
   */
  public static final ResponsePredicate SC_CONFLICT =
      create(ResponsePredicate.SC_CONFLICT, CONVERTER);

  /**
   * 410 Gone.
   */
  public static final ResponsePredicate SC_GONE =
      create(ResponsePredicate.SC_GONE, CONVERTER);

  /**
   * 411 Length Required.
   */
  public static final ResponsePredicate SC_LENGTH_REQUIRED =
      create(ResponsePredicate.SC_LENGTH_REQUIRED, CONVERTER);

  /**
   * 412 Precondition Failed.
   */
  public static final ResponsePredicate SC_PRECONDITION_FAILED =
      create(ResponsePredicate.SC_PRECONDITION_FAILED, CONVERTER);

  /**
   * 413 Request Entity Too Large.
   */
  public static final ResponsePredicate SC_REQUEST_ENTITY_TOO_LARGE =
      create(ResponsePredicate.SC_REQUEST_ENTITY_TOO_LARGE, CONVERTER);

  /**
   * 414 Request-URI Too Long.
   */
  public static final ResponsePredicate SC_REQUEST_URI_TOO_LONG =
      create(ResponsePredicate.SC_REQUEST_URI_TOO_LONG, CONVERTER);

  /**
   * 415 Unsupported Media Type.
   */
  public static final ResponsePredicate SC_UNSUPPORTED_MEDIA_TYPE =
      create(ResponsePredicate.SC_UNSUPPORTED_MEDIA_TYPE, CONVERTER);

  /**
   * 416 Requested Range Not Satisfiable.
   */
  public static final ResponsePredicate SC_REQUESTED_RANGE_NOT_SATISFIABLE =
      create(ResponsePredicate.SC_REQUESTED_RANGE_NOT_SATISFIABLE, CONVERTER);

  /**
   * 417 Expectation Failed.
   */
  public static final ResponsePredicate SC_EXPECTATION_FAILED =
      create(ResponsePredicate.SC_EXPECTATION_FAILED, CONVERTER);

  /**
   * 421 Misdirected Request.
   */
  public static final ResponsePredicate SC_MISDIRECTED_REQUEST =
      create(ResponsePredicate.SC_MISDIRECTED_REQUEST, CONVERTER);

  /**
   * 422 Unprocessable Entity (WebDAV, RFC4918).
   */
  public static final ResponsePredicate SC_UNPROCESSABLE_ENTITY =
      create(ResponsePredicate.SC_UNPROCESSABLE_ENTITY, CONVERTER);

  /**
   * 423 Locked (WebDAV, RFC4918).
   */
  public static final ResponsePredicate SC_LOCKED =
      create(ResponsePredicate.SC_LOCKED, CONVERTER);

  /**
   * 424 Failed Dependency (WebDAV, RFC4918).
   */
  public static final ResponsePredicate SC_FAILED_DEPENDENCY =
      create(ResponsePredicate.SC_FAILED_DEPENDENCY, CONVERTER);

  /**
   * 425 Unordered Collection (WebDAV, RFC3648).
   */
  public static final ResponsePredicate SC_UNORDERED_COLLECTION =
      create(ResponsePredicate.SC_UNORDERED_COLLECTION, CONVERTER);

  /**
   * 426 Upgrade Required (RFC2817).
   */
  public static final ResponsePredicate SC_UPGRADE_REQUIRED =
      create(ResponsePredicate.SC_UPGRADE_REQUIRED, CONVERTER);

  /**
   * 428 Precondition Required (RFC6585).
   */
  public static final ResponsePredicate SC_PRECONDITION_REQUIRED =
      create(ResponsePredicate.SC_PRECONDITION_REQUIRED, CONVERTER);

  /**
   * 429 Too Many Requests (RFC6585).
   */
  public static final ResponsePredicate SC_TOO_MANY_REQUESTS =
      create(ResponsePredicate.SC_TOO_MANY_REQUESTS, CONVERTER);

  /**
   * 431 Request Header Fields Too Large (RFC6585).
   */
  public static final ResponsePredicate SC_REQUEST_HEADER_FIELDS_TOO_LARGE =
      create(ResponsePredicate.SC_REQUEST_HEADER_FIELDS_TOO_LARGE, CONVERTER);

  /**
   * Any 5XX server error.
   */
  public static final ResponsePredicate SC_SERVER_ERRORS =
      create(ResponsePredicate.SC_SERVER_ERRORS, CONVERTER);

  /**
   * 500 Internal Server Error.
   */
  public static final ResponsePredicate SC_INTERNAL_SERVER_ERROR =
      create(ResponsePredicate.SC_INTERNAL_SERVER_ERROR, CONVERTER);

  /**
   * 501 Not Implemented.
   */
  public static final ResponsePredicate SC_NOT_IMPLEMENTED =
      create(ResponsePredicate.SC_NOT_IMPLEMENTED, CONVERTER);

  /**
   * 502 Bad Gateway.
   */
  public static final ResponsePredicate SC_BAD_GATEWAY =
      create(ResponsePredicate.SC_BAD_GATEWAY, CONVERTER);

  /**
   * 503 Service Unavailable.
   */
  public static final ResponsePredicate SC_SERVICE_UNAVAILABLE =
      create(ResponsePredicate.SC_SERVICE_UNAVAILABLE, CONVERTER);

  /**
   * 504 Gateway Timeout.
   */
  public static final ResponsePredicate SC_GATEWAY_TIMEOUT =
      create(ResponsePredicate.SC_GATEWAY_TIMEOUT, CONVERTER);

  /**
   * 505 HTTP Version Not Supported.
   */
  public static final ResponsePredicate SC_HTTP_VERSION_NOT_SUPPORTED =
      create(ResponsePredicate.SC_HTTP_VERSION_NOT_SUPPORTED, CONVERTER);

  /**
   * 506 Variant Also Negotiates (RFC2295).
   */
  public static final ResponsePredicate SC_VARIANT_ALSO_NEGOTIATES =
      create(ResponsePredicate.SC_VARIANT_ALSO_NEGOTIATES, CONVERTER);

  /**
   * 507 Insufficient Storage (WebDAV, RFC4918).
   */
  public static final ResponsePredicate SC_INSUFFICIENT_STORAGE =
      create(ResponsePredicate.SC_INSUFFICIENT_STORAGE, CONVERTER);

  /**
   * 510 Not Extended (RFC2774).
   */
  public static final ResponsePredicate SC_NOT_EXTENDED =
      create(ResponsePredicate.SC_NOT_EXTENDED, CONVERTER);

  /**
   * 511 Network Authentication Required (RFC6585).
   */
  public static final ResponsePredicate SC_NETWORK_AUTHENTICATION_REQUIRED =
      create(ResponsePredicate.SC_NETWORK_AUTHENTICATION_REQUIRED, CONVERTER);

  /**
   * Creates a predicate validating the response {@code content-type} is {@code application/json}.
   */
  public static final ResponsePredicate JSON = create(ResponsePredicate.JSON, CONVERTER);

  private ChattyResponsePredicate() {
  }

  /**
   * Creates a predicate validating the response has a {@code content-type} header
   * matching the {@code mimeType}.
   *
   * @param mimeType the mime type
   */
  public static ResponsePredicate contentType(String mimeType) {
    return create(ResponsePredicate.contentType(mimeType), CONVERTER);
  }

  /**
   * Creates a predicate validating the response has a {@code content-type} header
   * matching one of the {@code mimeTypes}.
   *
   * @param mimeTypes the list of mime types
   */
  public static ResponsePredicate contentType(List<String> mimeTypes) {
    return create(ResponsePredicate.contentType(mimeTypes), CONVERTER);
  }
}
