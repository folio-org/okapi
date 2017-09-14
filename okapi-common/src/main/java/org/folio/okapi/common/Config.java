package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;

public class Config {

  public static String getSysConf(String key, String def, JsonObject conf) {
    String v = System.getProperty(key, conf.getString(key, def));
    return v;
  }
}
