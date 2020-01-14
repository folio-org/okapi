package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;

public class Config {

  private Config() {
    throw new IllegalStateException("Config");
  }

  /**
   * Inspect Check string property first in system; if not found, inspect JSON
   * configuration
   * @param key property key (JSON key)
   * @param def default value (may be null)
   * @param conf JSON object configuration
   * @return property value (possibly null)
   */
  public static String getSysConf(String key, String def, JsonObject conf) {
    final String v = System.getProperty(key);
    if (v == null || (v.isEmpty() && def != null && !def.isEmpty())) {
      return conf.getString(key, def);
    } else {
      return v;
    }
  }

  /**
   * Inspect Check boolean property first in system; if not found, inspect JSON
   * configuration
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
    if (v.equals("true")) {
      return true;
    } else if (v.equals("false")) {
      return false;
    }
    throw new ClassCastException("java.lang.String cannot be cast to java.lang.Boolean");
  }
}
