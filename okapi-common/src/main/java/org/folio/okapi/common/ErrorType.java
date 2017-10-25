package org.folio.okapi.common;

/**
 * Types of errors.
 */
public enum ErrorType {
  /** Not really an error, but a success code */
  OK,
  /** Internal errors of any kind */
  INTERNAL,
  /** Bad requests, etc. */
  USER,
  /** Stuff that is not there */
  NOT_FOUND,
  /** Any kind of auth or permission problem */
  FORBIDDEN,
  /** Error type for anything else */
  ANY;

  public static int httpCode(ErrorType t) {
    int code = 500;
    switch (t) {
      case OK:
        code = 200;
        break;
      case USER:
        code = 400;
        break;
      case NOT_FOUND:
        code = 404;
        break;
      case FORBIDDEN:
        code = 403;
        break;
      case INTERNAL:
      case ANY:
        code = 500;
        break;
    }
    return code;
  }
}
