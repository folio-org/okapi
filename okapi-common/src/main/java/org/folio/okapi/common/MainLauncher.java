package org.folio.okapi.common;

import io.vertx.launcher.application.VertxApplication;

class MainLauncher extends VertxApplication {

  public static void main(String[] args) {
    System.setProperty("vertx.logger-delegate-factory-class-name",
        "io.vertx.core.logging.Log4jLogDelegateFactory");
    VertxApplication vertxApplication = new MainLauncher(args);
    vertxApplication.launch();
  }

  public MainLauncher(String[] args) {
    super(args);
  }
}
