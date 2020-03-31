package org.folio.okapi.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;


// S1118: Utility class should have a private constructor
// S4792: Configuring loggers is security-sensitive
@java.lang.SuppressWarnings({"squid:S4792", "squid:S1118"})
public class LogHelper {
  private static final Logger LOGGER = LogManager.getLogger(LogHelper.class);

  public static String getRootLogLevel() {
    return LOGGER.getLevel().toString();
  }

  private static void setRootLogLevel(Level l) {
    Configurator.setRootLevel(l);
  }

  public static void setRootLogLevel(String name) {
    Level l = Level.toLevel(name);
    setRootLogLevel(l);
  }
}
