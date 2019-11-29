package org.folio.okapi.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public class LogHelper {

  private static final Logger l4jlogger = LogManager.getLogger(LogHelper.class);

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
    return l4jlogger.getLevel().toString();
  }

  private void setRootLogLevel(Level l) {
    Configurator.setRootLevel(l);
  }

  public void setRootLogLevel(String name) {
    Level l = Level.toLevel(name);
    setRootLogLevel(l);
  }

}
