package org.folio.okapi.common;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class OkapiLogger {

  private OkapiLogger() {
    throw new IllegalStateException("OkapiLogger");
  }

  static Logger get() {
    System.setProperty("vertx.logger-delegate-factory-class-name",
      "io.vertx.core.logging.SLF4JLogDelegateFactory");
    return LoggerFactory.getLogger("okapi");
  }
}
