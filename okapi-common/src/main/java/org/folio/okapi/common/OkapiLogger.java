package org.folio.okapi.common;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class OkapiLogger {

  private OkapiLogger() {
    throw new IllegalStateException("OkapiLogger");
  }

  public static Logger get() {
    return get("okapi");
  }

  public static Logger get(Class<?> cl) {
    System.setProperty("vertx.logger-delegate-factory-class-name",
      "io.vertx.core.logging.SLF4JLogDelegateFactory");
    return LoggerFactory.getLogger(cl);
  }

  public static Logger get(String name) {
    System.setProperty("vertx.logger-delegate-factory-class-name",
      "io.vertx.core.logging.SLF4JLogDelegateFactory");
    return LoggerFactory.getLogger(name);
  }

}
