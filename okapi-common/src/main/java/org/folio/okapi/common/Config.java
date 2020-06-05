package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

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
   * Return the first that is defined and not empty:
   * key1 in system properties, key2 in system properties,
   * key1 in conf parameter, key2 in conf parameter.
   * Otherwise return def.
   * @param key1 the first property key (JSON key)
   * @param key2 the second property key (JSON key)
   * @param def default value (may be null)
   * @param conf JSON object configuration
   * @return property value (possibly null)
   */
  public static String getSysConf(String key1, String key2, String def, JsonObject conf) {
    String v = System.getProperty(key1);
    if (StringUtils.isNotEmpty(v)) {
      return v;
    }
    v = System.getProperty(key2);
    if (StringUtils.isNotEmpty(v)) {
      return v;
    }
    v = conf.getString(key1);
    if (StringUtils.isNotEmpty(v)) {
      return v;
    }
    v = conf.getString(key2);
    if (StringUtils.isNotEmpty(v)) {
      return v;
    }
    return def;
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
