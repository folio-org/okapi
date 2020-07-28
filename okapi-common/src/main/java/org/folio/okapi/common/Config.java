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
}
