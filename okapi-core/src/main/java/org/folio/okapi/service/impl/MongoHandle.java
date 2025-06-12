package org.folio.okapi.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.OkapiLogger;

/**
 * Generic handle to the Mongo database. Encapsulates the configuration and
 * creation of Mongo client that can be passed on to other Mongo-based storage
 * modules.
 */
class MongoHandle {

  private final MongoClient cli;

  // Little helper to get a config value:
  // First from System (-D on command line),
  // then from config (from the way the verticle gets deployed, e.g. in tests)
  // finally a default value.
  MongoHandle(Vertx vertx, JsonObject conf) {
    JsonObject opt = new JsonObject();
    String h = Config.getSysConf("mongo_host", "localhost", conf);
    if (!h.isEmpty()) {
      opt.put("host", h);
    }
    String p = Config.getSysConf("mongo_port", "27017", conf);
    if (!p.isEmpty()) {
      opt.put("port", Integer.parseInt(p));
    }
    String dbName = Config.getSysConf("mongo_db_name", "", conf);
    if (!dbName.isEmpty()) {
      opt.put("db_name", dbName);
    }
    Logger logger = OkapiLogger.get();
    logger.info("Using mongo backend at {} : {} / {}", h, p, dbName);
    this.cli = MongoClient.createShared(vertx, opt);
  }

  public MongoClient getClient() {
    return cli;
  }
}
