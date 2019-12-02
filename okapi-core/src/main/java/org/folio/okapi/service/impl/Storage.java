package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.DeploymentStore;
import org.folio.okapi.service.EnvStore;
import org.folio.okapi.service.ModuleStore;
import org.folio.okapi.service.TenantStore;

public class Storage {

  private MongoHandle mongo;
  private PostgresHandle postgres;
  private ModuleStore moduleStore;
  private TenantStore tenantStore;
  private DeploymentStore deploymentStore;
  private EnvStore envStore;

  public enum InitMode {
    NORMAL, // normal operation
    INIT, // create database at startup
    PURGE  // purge the whole database
  }
  private JsonObject config;
  private final Logger logger = OkapiLogger.get();

  public Storage(Vertx vertx, String type, JsonObject config) {
    this.config = config;
    switch (type) {
      case "mongo":
        mongo = new MongoHandle(vertx, config);
        moduleStore = new ModuleStoreMongo(mongo.getClient());
        tenantStore = new TenantStoreMongo(mongo.getClient());
        deploymentStore = new DeploymentStoreMongo(mongo.getClient());
        envStore = new EnvStoreMongo(mongo.getClient());
        break;
      case "inmemory":
        moduleStore = null;
        tenantStore = null;
        deploymentStore = new DeploymentStoreNull();
        envStore = new EnvStoreNull();
        break;
      case "postgres":
        postgres = new PostgresHandle(vertx, config);
        moduleStore = new ModuleStorePostgres(postgres);
        tenantStore = new TenantStorePostgres(postgres);
        deploymentStore = new DeploymentStorePostgres(postgres);
        envStore = new EnvStorePostgres(postgres);
        break;
      default:
        logger.fatal("Unknown storage type '{}'", type);
        System.exit(1);
    }
  }

  public void prepareDatabases(InitMode initModeP, Handler<ExtendedAsyncResult<Void>> fut) {
    String dbInit = Config.getSysConf("mongo_db_init", "0", config);
    if (mongo != null && "1".equals(dbInit)) {
      initModeP = InitMode.INIT;
    }
    dbInit = Config.getSysConf("postgres_db_init", "0", config);
    if (postgres != null && "1".equals(dbInit)) {
      logger.warn("Will initialize the whole database!");
      logger.warn("The postgres_db_init option is DEPRECATED!"
        + " use 'initdatabase' command (instead of 'dev' on the command line)");
      initModeP = InitMode.INIT;
    }
    final InitMode initMode = initModeP;
    logger.info("prepareDatabases: {}", initMode);

    boolean reset = initMode != InitMode.NORMAL;
    envStore.init(reset, res1
      -> deploymentStore.init(reset, res2 -> {
        if (tenantStore == null) {
          fut.handle(new Success<>());
        } else {
          tenantStore.init(reset, res3
            -> moduleStore.init(reset, fut)
          );
        }
      })
    );
  }

  public ModuleStore getModuleStore() {
    return moduleStore;
  }

  public TenantStore getTenantStore() {
    return tenantStore;
  }

  public DeploymentStore getDeploymentStore() {
    return deploymentStore;
  }

  public EnvStore getEnvStore() {
    return envStore;
  }

}
