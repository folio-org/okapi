/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import io.vertx.core.Launcher;

public class MainLauncher extends Launcher {

  public static void main(String[] args) {
    MainLauncher m = new MainLauncher();
    m.dispatch(args);
  }
}
