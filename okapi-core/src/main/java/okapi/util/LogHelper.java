/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Helper class to mess with logging stuff.
 * Normally we use io.vertx.core.logging.Logger for all our logging.
 * Behind the scenes, this turns out to use log4j. For advanced stuff
 * we need to access log4j directly. This class tries to hide such things
 * from the rest of the system.
 *
 * @author heikki
 */
public class LogHelper {

  private static final Logger l4jlogger = Logger.getLogger(LogHelper.class);

  private static class LogLevelInfo {
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
    Level lev = l4jlogger.getParent().getEffectiveLevel(); // getLevel();
    if ( lev == null )
      return "null";
    else
      return lev.toString();
  }

  public void getRootLogLevel(RoutingContext ctx) {
    String lev = getRootLogLevel();
    LogLevelInfo li = new LogLevelInfo(lev);
    String rj = Json.encode(li);
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .setStatusCode(200)
      .end(rj);
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

  public void setRootLogLevel(RoutingContext ctx) {
      final LogLevelInfo inf = Json.decodeValue(ctx.getBodyAsString(),
              LogLevelInfo.class);
      if ( inf == null || inf.getLevel() == null || inf.getLevel().isEmpty() ) {
        ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "text/plain")
          .end("Invalid id");
      } else {
        setRootLogLevel(inf.getLevel());
        ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(Json.encode(inf));
      }

  }



}
