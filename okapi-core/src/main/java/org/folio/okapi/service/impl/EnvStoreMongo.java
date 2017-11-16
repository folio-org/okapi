package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.EnvStore;

public class EnvStoreMongo implements EnvStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final MongoClient cli;
  private static final String COLLECTION = "okapi.env";

  public EnvStoreMongo(MongoClient cli) {
    this.cli = cli;
  }

  @Override
  public void add(EnvEntry env, Handler<ExtendedAsyncResult<Void>> fut) {
    String id = env.getName();
    JsonObject jq = new JsonObject().put("_id", id);
    String s = Json.encodePrettily(env);
    JsonObject document = new JsonObject(s);
    document.put("_id", id);
    UpdateOptions options = new UpdateOptions().setUpsert(true);
    cli.updateCollectionWithOptions(COLLECTION, jq, new JsonObject().put("$set", document), options, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>());
      } else {
        logger.warn("Failed to update " + id
          + ": " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    JsonObject jq = new JsonObject().put("_id", id);
    cli.removeDocument(COLLECTION, jq, rres -> {
      if (rres.failed()) {
        fut.handle(new Failure<>(INTERNAL, rres.cause()));
      } else if (rres.result().getRemovedCount() == 0) {
        fut.handle(new Failure<>(NOT_FOUND, id));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!reset) {
      fut.handle(new Success<>());
    } else {
      cli.dropCollection(COLLECTION, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        } else {
          fut.handle(new Success<>());
        }
      });
    }
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<EnvEntry>>> fut) {
    final String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(COLLECTION, jq, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> resl = res.result();
        List<EnvEntry> ml = new ArrayList<>(resl.size());
        for (JsonObject jo : resl) {
          jo.remove("_id");
          EnvEntry env = Json.decodeValue(jo.encode(),
            EnvEntry.class);
          ml.add(env);
        }
        fut.handle(new Success<>(ml));
      }
    });
  }
}
