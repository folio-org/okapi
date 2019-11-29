package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;

@java.lang.SuppressWarnings({"squid:S1192"})
class MongoUtil<T> {

  private final String collection;
  private final MongoClient cli;
  private Logger logger = OkapiLogger.get();

  public MongoUtil(String collection, MongoClient cli) {
    this.collection = collection;
    this.cli = cli;
  }

  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    JsonObject jq = new JsonObject().put("_id", id);
    cli.removeDocument(collection, jq, rres -> {
      if (rres.failed()) {
        logger.warn("MongoUtil.delete " + id + " failed : " + rres.cause());
        fut.handle(new Failure<>(INTERNAL, rres.cause()));
      } else if (rres.result().getRemovedCount() == 0) {
        fut.handle(new Failure<>(NOT_FOUND, id));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!reset) {
      fut.handle(new Success<>());
    } else {
      cli.dropCollection(collection, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        } else {
          fut.handle(new Success<>());
        }
      });
    }
  }

  public void add(T env, String id, Handler<ExtendedAsyncResult<Void>> fut) {
    JsonObject jq = new JsonObject().put("_id", id);
    String s = Json.encodePrettily(env);
    JsonObject document = new JsonObject(s);
    encode(document, null); // _id can not be put for Vert.x 3.5.1
    UpdateOptions options = new UpdateOptions().setUpsert(true);
    cli.updateCollectionWithOptions(collection, jq, new JsonObject().put("$set", document), options, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>());
      } else {
        logger.warn("MongoUtil.add " + id + " failed : " + res.cause());
        logger.warn("Document: " + document.encodePrettily());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  public void insert(T md, String id, Handler<ExtendedAsyncResult<Void>> fut) {
    String s = Json.encodePrettily(md);
    JsonObject document = new JsonObject(s);
    encode(document, id);
    cli.insert(collection, document, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>());
      } else {
        logger.warn("MongoUtil.insert " + id + " failed : " + res.cause());
        logger.warn("Document: " + document.encodePrettily());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  public void getAll(Class<T> clazz, Handler<ExtendedAsyncResult<List<T>>> fut) {
    final String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> resl = res.result();
        List<T> ml = new LinkedList<>();
        for (JsonObject jo : resl) {
          decode(jo);
          T env = Json.decodeValue(jo.encode(), clazz);
          ml.add(env);
        }
        fut.handle(new Success<>(ml));
      }
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
        if (m.contains("_")) {
          String n = m.replace("__", ".");
          repl.put(n, o.getBoolean(m));
        }
      }
      j.put("enabled", repl);
    }
  }

}
