package org.folio.okapi.logging;

import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.StrLookup;

/**
 * This class should be used for storing context variables
 * and use them in logging events.
 */
@Plugin(name = "FolioLoggingContext", category = StrLookup.CATEGORY)
public class FolioLoggingContext implements StrLookup {

  public static final String EMPTY_VALUE = "";

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
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      return getContextMap(ctx).getOrDefault(key, EMPTY_VALUE);
    }
    return EMPTY_VALUE;
  }

  /**
  * Put value by key to the logging context.   *
  * @param key the name of logging variable (e.g. requestId)
  */
  public static void put(String key, String value) {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      getContextMap(ctx).put(key, value);
    }
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentMap<String, String> getContextMap(ContextInternal ctx) {
    return (ConcurrentMap<String, String>) ctx.localContextData()
        .computeIfAbsent(FolioLoggingContext.class, (k) -> new ConcurrentHashMap<String, String>());
  }

}
