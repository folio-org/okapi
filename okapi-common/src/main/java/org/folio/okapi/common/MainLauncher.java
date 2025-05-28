package org.folio.okapi.common;

import io.vertx.launcher.application.VertxApplication;

class MainLauncher extends VertxApplication {
  public MainLauncher(String[] args) {
    super(args);
    System.setProperty("vertx.logger-delegate-factory-class-name",
        "io.vertx.core.logging.Log4jLogDelegateFactory");
  }
}
