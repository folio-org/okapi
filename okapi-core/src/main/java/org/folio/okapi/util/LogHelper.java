package org.folio.okapi.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

// S4792: Configuring loggers is security-sensitive
@java.lang.SuppressWarnings({"squid:S4792"})
public class LogHelper {
  private LogHelper() {}

  private static final Logger LOGGER = LogManager.getLogger(LogHelper.class);

  public static String getRootLogLevel() {
    return LOGGER.getLevel().toString();
  }

  private static void setRootLogLevel(Level l) {
    Configurator.setAllLevels(LogManager.getRootLogger().getName(), l);
  }

  /**
   * Set log level for root logger and all children.
   * @param name log level name
   */
  public static void setRootLogLevel(String name) {
    setRootLogLevel(Level.toLevel(name));
  }
}
