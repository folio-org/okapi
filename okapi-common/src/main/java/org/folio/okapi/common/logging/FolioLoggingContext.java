package org.folio.okapi.common.logging;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
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

  private static final String LOGGING_VAR_PREFIX = "folio_";

  public static final String TENANT_ID_LOGGING_VAR_NAME = "tenantid";

  public static final String REQUEST_ID_LOGGING_VAR_NAME = "requestid";

  public static final String MODULE_ID_LOGGING_VAR_NAME = "moduleid";

  public static final String USER_ID_LOGGING_VAR_NAME = "userid";

  /**
   * Lookup value by key.
   *
   * @param key the name of logging variable, {@code null} key isn't allowed
   * @return value for key or *empty string* if there is no such key
   */
  @Override
  public String lookup(String key) {
    return lookup(null, key);
  }

  /**
   * Lookup value by key. LogEvent isn't used.
   *
   * @param key the name of logging variable, {@code null} key isn't allowed
   * @return value for key or *empty string* if there is no such key
   */
  @Override
  public String lookup(LogEvent event, String key) {
    try {
      if (key == null) {
        throw new IllegalArgumentException("Key cannot be null");
      }
      Context ctx = Vertx.currentContext();
      if (ctx != null) {
        String val = ctx.getLocal(LOGGING_VAR_PREFIX + key);
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
        ctx.putLocal(LOGGING_VAR_PREFIX + key, value);
      } else {
        ctx.removeLocal(LOGGING_VAR_PREFIX + key);
      }
    }
  }

}
