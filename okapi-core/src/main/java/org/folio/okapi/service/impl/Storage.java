package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.DeploymentStore;
import org.folio.okapi.service.EnvStore;
import org.folio.okapi.service.ModuleStore;
import org.folio.okapi.service.TenantStore;
import org.folio.okapi.service.TimerStore;

public class Storage {

  private MongoHandle mongo;
  private PostgresHandle postgres;
  private final ModuleStore moduleStore;
  private final TenantStore tenantStore;
  private final DeploymentStore deploymentStore;
  private final EnvStore envStore;
  private final TimerStore timerStore;

  public enum InitMode {
    NORMAL, // normal operation
    INIT, // create database at startup
    PURGE  // purge the whole database
  }

  private final JsonObject config;
  private final Logger logger = OkapiLogger.get();

  /**
   * Create storage.
   * @param vertx Vert.x handle
   * @param type storage type "inmemory", "postgres", ..
   * @param config configuration
   */
  public Storage(Vertx vertx, String type, JsonObject config) {
    this.config = config;
    switch (type) {
      case "mongo":
        mongo = new MongoHandle(vertx, config);
        moduleStore = new ModuleStoreMongo(mongo.getClient());
        tenantStore = new TenantStoreMongo(mongo.getClient());
        deploymentStore = new DeploymentStoreMongo(mongo.getClient());
        envStore = new EnvStoreMongo(mongo.getClient());
        timerStore = new TimerStoreMongo(mongo.getClient());
        break;
      case "inmemory":
        moduleStore = null;
        tenantStore = new TenantStoreNull();
        deploymentStore = new DeploymentStoreNull();
        envStore = new EnvStoreNull();
        timerStore = new TimerStoreNull();
        break;
      case "postgres":
        postgres = new PostgresHandle(vertx, config);
        moduleStore = new ModuleStorePostgres(postgres);
        tenantStore = new TenantStorePostgres(postgres);
        deploymentStore = new DeploymentStorePostgres(postgres);
        envStore = new EnvStorePostgres(postgres);
        timerStore = new TimerStorePostgres(postgres);
        break;
      default:
        logger.fatal("Unknown storage type '{}'", type);
        throw new IllegalArgumentException("Unknown storage type: " + type);
    }
  }

  /**
   * prepare database.
   * @param mode initialize mode
   * @return future
   */
  public Future<Void> prepareDatabases(InitMode mode) {
    String dbInit = Config.getSysConf("mongo_db_init", "0", config);
    if (mongo != null && "1".equals(dbInit)) {
      mode = InitMode.INIT;
    }
    dbInit = Config.getSysConf("postgres_db_init", "0", config);
    if (postgres != null && "1".equals(dbInit)) {
      logger.warn("Will initialize the whole database!");
      logger.warn("The postgres_db_init option is DEPRECATED!"
          + " use 'initdatabase' command (instead of 'dev' on the command line)");
      mode = InitMode.INIT;
    }
    final InitMode initMode = mode;
    logger.info("prepareDatabases: {}", initMode);

    boolean reset = initMode != InitMode.NORMAL;

    return Future.succeededFuture()
        .compose(res -> envStore.init(reset))
        .compose(res -> deploymentStore.init(reset))
        .compose(res -> tenantStore.init(reset))
        .compose(res -> timerStore.init(reset))
        .compose(res -> {
          if (moduleStore == null) {
            return Future.succeededFuture();
          }
          return moduleStore.init(reset);
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

  public TimerStore getTimerStore() {
    return timerStore;
  }
}
