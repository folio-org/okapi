package org.folio.okapi.logging;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.StrLookup;

/**
 * This class should be used for storing context variables
 * and use them in logging events.
 */
@Plugin(name = "FolioLoggingContext", category = StrLookup.CATEGORY)
public class FolioLoggingContext implements StrLookup {

  private static final String EMPTY_VALUE = "";

  public static final String LOGGING_VAR_PREFIX = "folio_";

  /**
   * Lookup value by key.
   *
   * @param key the name of logging variable (e.g. requestId)
   * @return value for key or *empty string* if there is no such key
   */
  public String lookup(String key) {
    return lookup(null, key);
  }

  /**
  * Lookup value by key. LogEvent isn't used.
  *
  * @param key the name of logging variable (e.g. requestId)
  * @return value for key or *empty string* if there is no such key
  */
  public String lookup(LogEvent event, String key) {
    Context ctx = Vertx.currentContext();
    if (ctx != null) {
      String val = ctx.getLocal(LOGGING_VAR_PREFIX + key);
      if (val != null) {
        return val;
      }
    }
    return EMPTY_VALUE;
  }

  /**
  * Put value by key to the logging context.   *
  * @param key the name of logging variable (e.g. requestId)
  */
  public static void put(String key, String value) {
    Context ctx = Vertx.currentContext();
    if (ctx != null) {
      ctx.putLocal(key, value);
    }
  }

}
