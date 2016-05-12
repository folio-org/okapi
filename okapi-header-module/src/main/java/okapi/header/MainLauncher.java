/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.header;

import io.vertx.core.Launcher;

public class MainLauncher extends Launcher {
  public static void main(String[] args) {
    System.setProperty("vertx.logger-delegate-factory-class-name",
            "io.vertx.core.logging.SLF4JLogDelegateFactory");
    MainLauncher m = new MainLauncher();
    m.dispatch(args);
  }
}
