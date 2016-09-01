package org.folio.okapi.common;

import io.vertx.core.Launcher;

public class MainLauncher extends Launcher {
  public static void main(String[] args) {
    System.setProperty("vertx.logger-delegate-factory-class-name",
            "io.vertx.core.logging.SLF4JLogDelegateFactory");
    MainLauncher m = new MainLauncher();
    m.dispatch(args);
  }
}
