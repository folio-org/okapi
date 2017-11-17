package org.folio.okapi.service.impl;

import org.folio.okapi.service.ModuleStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
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

  private MongoClient cli;
  private static final String COLLECTION = "okapi.modules";
  private final MongoUtil<ModuleDescriptor> util;


  public ModuleStoreMongo(MongoClient cli) {
    this.cli = cli;
    this.util = new MongoUtil(COLLECTION, cli);
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    util.init(reset, fut);
  }

  @Override
  public void insert(ModuleDescriptor md,
          Handler<ExtendedAsyncResult<String>> fut) {
    String s = Json.encodePrettily(md);
    JsonObject document = new JsonObject(s);
    String id = md.getId();
    document.put("_id", id);
    cli.insert(COLLECTION, document, res -> {
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
    Handler<ExtendedAsyncResult<Void>> fut) {

    util.add(md, md.getId(), fut);
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    util.getAll(ModuleDescriptor.class, fut);
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    util.delete(id, fut);
  }

}
