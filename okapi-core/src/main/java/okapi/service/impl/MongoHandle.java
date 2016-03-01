/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package okapi.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * Generic handle to the Mongo database.
 * Encapsulates the configuration and creation of Mongo client
 * that can be passed on to other Mongo-based storage modules.
 */
public class MongoHandle {
  private MongoClient cli;
  final private String collection = "okapi.timestamps";

  public MongoHandle(Vertx vertx) {
    JsonObject opt = new JsonObject().
      put("host", "127.0.0.1"). // TODO - pass as parameters
      put("port", 27017);       // or read from some kind of config
    this.cli = MongoClient.createShared(vertx, opt);
  }
 
  public MongoClient getClient() {
    return cli;
  }
  
}
