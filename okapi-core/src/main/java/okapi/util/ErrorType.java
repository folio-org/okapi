/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

/**
 * Types of errors.
 *
 */
public enum ErrorType {
  OK, // Not really an error, but a success code
  INTERNAL, // Internal errors of any kind
  USER, // Bad requests, etc
  NOT_FOUND, // Stuff that is not there
  ANY;        // Anything else

}
