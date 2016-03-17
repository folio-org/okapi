/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Helper class to mess with logging stuff.
 * Normally we use io.vertx.core.logging.Logger for all our logging.
 * Behind the scenes, this turns out to use log4j. For advanced stuff
 * we need to access log4j directly. This class tries to hide such things
 * from the rest of the system.
 * @author heikki
 */
public class LogHelper {
  private static Logger l4jlogger = Logger.getLogger(LogHelper.class);

  public static void setRootLogLevel(Level l) {
    l4jlogger.getParent().setLevel(l);
  }
  public static void setRootLogLevel(String name) {
    Level l = Level.toLevel(name);
    setRootLogLevel(l);
  }

}
