package org.folio.okapi.service.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.OkapiLogger;
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
        tenantStore = new TenantStoreNull();
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
        throw new IllegalArgumentException("Unknown storage type: " + type);
    }
  }

  public Future<Void> prepareDatabases(InitMode initModeP) {
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

    return Future.succeededFuture().compose(res -> {
      Promise<Void> promise = Promise.promise();
      envStore.init(reset, promise::handle);
      return promise.future();
    }).compose(res -> {
      Promise<Void> promise = Promise.promise();
      deploymentStore.init(reset, promise::handle);
      return promise.future();
    }).compose(res -> {
      Promise<Void> promise = Promise.promise();
      tenantStore.init(reset, promise::handle);
      return promise.future();
    }).compose(res -> {
      if (moduleStore == null) {
        return Future.succeededFuture();
      }
      Promise<Void> promise = Promise.promise();
      moduleStore.init(reset, promise::handle);
      return promise.future();
    });
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
