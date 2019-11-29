package org.folio.okapi.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public class LogHelper {
  private static final Logger l4jlogger = LogManager.getLogger(LogHelper.class);

  private LogHelper() {
    throw new IllegalAccessError(this.toString());
  }

  public static String getRootLogLevel() {
    return l4jlogger.getLevel().toString();
  }

  private static void setRootLogLevel(Level l) {
    Configurator.setRootLevel(l);
  }

  public static void setRootLogLevel(String name) {
    Level l = Level.toLevel(name);
    setRootLogLevel(l);
  }
}
