package org.folio.okapi.common;

import io.vertx.launcher.application.VertxApplication;

public class MainLauncher extends VertxApplication {
  /** A launcher that sets log4j as logger for Vert.x */
  public MainLauncher(String[] args) {
    super(args);
    System.setProperty("vertx.logger-delegate-factory-class-name",
        "io.vertx.core.logging.Log4jLogDelegateFactory");
  }
}
