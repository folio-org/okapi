package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;

public class Config {

  private Config() {
    throw new IllegalStateException("Config");
  }

  /**
   * Returns string config info from properties and JSON config.
   * Check string property first in system; if not found OR empty,
   * inspect JSON configuration
   * @param key property key (JSON key)
   * @param def default value (may be null)
   * @param conf JSON object configuration
   * @return property value (possibly null)
   */
  public static String getSysConf(String key, String def, JsonObject conf) {
    final String v = System.getProperty(key);
    if (v == null || v.isEmpty()) {
      return conf.getString(key, def);
    } else {
      return v;
    }
  }

  /**
   * Returns string config info from properties and JSON config.
   * Check in this order and return the first that is defined and,
   * for properties, is not empty:
   * key1 property, key2 property, key1 in JsonObject, key2 in JsonObject,
   * {@code def} fallback value.
   * @param key1 property key (JSON key)
   * @param key2 property key (JSON key)
   * @param def default value (may be null)
   * @param conf JSON object configuration
   * @return property value (possibly null)
   */
  public static String getSysConf(String key1, String key2, String def, JsonObject conf) {
    String v = System.getProperty(key1);
    if (v != null && ! v.isEmpty()) {
      return v;
    }
    v = System.getProperty(key2);
    if (v != null && ! v.isEmpty()) {
      return v;
    }
    return conf.getString(key1, conf.getString(key2, def));
  }

  /**
   * Returns boolean config info from properties and JSON config.
   * Check boolean property first in system; if not found OR empty,
   * inspect JSON configuration
   * @param key property key (JSON key)
   * @param def default value (may be null)
   * @param conf JSON object configuration
   * @return property value (possibly null)
   * @throws ClassCastException for bad boolean value
   */
  public static Boolean getSysConfBoolean(String key, Boolean def, JsonObject conf) {
    final String v = System.getProperty(key);
    if (v == null || v.isEmpty()) {
      return conf.getBoolean(key, def);
    }
    return Boolean.parseBoolean(v);
  }

  /**
   * Returns integer config info from properties and JSON config.
   * Check property first in system; if not found OR empty, inspect JSON configuration.
   * @param key property key (JSON key).
   * @param def default value (may be null).
   * @param conf JSON object configuration.
   * @return value (possibly null).
   */
  public static Integer getSysConfInteger(String key, Integer def, JsonObject conf) {
    final String v = System.getProperty(key);
    if (v == null || v.isEmpty()) {
      return conf.getInteger(key, def);
    }
    return Integer.parseInt(v);
  }
}
