package org.folio.okapi.util;

public class NotFound extends Throwable {
  private String msg;

  public NotFound(String msg) {
    this.msg = msg;
  }

  @Override
  public String getMessage() {
    return msg;
  }
}
