package org.folio.okapi.common.logging;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.spi.context.storage.ContextLocal;
import java.util.Map;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.StrLookup;

/**
 * This class should be used for storing context variables and use them in logging events.
 *
 * <p>The variables are stored in the local vert.x
 * {@link io.vertx.core.Context#putLocal Context}
 * and can be used in log4j log lines by using
 * {@link org.apache.logging.log4j.core.lookup.StrLookup StrLookup}
 *
 * <p>Usage example in log4j2.properties:
 * <pre>{@code
 * appender.console.layout.requestId.type = KeyValuePair
 * appender.console.layout.requestId.key = requestId
 * appender.console.layout.requestId.value = $${FolioLoggingContext:requestid}
 * }
 * </pre>
 *
 * <p>Note this does not work (empty values) when using the
 * <a href="https://logging.apache.org/log4j/2.x/manual/async.html">async logger</a>.
 *
 * <p>The default sync logger works fine.
 */
@Plugin(name = "FolioLoggingContext", category = StrLookup.CATEGORY)
public class FolioLoggingContext implements StrLookup {

  private static final String EMPTY_VALUE = "";
  private static final Map<String, ContextLocal<String>> LOCAL = Map.of(
      "tenantId", FolioLocal.TENANT_ID,
      "requestId", FolioLocal.REQUEST_ID,
      "moduleId", FolioLocal.MODULE_ID,
      "userId", FolioLocal.USER_ID
      );
  private static final String ALLOWED_KEYS = "[" + String.join(", ", LOCAL.keySet()) + "]";

  /**
   * Lookup value by key. LogEvent isn't used.
   *
   * @param key the name of logging variable, {@code null} key isn't allowed
   * @return value for key or *empty string* if there is no such key
   */
  @Override
  public String lookup(LogEvent event, String key) {
    return lookup(key);
  }

  /**
   * Lookup value by key.
   *
   * @param key the name of logging variable, {@code null} key isn't allowed
   * @return value for key or *empty string* if there is no such key
   */
  @Override
  public String lookup(String key) {
    // needs try/catch until fixed: https://github.com/eclipse-vertx/vert.x/issues/4611
    try {
      Context ctx = Vertx.currentContext();
      if (ctx != null) {
        String val = ctx.getLocal(localKey(key));
        if (val != null) {
          return val;
        }
      }
      return EMPTY_VALUE;
    } catch (NullPointerException e) {
      return EMPTY_VALUE;
    }
  }

  /**
   * Put value by key to the logging context.
   * @param key the name of logging variable, {@code null} key isn't allowed.
   * @param value the value of logging variable.
   *              If {@code null} is passed, entry is removed from context
   */
  public static void put(String key, String value) {
    Context ctx = Vertx.currentContext();
    if (ctx != null) {
      if (value != null) {
        ctx.putLocal(localKey(key), value);
      } else {
        ctx.removeLocal(localKey(key));
      }
    }
  }

  private static ContextLocal<String> localKey(String key) {
    var contextLocal = LOCAL.get(key);
    if (contextLocal == null) {
      throw new IllegalArgumentException(
          "key expected to be one of " + ALLOWED_KEYS + " but was: " + key);
    }
    return contextLocal;
  }
}
