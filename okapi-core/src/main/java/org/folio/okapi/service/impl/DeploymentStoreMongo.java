package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.DeploymentStore;

public class DeploymentStoreMongo implements DeploymentStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  private final MongoClient cli;
  private static final String COLLECTION = "okapi.deployments";

  public DeploymentStoreMongo(MongoClient cli) {
    this.cli = cli;
  }

  @Override
  public void insert(DeploymentDescriptor dd, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    String s = Json.encodePrettily(dd);
    JsonObject document = new JsonObject(s);
    String id = dd.getInstId();
    document.put("_id", id);
    cli.insert(COLLECTION, document, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>(dd));
      } else {
        logger.debug("ModuleDbMongo: Failed to insert " + id
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
  public void reset(Handler<ExtendedAsyncResult<Void>> fut) {
    cli.dropCollection(COLLECTION, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    final String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(COLLECTION, jq, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> resl = res.result();
        List<DeploymentDescriptor> ml = new ArrayList<>(resl.size());
        for (JsonObject jo : resl) {
          jo.remove("_id");
          DeploymentDescriptor md = Json.decodeValue(jo.encode(),
            DeploymentDescriptor.class);
          ml.add(md);
        }
        fut.handle(new Success<>(ml));
      }
    });
  }
}
