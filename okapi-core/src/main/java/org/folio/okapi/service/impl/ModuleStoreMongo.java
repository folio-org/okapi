package org.folio.okapi.service.impl;

import org.folio.okapi.service.ModuleStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Stores ModuleDescriptors in a Mongo database.
 */
public class ModuleStoreMongo implements ModuleStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  MongoClient cli;
  final private String collection = "okapi.modules";

  public ModuleStoreMongo(MongoClient cli) {
    this.cli = cli;
  }

  @Override
  public void insert(ModuleDescriptor md,
          Handler<ExtendedAsyncResult<String>> fut) {
    String s = Json.encodePrettily(md);
    JsonObject document = new JsonObject(s);
    String id = md.getId();
    document.put("_id", id);
    cli.insert(collection, document, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>(id));
      } else {
        logger.debug("ModuleDbMongo: Failed to insert " + id
                + ": " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  @Override
  public void update(ModuleDescriptor md,
          Handler<ExtendedAsyncResult<String>> fut) {
    String id = md.getId();
    JsonObject jq = new JsonObject().put("_id", id);
    String s = Json.encodePrettily(md);
    JsonObject document = new JsonObject(s);
    document.put("_id", id);
    UpdateOptions options = new UpdateOptions().setUpsert(true);
    cli.updateCollectionWithOptions(collection, jq, new JsonObject().put("$set", document), options, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>(id));
      } else {
        logger.warn("Failed to update " + id
                + ": " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  @Override
  public void get(String id,
          Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    JsonObject jq = new JsonObject().put("_id", id);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> l = res.result();
        if (l.size() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, "Module " + id + " not found"));
        } else {
          JsonObject d = l.get(0);
          d.remove("_id");
          final ModuleDescriptor md = Json.decodeValue(d.encode(),
                  ModuleDescriptor.class);
          fut.handle(new Success<>(md));
        }
      }
    });
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    final String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> resl = res.result();
        List<ModuleDescriptor> ml = new ArrayList<>(resl.size());
        for (JsonObject jo : resl) {
          jo.remove("_id");
          ModuleDescriptor md = Json.decodeValue(jo.encode(),
                  ModuleDescriptor.class);
          ml.add(md);
        }
        fut.handle(new Success<>(ml));
      }
    });
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    JsonObject jq = new JsonObject().put("id", id);
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

}
