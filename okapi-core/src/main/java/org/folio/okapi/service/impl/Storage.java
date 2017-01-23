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
  private PostgresHandle postgres;
  private ModuleStore moduleStore;
  private TimeStampStore timeStampStore;
  private TenantStore tenantStore;

  public enum InitMode {
    NORMAL, // normal operation
    INIT, // create database at startup
    PURGE  // purge the whole database
  };
  private InitMode initMode;

  private final Logger logger = LoggerFactory.getLogger("okapi");

  public Storage(Vertx vertx, String type, InitMode initMode, JsonObject config) {
    timeStampStore = new TimeStampMemory(vertx);
    this.initMode = initMode;
    switch (type) {
      case "mongo":
        mongo = new MongoHandle(vertx, config);
        moduleStore = new ModuleStoreMongo(mongo.getClient());
        tenantStore = new TenantStoreMongo(mongo.getClient());
        break;
      case "inmemory":
        moduleStore = new ModuleStoreMemory(vertx);
        tenantStore = new TenantStoreMemory();
        break;
      case "postgres":
        postgres = new PostgresHandle(vertx, config);
        moduleStore = new ModuleStorePostgres(postgres);
        tenantStore = new TenantStorePostgres(postgres);
        break;
      default:
        logger.fatal("Unknown storage type '" + type + "'");
        System.exit(1);
    }
  }

  public void resetDatabases(Handler<ExtendedAsyncResult<Void>> fut) {
    logger.warn("Storage.resetDatabases: " + initMode);
    if (mongo != null) {
      if (initMode != InitMode.NORMAL) {
        logger.warn("Mong backend does not support the init/purge database commands");
      } // This does not matter, we will drop mongo soon
      mongo.resetDatabases(fut);
    } else if (postgres != null) {
      TenantStorePostgres tnp = (TenantStorePostgres) tenantStore;
      tnp.resetDatabase(initMode, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          ModuleStorePostgres mnp = (ModuleStorePostgres) moduleStore;
          mnp.resetDatabase(initMode, fut);
        }
      });
    } else {
      // inmemory will always ignore the database things, it always starts with
      // nothing in its in-memory arrays
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
