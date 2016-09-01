package org.folio.okapi.common;

/**
 * Types of errors.
 */
public enum ErrorType {
  OK, // Not really an error, but a success code
  INTERNAL, // Internal errors of any kind
  USER, // Bad requests, etc
  NOT_FOUND, // Stuff that is not there
  ANY;        // Anything else

}
