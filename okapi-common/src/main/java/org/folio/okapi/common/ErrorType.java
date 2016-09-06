package org.folio.okapi.common;

/**
 * Types of errors.
 */
public enum ErrorType {
  /** Not really an error, but a success code */
  OK,
  /** Internal errors of any kind */
  INTERNAL,
  /** Bad requests, etc */
  USER,
  /** Stuff that is not there */
  NOT_FOUND,
  /** Error type for anything else */
  ANY;

}
