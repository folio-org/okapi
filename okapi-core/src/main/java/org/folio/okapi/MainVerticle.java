package org.folio.okapi;

import org.folio.okapi.service.ModuleManager;
import org.folio.okapi.web.TenantWebService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.deployment.DeploymentManager;
import org.folio.okapi.web.HealthService;
import org.folio.okapi.service.ModuleStore;
import org.folio.okapi.web.ModuleWebService;
import org.folio.okapi.service.ProxyService;
import org.folio.okapi.service.TenantManager;
import org.folio.okapi.service.TenantStore;
import org.folio.okapi.service.TimeStampStore;
import org.folio.okapi.util.LogHelper;
import static org.folio.okapi.common.HttpResponse.*;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.deployment.DeploymentWebService;
import org.folio.okapi.discovery.DiscoveryManager;
import org.folio.okapi.discovery.DiscoveryService;
import org.folio.okapi.service.impl.Storage;
import org.folio.okapi.toys.Receiver;
import org.folio.okapi.toys.Sender;

public class MainVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final LogHelper logHelper = new LogHelper();

  HealthService healthService;
  ModuleManager moduleManager;
  ModuleWebService moduleWebService;
  ProxyService proxyService;
  TenantWebService tenantWebService;
  DeploymentWebService deploymentWebService;
  DeploymentManager deploymentManager;
  DiscoveryService discoveryService;
  DiscoveryManager discoveryManager;
  ClusterManager clusterManager;
  private Storage storage;
  private int port;

  // Little helper to get a config value
  // First from System (-D on command line),
  // then from config (from the way the verticle gets deployed, e.g. in tests)
  // finally a default value
  static String conf(String key, String def, JsonObject c) {
    return System.getProperty(key, c.getString(key, def));
  }

  public void setClusterManager(ClusterManager mgr) {
    clusterManager = mgr;
  }

  @Override
  public void init(Vertx vertx, Context context) {
    InputStream in = getClass().getClassLoader().getResourceAsStream("git.properties");
    if (in != null) {
      try {
        Properties prop = new Properties();
        prop.load(in);
        in.close();
        logger.info("git: " + prop.getProperty("git.remote.origin.url")
                + " " + prop.getProperty("git.commit.id"));
      } catch (Exception e) {
        logger.warn(e.getMessage());
      }
    }
    boolean enableProxy = false;
    boolean enableDeployment = false;

    super.init(vertx, context);

    JsonObject config = context.config();
    port = Integer.parseInt(conf("port", "9130", config));
    int port_start = Integer.parseInt(conf("port_start", Integer.toString(port + 1), config));
    int port_end = Integer.parseInt(conf("port_end", Integer.toString(port_start + 10), config));

    if (clusterManager != null) {
      logger.info("cluster NodeId " + clusterManager.getNodeID());
    } else {
      logger.info("clusterManager not in use");
    }
    final String host = conf("host", "localhost", config);
    String okapiUrl = conf("okapiurl", "http://localhost:" + port , config);
    okapiUrl = okapiUrl.replaceAll("/+$", ""); // Remove trailing slash, if there
    String storageType = conf("storage", "inmemory", config);
    String loglevel = conf("loglevel", "", config);
    if (!loglevel.isEmpty()) {
      logHelper.setRootLogLevel(loglevel);
    }
    String mode = config.getString("mode", "cluster");
    switch (mode) {
      case "cluster":
      case "dev":
        enableDeployment = true;
        enableProxy = true;
        break;
      case "deployment":
        enableDeployment = true;
        break;
      case "proxy":
        enableProxy = true;
        break;
      default:
        logger.fatal("Unknown role '" + mode + "'");
        System.exit(1);
    }

    discoveryManager = new DiscoveryManager();
    if (clusterManager != null) {
      discoveryManager.setClusterManager(clusterManager);
    }
    if (enableDeployment) {
      Ports ports = new Ports(port_start, port_end);
      deploymentManager = new DeploymentManager(vertx, discoveryManager, host, ports, port);
      deploymentWebService = new DeploymentWebService(deploymentManager);
      Runtime.getRuntime().addShutdownHook(new Thread() {
        public void run() {
          CountDownLatch latch = new CountDownLatch(1);
          deploymentManager.shutdown(ar -> {
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
    if (enableProxy) {
      discoveryService = new DiscoveryService(discoveryManager);
      healthService = new HealthService();
      moduleManager = new ModuleManager(vertx);
      TenantManager tenantManager = new TenantManager(moduleManager);
      moduleManager.setTenantManager(tenantManager);

      if (discoveryManager != null && moduleManager != null) {
        discoveryManager.setModuleManager(moduleManager);
      }
      storage = new Storage(vertx, storageType, config);
      ModuleStore moduleStore = storage.getModuleStore();
      TimeStampStore timeStampStore = storage.getTimeStampStore();
      TenantStore tenantStore = storage.getTenantStore();
      logger.info("Proxy using " + storageType + " storage");
      moduleWebService = new ModuleWebService(vertx, moduleManager, moduleStore, timeStampStore);
      tenantWebService = new TenantWebService(vertx, tenantManager, tenantStore);

      proxyService = new ProxyService(vertx, moduleManager, tenantManager, discoveryManager, okapiUrl);
    }
  }

  public void NotFound(RoutingContext ctx) {
    responseText(ctx, 404).end("Okapi: unrecognized service");
  }

  @Override
  public void start(Future<Void> fut) {
    if (storage != null) {
      storage.resetDatabases(res -> {
        if (res.failed()) {
          logger.fatal("start failed", res.cause());
          fut.complete();
        } else {
          startModules(fut);
        }
      });
    } else {
      startModules(fut);
    }
  }

  private void startModules(Future<Void> fut) {
    if (moduleWebService == null) {
      startTenants(fut);
    } else {
      moduleWebService.loadModules(res -> {
        if (res.succeeded()) {
          startTenants(fut);
        } else {
          logger.fatal("load modules: " + res.cause().getMessage());
          fut.fail(res.cause());
        }
      });
    }
  }

  private void startTenants(Future<Void> fut) {
    if (tenantWebService == null) {
      startDiscovery(fut);
    } else {
      tenantWebService.loadTenants(res -> {
        if (res.succeeded()) {
          startDiscovery(fut);
        } else {
          logger.fatal("load tenants failed: " + res.cause().getMessage());
          fut.fail(res.cause());
        }
      });
    }
  }

  private void startDiscovery(Future<Void> fut) {
    if (discoveryManager == null) {
      startDeployment(fut);
    } else {
      discoveryManager.init(vertx, res -> {
        if (res.succeeded()) {
          startDeployment(fut);
        } else {
          fut.fail(res.cause());
        }
      });
    }
  }

  public void startDeployment(Future<Void> fut) {
    if (deploymentManager == null) {
      startListening(fut);
    } else {
      deploymentManager.init(res -> {
        if (res.succeeded()) {
          startListening(fut);
        } else {
          fut.fail(res.cause());
        }
      });
    }
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
            .allowedHeader(XOkapiHeaders.TENANT)
            .allowedHeader(XOkapiHeaders.TOKEN)
            .allowedHeader("Authorization")
            //expose response headers
            .exposedHeader(HttpHeaders.LOCATION.toString())
            .exposedHeader(XOkapiHeaders.TRACE)
            .exposedHeader(XOkapiHeaders.TOKEN)
    );

    // Paths that start with /_/ are okapi internal configuration
    router.route("/_*").handler(BodyHandler.create()); //enable reading body to string

    if (moduleWebService != null) {
      router.postWithRegex("/_/proxy/modules").handler(moduleWebService::create);
      router.delete("/_/proxy/modules/:id").handler(moduleWebService::delete);
      router.get("/_/proxy/modules/:id").handler(moduleWebService::get);
      router.getWithRegex("/_/proxy/modules").handler(moduleWebService::list);
      router.put("/_/proxy/modules/:id").handler(moduleWebService::update);
    }
    if (tenantWebService != null) {
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
    }
    // Endpoints for internal testing only.
    // The reload points can be removed as soon as we have a good integration
    // test that verifies that changes propagate across a cluster...
    if (moduleWebService != null) {
      router.getWithRegex("/_/test/reloadmodules").handler(moduleWebService::reloadModules);
      router.get("/_/test/reloadtenant/:id").handler(tenantWebService::reloadTenant);
      router.getWithRegex("/_/test/loglevel").handler(logHelper::getRootLogLevel);
      router.postWithRegex("/_/test/loglevel").handler(logHelper::setRootLogLevel);
    }

    if (deploymentWebService != null) {
      router.postWithRegex("/_/deployment/modules").handler(deploymentWebService::create);
      router.delete("/_/deployment/modules/:instid").handler(deploymentWebService::delete);
      router.getWithRegex("/_/deployment/modules").handler(deploymentWebService::list);
      router.get("/_/deployment/modules/:instid").handler(deploymentWebService::get);
    }
    if (discoveryService != null) {
      router.postWithRegex("/_/discovery/modules").handler(discoveryService::create);
      router.delete("/_/discovery/modules/:srvcid/:instid").handler(discoveryService::delete);
      router.get("/_/discovery/modules/:srvcid/:instid").handler(discoveryService::get);
      router.get("/_/discovery/modules/:srvcid").handler(discoveryService::getSrvcId);
      router.getWithRegex("/_/discovery/modules").handler(discoveryService::getAll);
      router.get("/_/discovery/health/:srvcid/:instid").handler(discoveryService::health);
      router.get("/_/discovery/health/:srvcid").handler(discoveryService::healthSrvcId);
      router.getWithRegex("/_/discovery/health").handler(discoveryService::healthAll);
      router.get("/_/discovery/nodes/:id").handler(discoveryService::getNode);
      router.getWithRegex("/_/discovery/nodes").handler(discoveryService::getNodes);
    }

    if (System.getProperty("toys.sender") != null) {
      Sender sender = new Sender(vertx);
      router.get("/_/sender/:message").handler(sender::send);
    }

    if (System.getProperty("toys.receiver") != null) {
      Receiver receiver = new Receiver(vertx);
    }

    router.route("/_*").handler(this::NotFound);

    //everything else gets proxified to modules
    if (proxyService != null) {
      router.route("/*").handler(proxyService::proxy);
    }
    HttpServerOptions so = new HttpServerOptions()
            .setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
            .requestHandler(router::accept)
            .listen(port,
                    result -> {
                      if (result.succeeded()) {
                        logger.info("API Gateway started PID "
                                + ManagementFactory.getRuntimeMXBean().getName()
                                + ". Listening on port " + port);
                        fut.complete();
                      } else {
                        logger.fatal("createHttpServer failed", result.cause());
                        fut.fail(result.cause());
                      }
                    }
            );
  }

}
