package org.folio.okapi.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OkapiLogger {

  private OkapiLogger() {
    throw new IllegalStateException("OkapiLogger");
  }

  public static Logger get() {
    return get("okapi");
  }

  public static Logger get(Class<?> cl) {
    return LogManager.getLogger(cl);
  }

  public static Logger get(String name) {
    return LogManager.getLogger(name);
  }

}
