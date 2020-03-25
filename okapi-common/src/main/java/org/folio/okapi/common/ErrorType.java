package org.folio.okapi.common;

/**
 * Types of errors.
 */
public enum ErrorType {
  /** Not really an error, but a success code. */
  OK(200),
  /** Internal errors of any kind. */
  INTERNAL(500),
  /** Bad requests, etc. */
  USER(400),
  /** Stuff that is not there. */
  NOT_FOUND(404),
  /** Any kind of auth or permission problem. */
  FORBIDDEN(403),
  /** Error type for anything else. */
  ANY(500);

  private final int statusCode;

  ErrorType(int code) {
    statusCode = code;
  }

  /**
   * Maps error type to HTTP status code.
   * @param t error type
   * @return HTTP status
   */
  public static int httpCode(ErrorType t) {
    return t.statusCode;
  }
}
