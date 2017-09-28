package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;

public class Config {

  private Config() {
    throw new IllegalStateException("Config");
  }

  public static String getSysConf(String key, String def, JsonObject conf) {
    return System.getProperty(key, conf.getString(key, def));
  }
}
