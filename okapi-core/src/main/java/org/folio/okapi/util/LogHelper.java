package org.folio.okapi.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Helper class to mess with logging stuff. Normally we use
 * io.vertx.core.logging.Logger for all our logging. Behind the scenes, this
 * turns out to use log4j. For advanced stuff, we need to access log4j directly.
 * This class tries to hide such things from the rest of the system. There are
 * methods for getting and setting the root logging level, from a Level, a
 * String, or as a web service. Later we may want to add more fancy log level
 * control, for example for different loggers or systems.
 *
 * @author heikki
 */
public class LogHelper {

  private static final Logger l4jlogger = Logger.getLogger(LogHelper.class);

  /**
   * Parameter for the web service. At some point we may want to add more stuff
   * here, to control logging for each module, or something. For now this is
   * good enough.
   */
  public static class LogLevelInfo {

    String level;

    @JsonCreator
    public LogLevelInfo(@JsonProperty String level) {
      this.level = level;
    }

    public LogLevelInfo() {
      this.level = "";
    }

    public String getLevel() {
      return level;
    }

    public void setLevel(String level) {
      this.level = level;
    }
  }

  public String getRootLogLevel() {
    Level lev = l4jlogger.getParent().getEffectiveLevel();
    return lev == null ? "null" : lev.toString();
  }

  public void setRootLogLevel(Level l) {
    // This might stop working in log4j version 2. See
    // http://stackoverflow.com/questions/23434252/programmatically-change-log-level-in-log4j2
    l4jlogger.getParent().setLevel(l);
  }

  public void setRootLogLevel(String name) {
    Level l = Level.toLevel(name);
    setRootLogLevel(l);
  }

}
