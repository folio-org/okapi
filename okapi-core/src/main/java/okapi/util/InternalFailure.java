/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import static okapi.util.ErrorType.*;


public class InternalFailure<T> extends Failure<T> {

  public InternalFailure(Throwable failure) {
    super(failure);
  }

  public InternalFailure(String s) {
    super(s);
  }

  public ErrorType getType() {
    return INTERNAL;
  }
}
