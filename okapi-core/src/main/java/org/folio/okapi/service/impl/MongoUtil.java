/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import java.util.LinkedList;
import java.util.List;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

public class MongoUtil<T> {

  final String collection;
  final MongoClient cli;

  public MongoUtil(String collection, MongoClient cli) {
    this.collection = collection;
    this.cli = cli;
  }

  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    JsonObject jq = new JsonObject().put("_id", id);
    cli.removeDocument(collection, jq, rres -> {
      if (rres.failed()) {
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
    document.put("_id", id);
    UpdateOptions options = new UpdateOptions().setUpsert(true);
    cli.updateCollectionWithOptions(collection, jq, new JsonObject().put("$set", document), options, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>());
      } else {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  public void insert(T md, String id, Handler<ExtendedAsyncResult<Void>> fut) {
    String s = Json.encodePrettily(md);
    JsonObject document = new JsonObject(s);
    document.put("_id", id);
    cli.insert(collection, document, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>());
      } else {
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
          jo.remove("_id");
          T env = Json.decodeValue(jo.encode(), clazz);
          ml.add(env);
        }
        fut.handle(new Success<>(ml));
      }
    });
  }
}
