/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import okapi.service.ModuleManager;
import okapi.web.TenantWebService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okapi.bean.Ports;
import okapi.deployment.DeploymentManager;
import okapi.web.HealthService;
import okapi.service.ModuleStore;
import okapi.web.ModuleWebService;
import okapi.service.ProxyService;
import okapi.service.TenantManager;
import okapi.service.TenantStore;
import okapi.service.TimeStampStore;
import okapi.service.impl.ModuleStoreMemory;
import okapi.service.impl.ModuleStoreMongo;
import okapi.service.impl.MongoHandle;
import okapi.service.impl.TenantStoreMemory;
import okapi.service.impl.TenantStoreMongo;
import okapi.service.impl.TimeStampMemory;
import okapi.service.impl.TimeStampMongo;
import okapi.util.LogHelper;
import static okapi.util.HttpResponse.*;
import okapi.deployment.DeploymentWebService;
import okapi.discovery.DiscoveryManager;
import okapi.discovery.DiscoveryService;

public class MainVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final LogHelper logHelper = new LogHelper();

  private int port;
  private int port_start;
  private int port_end;
  private String storage;

  MongoHandle mongo = null;

  HealthService healthService;
  ModuleManager moduleManager;
  ModuleWebService moduleWebService;
  ProxyService proxyService;
  TenantWebService tenantWebService;
  DeploymentWebService deploymentWebService;
  DiscoveryService discoveryService;
  DiscoveryManager discoveryManager;
  Ports ports;

  // Little helper to get a config value
  // First from System (-D on command line),
  // then from config (from the way the verticle gets deployed, e.g. in tests)
  // finally a default value
  static String conf(String key, String def, JsonObject c) {
    return System.getProperty(key, c.getString(key, def));
  }

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);

    JsonObject config = context.config();
    port = Integer.parseInt(conf("port", "9130", config));
    port_start = Integer.parseInt(conf("port_start", Integer.toString(port + 1), config));
    port_end = Integer.parseInt(conf("port_end", Integer.toString(port_start + 10), config));
    final String host = conf("host", "localhost", config);
    ports = new Ports(port_start, port_end);
    storage = conf("storage", "inmemory", config);
    String loglevel = conf("loglevel", "", config);
    if (!loglevel.isEmpty()) {
      logHelper.setRootLogLevel(loglevel);
    }

    healthService = new HealthService();


    moduleManager = new ModuleManager(vertx);
    TenantStore tenantStore = null;
    TenantManager tman = new TenantManager(moduleManager);

    ModuleStore moduleStore = null;
    TimeStampStore timeStampStore = null;

    switch (storage) {

      case "mongo":
        mongo = new MongoHandle(vertx, config);
        moduleStore = new ModuleStoreMongo(mongo);
        timeStampStore = new TimeStampMongo(mongo);
        tenantStore = new TenantStoreMongo(mongo);
        break;
      case "inmemory":
        moduleStore = new ModuleStoreMemory(vertx);
        timeStampStore = new TimeStampMemory(vertx);
        tenantStore = new TenantStoreMemory();
        break;
      default:
        logger.fatal("Unknown storage type '" + storage + "'");
        System.exit(1);
    }
    moduleWebService = new ModuleWebService(vertx, moduleManager, moduleStore, timeStampStore);
    tenantWebService = new TenantWebService(vertx, tman, tenantStore);

    DeploymentManager dm = new DeploymentManager(vertx, host, ports);
    deploymentWebService = new DeploymentWebService(dm);

    discoveryManager = new DiscoveryManager();
    discoveryService = new DiscoveryService(discoveryManager);

    proxyService = new ProxyService(vertx, moduleManager, tman, discoveryManager);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        CountDownLatch latch = new CountDownLatch(1);
        moduleManager.deleteAll(ar -> {
          latch.countDown();
        });
        try {
          if (!latch.await(2, TimeUnit.MINUTES)) {
            logger.error("Timed out waiting to undeploy all");
          }
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
      }
    });
  }

  public void NotFound(RoutingContext ctx) {
    responseText(ctx, 404).end("Okapi: unrecognized service");
  }

  @Override
  public void start(Future<Void> fut) {
    if (mongo != null && mongo.isTransient()) {
      mongo.dropDatabase(res -> {
        if (res.succeeded()) {
          startModules(fut);
        } else {
          logger.fatal("createHttpServer failed", res.cause());
          fut.fail(res.cause());
        }
      });
    } else {
      startModules(fut);
    }
  }

  private void startModules(Future<Void> fut) {
    this.moduleWebService.loadModules(res -> {
       if (res.succeeded()) {
         startTenants(fut);
       } else {
         fut.fail(res.cause());
       }
     });
  }

  private void startTenants(Future<Void> fut) {
    this.tenantWebService.loadTenants(res -> {
      if (res.succeeded()) {
        startDiscovery(fut);
      } else {
        fut.fail(res.cause());
      }
    });
  }
  private void startDiscovery(Future<Void> fut) {
    this.discoveryManager.init(vertx, res -> {
      if (res.succeeded()) {
        startListening(fut);
      } else {
        fut.fail(res.cause());
      }
    });

  }

  private void startListening(Future<Void> fut) {
    Router router = Router.router(vertx);

    //handle CORS
    router.route().handler(CorsHandler.create("*")
            .allowedMethod(HttpMethod.PUT)
            .allowedMethod(HttpMethod.DELETE)
            .allowedMethod(HttpMethod.GET)
            .allowedMethod(HttpMethod.POST)
            //allow request headers
            .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
            .allowedHeader("X-Okapi-Tenant")
            .allowedHeader("X-Okapi-Token")
            //expose response headers
            .exposedHeader(HttpHeaders.LOCATION.toString())
            .exposedHeader("X-Okapi-Trace")
            .exposedHeader("X-Okapi-Token")
    );

    // Paths that start with /_/ are okapi internal configuration
    router.route("/_*").handler(BodyHandler.create()); //enable reading body to string


    router.postWithRegex("/_/proxy/modules").handler(moduleWebService::create);
    router.delete("/_/proxy/modules/:id").handler(moduleWebService::delete);
    router.get("/_/proxy/modules/:id").handler(moduleWebService::get);
    router.getWithRegex("/_/proxy/modules").handler(moduleWebService::list);
    router.put("/_/proxy/modules/:id").handler(moduleWebService::update);

    router.postWithRegex("/_/proxy/tenants").handler(tenantWebService::create);
    router.getWithRegex("/_/proxy/tenants").handler(tenantWebService::list);
    router.get("/_/proxy/tenants/:id").handler(tenantWebService::get);
    router.put("/_/proxy/tenants/:id").handler(tenantWebService::update);
    router.delete("/_/proxy/tenants/:id").handler(tenantWebService::delete);
    router.post("/_/proxy/tenants/:id/modules").handler(tenantWebService::enableModule);
    router.delete("/_/proxy/tenants/:id/modules/:mod").handler(tenantWebService::disableModule);
    router.get("/_/proxy/tenants/:id/modules").handler(tenantWebService::listModules);
    router.get("/_/proxy/tenants/:id/modules/:mod").handler(tenantWebService::getModule);
    router.getWithRegex("/_/proxy/health").handler(healthService::get);

    // Endpoints for internal testing only.
    // The reload points can be removed as soon as we have a good integration
    // test that verifies that changes propagate across a cluster...
    router.getWithRegex("/_/test/reloadmodules").handler(moduleWebService::reloadModules);
    router.get("/_/test/reloadtenant/:id").handler(tenantWebService::reloadTenant);
    router.getWithRegex("/_/test/loglevel").handler(logHelper::getRootLogLevel);
    router.postWithRegex("/_/test/loglevel").handler(logHelper::setRootLogLevel);

    router.postWithRegex("/_/deployment/modules").handler(deploymentWebService::create);
    router.delete("/_/deployment/modules/:instid").handler(deploymentWebService::delete);
    router.getWithRegex("/_/deployment/modules").handler(deploymentWebService::list);
    router.get("/_/deployment/modules/:instid").handler(deploymentWebService::get);
    router.put("/_/deployment/modules/:instid").handler(deploymentWebService::update);

    router.postWithRegex("/_/discovery/modules").handler(discoveryService::create);
    router.delete("/_/discovery/modules/:srvcid/:instid").handler(discoveryService::delete);
    router.get("/_/discovery/modules/:srvcid/:instid").handler(discoveryService::get);
    router.get("/_/discovery/modules/:srvcid").handler(discoveryService::getSrvcId);
    router.getWithRegex("/_/discovery/modules").handler(discoveryService::getAll);

    router.route("/_*").handler(this::NotFound);

    //everything else gets proxified to modules
    router.route("/*").handler(proxyService::proxy);

    vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(port,
                    result -> {
                      if (result.succeeded()) {
                        logger.info("API Gateway started PID "
                                + ManagementFactory.getRuntimeMXBean().getName()
                                + ". Listening on port " + port + " using '" + storage + "' storage");
                        fut.complete();
                      } else {
                        logger.fatal("createHttpServer failed", result.cause());
                        fut.fail(result.cause());
                      }
                    }
            );
  }

}
