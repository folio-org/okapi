package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;

@java.lang.SuppressWarnings({"squid:S1192"})
class MongoUtil<T> {

  private final String collection;
  private final MongoClient cli;
  private final Logger logger = OkapiLogger.get();

  public MongoUtil(String collection, MongoClient cli) {
    this.collection = collection;
    this.cli = cli;
  }

  public Future<Boolean> delete(String id) {
    JsonObject jq = new JsonObject().put("_id", id);
    return cli.removeDocument(collection, jq).compose(res -> {
      if (res.getRemovedCount() == 0) {
        return Future.succeededFuture(Boolean.FALSE);
      } else {
        return Future.succeededFuture(Boolean.TRUE);
      }
    });
  }

  public Future<Void> init(boolean reset) {
    if (!reset) {
      return Future.succeededFuture();
    }
    return cli.dropCollection(collection);
  }

  public Future<Void> add(T env, String id) {
    JsonObject jq = new JsonObject().put("_id", id);
    String s = Json.encodePrettily(env);
    JsonObject document = new JsonObject(s);
    encode(document, null); // _id can not be put for Vert.x 3.5.1
    UpdateOptions options = new UpdateOptions().setUpsert(true);
    return cli.updateCollectionWithOptions(collection, jq,
        new JsonObject().put("$set", document), options).mapEmpty();
  }

  public Future<Void> insert(T md, String id) {
    String s = Json.encodePrettily(md);
    JsonObject document = new JsonObject(s);
    encode(document, id);
    return cli.insert(collection, document).mapEmpty();
  }

  public Future<List<T>> getAll(Class<T> clazz) {
    final String q = "{}";
    JsonObject jq = new JsonObject(q);
    return cli.find(collection, jq).compose(resl -> {
      List<T> ml = new LinkedList<>();
      for (JsonObject jo : resl) {
        decode(jo);
        T env = Json.decodeValue(jo.encode(), clazz);
        ml.add(env);
      }
      return Future.succeededFuture(ml);
    });
  }

  public void encode(JsonObject j, String id) {
    if (id != null) {
      j.put("_id", id);
    }
    JsonObject o = j.getJsonObject("enabled");
    if (o != null) {
      JsonObject repl = new JsonObject();
      for (String m : o.fieldNames()) {
        String n = m.replace(".", "__");
        repl.put(n, o.getBoolean(m));
      }
      j.put("enabled", repl);
    }
  }

  public void decode(JsonObject j) {
    j.remove("_id");
    JsonObject o = j.getJsonObject("enabled");
    if (o != null) {
      JsonObject repl = new JsonObject();
      for (String m : o.fieldNames()) {
        String n = m.replace("__", ".");
        repl.put(n, o.getBoolean(m));
      }
      j.put("enabled", repl);
    }
  }

}
