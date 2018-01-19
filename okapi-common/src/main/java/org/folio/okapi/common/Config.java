package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;

public class Config {

  private Config() {
    throw new IllegalStateException("Config");
  }

  public static String getSysConf(String key, String def, JsonObject conf) {
    final String v = System.getProperty(key);
    if (v == null || (v.isEmpty() && def != null && !def.isEmpty())) {
      return conf.getString(key, def);
    } else {
      return v;
    }
  }
}
