package org.folio.okapi.util;

import org.folio.okapi.common.ErrorType;

public class OkapiError extends RuntimeException {
  private final String msg;
  private final ErrorType type;

  public OkapiError(ErrorType type, String msg) {
    this.msg = msg;
    this.type = type;
  }

  @Override
  public String getMessage() {
    return msg;
  }

  public ErrorType getType() {
    return type;
  }

  /**
   * Get HTTP error type for Throwable.
   * @param cause the thrown exception
   * @return Error type
   */
  public static ErrorType getType(Throwable cause) {
    if (cause instanceof OkapiError) {
      return ((OkapiError) cause).getType();
    }
    return ErrorType.INTERNAL;
  }
}
