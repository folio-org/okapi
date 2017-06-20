package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.ModuleStore;
import org.folio.okapi.service.TenantStore;

public class Storage {

  private MongoHandle mongo;
  private PostgresHandle postgres;
  private ModuleStore moduleStore;
  private TenantStore tenantStore;

  public enum InitMode {
    NORMAL, // normal operation
    INIT, // create database at startup
    PURGE  // purge the whole database
  };
  private JsonObject config;
  private final Logger logger = LoggerFactory.getLogger("okapi");

  public Storage(Vertx vertx, String type, JsonObject config) {
    this.config = config;
    switch (type) {
      case "mongo":
        mongo = new MongoHandle(vertx, config);
        moduleStore = new ModuleStoreMongo(mongo.getClient());
        tenantStore = new TenantStoreMongo(mongo.getClient());
        break;
      case "inmemory":
        moduleStore = null;
        tenantStore = null;
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

  public void prepareDatabases(InitMode initModeP, Handler<ExtendedAsyncResult<Void>> fut) {
    String db_init = Config.getSysConf("mongo_db_init", "0", config);
    if (mongo != null && "1".equals(db_init)) {
      initModeP = InitMode.INIT;
    }
    db_init = Config.getSysConf("postgres_db_init", "0", config);
    if (postgres != null && "1".equals(db_init)) {
      logger.warn("Will initialize the whole database!");
      logger.warn("The postgres_db_init option is DEPRECATED!"
        + " use 'initdatabase' command (instead of 'dev' on the command line)");
      initModeP = InitMode.INIT;
    }
    final InitMode initMode = initModeP;
    logger.info("prepareDatabases: " + initMode);
    if (initMode == InitMode.NORMAL) {
      fut.handle(new Success<>());
    } else if (mongo != null) {
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

}
