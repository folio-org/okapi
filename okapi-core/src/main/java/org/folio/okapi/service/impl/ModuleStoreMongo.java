package org.folio.okapi.service.impl;

import org.folio.okapi.service.ModuleStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
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
    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    String s = Json.encodePrettily(md);
    JsonObject document = new JsonObject(s);
    document.put("_id", id);
    cli.replace(collection, jq, document, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>(id));
      } else {
        logger.debug("ModuleDbMongo: Failed to update" + id
                + ": " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  @Override
  public void get(String id,
          Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
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
    String q = "{ \"id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, fres -> {
      if (fres.failed()) {
        fut.handle(new Failure<>(INTERNAL, fres.cause()));
      } else {
        List<JsonObject> l = fres.result();
        if (l.size() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, id));
        } else {
          cli.remove(collection, jq, rres -> {
            if (rres.failed()) {
              fut.handle(new Failure<>(INTERNAL, rres.cause()));
            } else {
              fut.handle(new Success<>());
            }
          });
        }
      }
    });
  }

}
