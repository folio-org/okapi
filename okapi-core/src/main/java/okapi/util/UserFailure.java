/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import static okapi.util.ErrorType.*;


public class UserFailure<T> extends Failure<T> {

  public UserFailure(Throwable failure) {
    super(failure);
  }

  public UserFailure(String s) {
    super(s);
  }

  public ErrorType getType() {
    return USER;
  }
}
