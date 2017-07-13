package org.folio.okapi;

import org.folio.okapi.service.ModuleManager;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import java.io.InputStream;
import static java.lang.System.getenv;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.bean.Tenant;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import org.folio.okapi.deployment.DeploymentManager;
import org.folio.okapi.web.HealthService;
import org.folio.okapi.service.ModuleStore;
import org.folio.okapi.service.ProxyService;
import org.folio.okapi.service.TenantManager;
import org.folio.okapi.service.TenantStore;
import org.folio.okapi.util.LogHelper;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.deployment.DeploymentWebService;
import org.folio.okapi.discovery.DiscoveryManager;
import org.folio.okapi.discovery.DiscoveryService;
import org.folio.okapi.env.EnvManager;
import org.folio.okapi.pull.PullManager;
import org.folio.okapi.service.impl.Storage;
import static org.folio.okapi.service.impl.Storage.InitMode.*;
import org.folio.okapi.util.ProxyContext;
import org.folio.okapi.web.InternalModule;

public class MainVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final LogHelper logHelper = new LogHelper();

  HealthService healthService;
  ModuleManager moduleManager;
  TenantManager tenantManager;
  EnvManager envManager;
  ProxyService proxyService;
  DeploymentWebService deploymentWebService;
  DeploymentManager deploymentManager;
  DiscoveryService discoveryService;
  DiscoveryManager discoveryManager;
  ClusterManager clusterManager;
  PullManager pullManager;
  private Storage storage;
  Storage.InitMode initMode = NORMAL;
  private int port;
  private String okapiVersion = null;


  // Little helper to get a config value:
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
    InputStream in = getClass().getClassLoader().
      getResourceAsStream("META-INF/maven/org.folio.okapi/okapi-core/pom.properties");
    if (in != null) {
      try {
        Properties prop = new Properties();
        prop.load(in);
        in.close();
        okapiVersion = prop.getProperty("version");
        logger.info(prop.getProperty("artifactId") + " " + okapiVersion);
      } catch (Exception e) {
        logger.warn(e);
      }
    }

    in = getClass().getClassLoader().getResourceAsStream("git.properties");
    if (in != null) {
      try {
        Properties prop = new Properties();
        prop.load(in);
        in.close();
        logger.info("git: " + prop.getProperty("git.remote.origin.url")
                + " " + prop.getProperty("git.commit.id"));
      } catch (Exception e) {
        logger.warn(e);
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
    } else {
      String lev = getenv("OKAPI_LOGLEVEL");
      if (lev != null && !lev.isEmpty()) {
        logHelper.setRootLogLevel(loglevel);
      }
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
      case "purgedatabase":
        initMode = PURGE;
        enableProxy = true; // so we get to initialize the database. We exit soon after anyway
        break;
      case "initdatabase":
        initMode = INIT;
        enableProxy = true;
        break;
      default:
        logger.fatal("Unknown role '" + mode + "'");
        System.exit(1);
    }

    envManager = new EnvManager();
    discoveryManager = new DiscoveryManager();
    if (clusterManager != null) {
      discoveryManager.setClusterManager(clusterManager);
    }
    if (enableDeployment) {
      Ports ports = new Ports(port_start, port_end);
      deploymentManager = new DeploymentManager(vertx, discoveryManager, envManager,
              host, ports, port);
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
      storage = new Storage(vertx, storageType, config);
      discoveryService = new DiscoveryService(discoveryManager);
      healthService = new HealthService();
      ModuleStore moduleStore = storage.getModuleStore();
      moduleManager = new ModuleManager(vertx, moduleStore);
      TenantStore tenantStore = storage.getTenantStore();
      tenantManager = new TenantManager(moduleManager, tenantStore);
      moduleManager.setTenantManager(tenantManager);
      discoveryManager.setModuleManager(moduleManager);
      logger.info("Proxy using " + storageType + " storage");
      pullManager = new PullManager(vertx, okapiUrl);
      InternalModule internalModule = new InternalModule(moduleManager, 
              tenantManager, envManager, pullManager,okapiVersion);
      proxyService = new ProxyService(vertx,
        moduleManager, tenantManager, discoveryManager,
        internalModule, okapiUrl);
      tenantManager.setProxyService(proxyService);
    }
  }

  public void NotFound(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.notfound");
    String slash = "";
    if (ctx.request().path().endsWith("/")) {
      slash = "  Try without a trailing slash";
    }
    pc.responseError(404, "Okapi: unrecognized service "
      + ctx.request().path() + slash);

  }

  @Override
  public void start(Future<Void> fut) {
    logger.debug("starting");
    if (storage != null) {
      storage.prepareDatabases(initMode, res -> {
        if (res.failed()) {
          logger.fatal("start failed", res.cause());
          fut.fail(res.cause());
        } else {
          if (initMode != NORMAL) {
            logger.info("Database operation " + initMode.toString() + " done. Exiting");
            System.exit(0);
          }
          startModmanager(fut);
        }
      });
    } else {
      startModmanager(fut);
    }
  }

  private void startModmanager(Future<Void> fut) {
    if (moduleManager == null) {
      startTenants(fut);
    } else {
      logger.debug("Starting modules");
      moduleManager.init(vertx, res -> {
        if (res.succeeded()) {
          startTenants(fut);
        } else {
          logger.fatal("ModuleManager init: " + res.cause().getMessage());
          fut.fail(res.cause());
        }
      });
    }
  }

  private void startTenants(Future<Void> fut) {
    if (tenantManager == null) {
      checkInternalModules(fut);
    } else {
      logger.debug("Startting tenants");
      tenantManager.init(vertx, res -> {
        if (res.succeeded()) {
          checkInternalModules(fut);
        } else {
          logger.fatal("load tenants failed: " + res.cause().getMessage());
          fut.fail(res.cause());
        }
      });
    }
  }


  private void checkInternalModules(Future<Void> fut) {
    // TODO - Refactor most of this into InternalModule.
    String v = okapiVersion;
    if (v == null) {  // happens at compile time,
      v = "0.0.0";   // unit tests can just check for this
    }
    String okapiModule = XOkapiHeaders.OKAPI_MODULE + "-" + v;
    String interfaceVersion = v.replaceFirst("^(\\d+)\\.(\\d+)\\.(\\d*).*$", "$1.$2");
    if (moduleManager == null) {
      logger.debug("checkInternalModules: skipping, no moduleManager");
      checkSuperTenant(okapiModule, fut);
      return;
    }
    moduleManager.get(okapiModule, gres -> {
      if (gres.succeeded()) { // we already have one, go on
        logger.debug("checkInternalModules: Already have " + okapiModule
          + " with interface version " + interfaceVersion);
        // TODO - What if it is a wrong version?
        checkSuperTenant(okapiModule, fut);
        return;
      }
      if (gres.getType() != NOT_FOUND) {
        logger.warn("checkInternalModules: Could not get "
          + okapiModule + ": " + gres.cause());
        fut.fail(gres.cause()); // something went badly wrong
        return;
      }
      logger.debug("Creating the internal Okapi module " + okapiModule
        + " with interface version " + interfaceVersion);
      final String doc = "{"
        + " \"id\" : \"" + okapiModule + "\","
        + " \"name\" : \"" + okapiModule + "\","
        + " \"provides\" : [ {"
        + "   \"id\" : \"okapi\","
        + "   \"version\" : \"" + interfaceVersion + "\","
        + "   \"interfaceType\" : \"internal\","
        + "   \"handlers\" : [ {"
        + "    \"methods\" :  [ \"*\" ],"  // TODO - set them up one by one, with permissions
        + "    \"pathPattern\" : \"/_/proxy/tenants*\","
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"*\" ],"
        + "    \"pathPattern\" : \"/_/proxy/modules*\","
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"POST\" ],"
        + "    \"pathPattern\" : \"/_/proxy/pull*\","
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/proxy/health*\","
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"*\" ],"
        + "    \"pathPattern\" : \"/_/env*\","
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/version*\","
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\", \"POST\" ],"
        + "    \"pathPattern\" : \"/_/test*\","
        + "    \"type\" : \"internal\" "
        + "   } ]"
        + " } ]"
        + "}";

      final ModuleDescriptor md = Json.decodeValue(doc, ModuleDescriptor.class);
      moduleManager.create(md, ires -> {
        if (ires.failed()) {
          logger.warn("Failed to create the internal Okapi module"
            + okapiModule + " " + ires.cause());
          fut.fail(ires.cause()); // something went badly wrong
          return;
        }
        checkSuperTenant(okapiModule, fut);
      });

    });

  }

  /**
   * Create the super tenant, if not already there.
   *
   * @param fut
   */
  private void checkSuperTenant(String okapiModule, Future<Void> fut) {
    if (tenantManager == null) {
      logger.debug("checkSuperTenant: Skipping, no tenantManager");
      startEnv(fut);
      return;
    }
    tenantManager.get(XOkapiHeaders.SUPERTENANT_ID, gres -> {
      if (gres.succeeded()) { // we already have one, go on
        logger.debug("checkSuperTenant: Already have " + XOkapiHeaders.SUPERTENANT_ID);
        startEnv(fut);
        return;
      }
      if (gres.getType() != NOT_FOUND) {
        logger.warn("checkSuperTenant: Could not get "
          + XOkapiHeaders.SUPERTENANT_ID + ": " + gres.cause());
        fut.fail(gres.cause()); // something went badly wrong
        return;
      }
      logger.debug("Creating the superTenant " + XOkapiHeaders.SUPERTENANT_ID);
      final String docTenant = "{"
        + "\"descriptor\" : {"
        + " \"id\" : \"" + XOkapiHeaders.SUPERTENANT_ID + "\","
        + " \"name\" : \"" + XOkapiHeaders.SUPERTENANT_ID + "\","
        + " \"description\" : \"Okapi built-in super tenant\""
        + " },"
        + "\"enabled\" : {"
        + "\"" + okapiModule + "\" : true"
        + "}"
        + "}";
      final Tenant ten = Json.decodeValue(docTenant, Tenant.class);
      tenantManager.insert(ten, ires -> {
        if (ires.failed()) {
          logger.warn("Failed to create the superTenant "
            + XOkapiHeaders.SUPERTENANT_ID + " " + ires.cause());
          fut.fail(ires.cause()); // something went badly wrong
          return;
        }
        startEnv(fut);
        return;
      });
    });
  }


  private void startEnv(Future<Void> fut) {
    if (envManager == null) {
      logger.debug("startEnv: no envManager, skipping");
      startDiscovery(fut);
    } else {
      logger.debug("starting Env");
      envManager.init(vertx, res -> {
        if (res.succeeded()) {
          startDiscovery(fut);
        } else {
          fut.fail(res.cause());
        }
      });
    }
  }

  private void startDiscovery(Future<Void> fut) {
    if (discoveryManager == null) {
      startDeployment(fut);
    } else {
      logger.debug("Starting discovery");
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
      logger.debug("Starting deployment");
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
    logger.debug("Setting up routes");
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
            .allowedHeader(XOkapiHeaders.AUTHORIZATION)
      .allowedHeader(XOkapiHeaders.REQUEST_ID)            //expose response headers
            .exposedHeader(HttpHeaders.LOCATION.toString())
            .exposedHeader(XOkapiHeaders.TRACE)
            .exposedHeader(XOkapiHeaders.TOKEN)
            .exposedHeader(XOkapiHeaders.AUTHORIZATION)
      .exposedHeader(XOkapiHeaders.REQUEST_ID)
    );

    if (proxyService != null) {
      router.routeWithRegex("/_/invoke/tenant/[^/ ]+/.*")
        .handler(proxyService::redirectProxy);
      // Note: This can not go into the InternalModule, it reads the req body,
      // and then we can not ctx.reroute(). Unless we do something trickier,
      // like a new HTTP request.
    }


    // TODO - Refactor these into InternalModule too
    if (deploymentWebService != null) {
      router.route("/_/deployment/*").handler(BodyHandler.create()); //enable reading body to string
      router.postWithRegex("/_/deployment/modules").handler(deploymentWebService::create);
      router.delete("/_/deployment/modules/:instid").handler(deploymentWebService::delete);
      router.getWithRegex("/_/deployment/modules").handler(deploymentWebService::list);
      router.get("/_/deployment/modules/:instid").handler(deploymentWebService::get);
    }
    if (discoveryService != null) {
    router.route("/_/discovery/*").handler(BodyHandler.create()); //enable reading body to string
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

    //router.route("/_/*").handler(this::NotFound);

    // everything else gets proxified to modules
    if (proxyService != null) {
      router.route("/*").handler(proxyService::proxy);
    }
    logger.debug("About to start HTTP server");
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
