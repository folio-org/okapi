package org.folio.okapi.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.StrLookup;

import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;

/**
 * This class should be used for storing context variables
 * and use them in logging events
 */
@Plugin(name = "FolioLoggingContext", category = StrLookup.CATEGORY)
public class FolioLoggingContext implements StrLookup {

  public String EMPTY_VALUE = "";

  public String lookup(String key) {
    return lookup(null, key);
  }

  public String lookup(LogEvent event, String key) {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      return getContextMap(ctx).getOrDefault(key, EMPTY_VALUE);
    }
    return EMPTY_VALUE;
  }

  public static void put(String key, String value) {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      getContextMap(ctx).put(key, value);
    }
  }

  public static Map<String, String> getAll() {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      return new HashMap<>(getContextMap(ctx));
    }
    return null;
  }

  private static ConcurrentMap<String, String> getContextMap(ContextInternal ctx) {
    return (ConcurrentMap<String, String>) ctx.localContextData()
        .computeIfAbsent(FolioLoggingContext.class, (k) -> new ConcurrentHashMap<String, String>());
  }

}
