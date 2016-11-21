package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.ModuleStore;
import org.folio.okapi.service.TenantStore;
import org.folio.okapi.service.TimeStampStore;

public class Storage {

  private MongoHandle mongo;
  private ModuleStore moduleStore;
  private TimeStampStore timeStampStore;
  private TenantStore tenantStore;

  private final Logger logger = LoggerFactory.getLogger("okapi");

  public Storage(Vertx vertx, String type, JsonObject config) {
    switch (type) {
      case "mongo":
        mongo = new MongoHandle(vertx, config);
        moduleStore = new ModuleStoreMongo(mongo.getClient());
        timeStampStore = new TimeStampMongo(mongo.getClient());
        tenantStore = new TenantStoreMongo(mongo.getClient());
        break;
      case "inmemory":
        moduleStore = new ModuleStoreMemory(vertx);
        timeStampStore = new TimeStampMemory(vertx);
        tenantStore = new TenantStoreMemory();
        break;
      default:
        logger.fatal("Unknown storage type '" + type + "'");
        System.exit(1);
    }
  }

  public void resetDatabases(Handler<ExtendedAsyncResult<Void>> fut) {
    if (mongo != null) {
      mongo.resetDatabases(fut);
    } else {
      fut.handle(new Success<>());
    }
  }

  public ModuleStore getModuleStore() {
    return moduleStore;
  }

  public TenantStore getTenantStore() {
    return tenantStore;
  }

  public TimeStampStore getTimeStampStore() {
    return timeStampStore;
  }
}
