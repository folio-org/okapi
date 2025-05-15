package org.folio.okapi;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.TooLongHttpHeaderException;
import io.netty.handler.codec.http.TooLongHttpLineException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.web.Router;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.MetricsUtil;
import org.folio.okapi.common.ModuleVersionReporter;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.OkapiStringUtil;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.managers.DeploymentManager;
import org.folio.okapi.managers.DiscoveryManager;
import org.folio.okapi.managers.EnvManager;
import org.folio.okapi.managers.HealthManager;
import org.folio.okapi.managers.InternalModule;
import org.folio.okapi.managers.KubernetesManager;
import org.folio.okapi.managers.ModuleManager;
import org.folio.okapi.managers.ProxyService;
import org.folio.okapi.managers.PullManager;
import org.folio.okapi.managers.TenantManager;
import org.folio.okapi.managers.TimerManager;
import org.folio.okapi.service.ModuleStore;
import org.folio.okapi.service.TenantStore;
import org.folio.okapi.service.impl.ModuleStoreNull;
import org.folio.okapi.service.impl.Storage;
import org.folio.okapi.service.impl.Storage.InitMode;
import org.folio.okapi.service.impl.TenantStoreNull;
import org.folio.okapi.util.CorsHelper;
import org.folio.okapi.util.EventBusChecker;
import org.folio.okapi.util.LogHelper;
import org.folio.okapi.util.OkapiError;

@java.lang.SuppressWarnings({"squid:S1192"})
public class MainVerticle extends AbstractVerticle {

  private static final int MAX_INITIAL_LINE_LENGTH = 8192;

  private final Logger logger = OkapiLogger.get();

  private ModuleManager moduleManager;
  private TenantManager tenantManager;
  private TimerManager timerManager;
  private EnvManager envManager;
  private ProxyService proxyService;
  private DeploymentManager deploymentManager;
  private DiscoveryManager discoveryManager;
  private ClusterManager clusterManager;
  private HealthManager healthManager;
  private KubernetesManager kubernetesManager;
  private Storage storage;
  private Storage.InitMode initMode = InitMode.NORMAL;
  private int port;
  private String okapiVersion = null;
  private final Messages messages = Messages.getInstance();
  boolean enableProxy = false;

  public void setClusterManager(ClusterManager mgr) {
    clusterManager = mgr;
  }

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    ModuleVersionReporter m = new ModuleVersionReporter("org.folio.okapi/okapi-core");
    okapiVersion = m.getVersion();
    m.logStart();

    boolean enableDeployment = false;

    super.init(vertx, context);

    JsonObject config = context.config();
    port = Integer.parseInt(Config.getSysConf("http.port", "port", "9130", config));
    String okapiVersion2 = Config.getSysConf("okapiVersion", null, config);
    if (okapiVersion2 != null) {
      okapiVersion = okapiVersion2;
    }
    if (clusterManager != null) {
      logger.info(messages.getMessage("10000", clusterManager.getNodeId()));
    } else {
      logger.info(messages.getMessage("10001"));
    }
    final String host = Config.getSysConf("host", "localhost", config);
    String okapiUrl = Config.getSysConf("okapiurl", "http://localhost:" + port, config);
    okapiUrl = OkapiStringUtil.trimTrailingSlashes(okapiUrl);
    final String nodeName = Config.getSysConf("nodename", null, config);
    String loglevel = Config.getSysConf("loglevel", System.getenv("OKAPI_LOGLEVEL"), config);
    if (loglevel != null && !loglevel.isEmpty()) {
      LogHelper.setRootLogLevel(loglevel);
    }
    String mode = config.getString("mode", "cluster");
    switch (mode) {
      case "deployment":
        enableDeployment = true;
        break;
      case "proxy":
        enableProxy = true;
        break;
      case "purgedatabase":
        initMode = InitMode.PURGE;
        enableProxy = true; // so we get to initialize the database. We exit soon after anyway
        break;
      case "initdatabase":
        initMode = InitMode.INIT;
        enableProxy = true;
        break;
      default: // cluster and dev
        enableDeployment = true;
        enableProxy = true;
        break;
    }

    String storageType = Config.getSysConf("storage", "inmemory", config);
    storage = new Storage(vertx, storageType, config);

    healthManager = new HealthManager(Integer.parseInt(
        Config.getSysConf("healthPort", "0", config)));
    envManager = new EnvManager(storage.getEnvStore());
    discoveryManager = new DiscoveryManager(storage.getDeploymentStore());
    if (clusterManager != null) {
      discoveryManager.setClusterManager(clusterManager);
    }
    kubernetesManager = new KubernetesManager(discoveryManager, config);
    if (enableDeployment) {
      deploymentManager = new DeploymentManager(vertx, discoveryManager, envManager,
          host, port, nodeName, config);
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        CountDownLatch latch = new CountDownLatch(1);
        deploymentManager.shutdown().onComplete(ar -> latch.countDown());
        try {
          if (!latch.await(2, TimeUnit.MINUTES)) {
            logger.error("Timed out waiting to undeploy all");
          }
        } catch (InterruptedException e) {
          logger.error("Exception while shutting down");
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }));
    }
    if (enableProxy) {
      ModuleStore moduleStore = storage.getModuleStore();
      moduleManager = new ModuleManager(moduleStore, false);
      TenantStore tenantStore = storage.getTenantStore();
      tenantManager = new TenantManager(moduleManager, tenantStore, false);
      discoveryManager.setModuleManager(moduleManager);
      logger.info("Proxy using {} storage", storageType);
      PullManager pullManager = new PullManager(vertx, moduleManager);
      InternalModule internalModule = new InternalModule(moduleManager,
          tenantManager, deploymentManager, discoveryManager,
          envManager, pullManager, kubernetesManager, okapiVersion);
      proxyService = new ProxyService(vertx, tenantManager, discoveryManager, internalModule,
          okapiUrl, config);
      tenantManager.setProxyService(proxyService);
      timerManager = new TimerManager(storage.getTimerStore(), false);
      internalModule.withTimerManager(timerManager);
    } else { // not really proxying, except to /_/deployment
      moduleManager = new ModuleManager(new ModuleStoreNull(), true);
      tenantManager = new TenantManager(moduleManager, new TenantStoreNull(), true);
      discoveryManager.setModuleManager(moduleManager);
      InternalModule internalModule = new InternalModule(
          null, null, deploymentManager, null,
          envManager, null, null, okapiVersion);
      // no modules, tenants, or discovery. Only deployment and env.
      proxyService = new ProxyService(vertx, tenantManager, discoveryManager, internalModule,
          okapiUrl, config);
    }
  }

  @Override
  public void stop(Promise<Void> promise) {
    logger.info("stop");
    MetricsUtil.stop();
    Future<Void> future = Future.succeededFuture();
    if (deploymentManager != null) {
      future = future.compose(x -> deploymentManager.shutdown());
    }
    future.compose(x -> discoveryManager.shutdown()).onComplete(promise);
  }

  @Override
  public void start(Promise<Void> promise) {
    Future<Void> fut = startDatabases();
    if (initMode == InitMode.NORMAL) {
      fut = fut.compose(x -> EventBusChecker.check(vertx, clusterManager)
          .recover(cause -> {
            logger.warn("event bus check failed {}", cause.getMessage());
            return Future.succeededFuture();
          }));
      fut = fut.compose(x -> startModuleManager());
      fut = fut.compose(x -> startTenants());
      fut = fut.compose(x -> checkInternalModules());
      fut = fut.compose(x -> startEnv());
      fut = fut.compose(x -> startDiscovery());
      fut = fut.compose(x -> startDeployment());
      fut = fut.compose(x -> kubernetesManager.init(vertx));
      fut = fut.compose(x -> startListening());
      fut = fut.compose(x -> startRedeploy());
      fut = fut.compose(x -> tenantManager.prepareModules(okapiVersion));
      fut = fut.compose(x -> startTimers());
      fut = fut.compose(x -> healthManager.init(vertx, Collections.singletonList(tenantManager)));
    }
    fut.onComplete(x -> {
      if (x.failed()) {
        logger.error(x.cause().getMessage());
      } else if (initMode != InitMode.NORMAL) {
        // no failure, must stop the Verticle from going further if initdatabase or similar
        vertx.close();
      }
      promise.handle(x);
    });
  }

  private Future<Void> startDatabases() {
    return storage.prepareDatabases(initMode);
  }

  private Future<Void> startModuleManager() {
    logger.info("startModuleManager");
    return moduleManager.init(vertx);
  }

  TenantManager getTenantManager() {
    return tenantManager;
  }

  private Future<Void> startTenants() {
    logger.info("startTenants");
    return tenantManager.init(vertx);
  }

  private Future<Void> checkInternalModules() {
    logger.info("checkInternalModules");
    final ModuleDescriptor md = InternalModule.moduleDescriptor(okapiVersion);
    final String okapiModule = md.getId();
    final String interfaceVersion = md.getProvides()[0].getVersion();
    return moduleManager.get(okapiModule).compose(
        gres -> {
          // we already have one, go on
          logger.debug("checkInternalModules: Already have {} "
              + " with interface version {}", okapiModule, interfaceVersion);
          // See Okapi-359 about version checks across the cluster
          return Future.succeededFuture();
        },
        cause -> {
          if (OkapiError.getType(cause) != ErrorType.NOT_FOUND) {
            return Future.failedFuture(cause); // something went badly wrong
          }
          logger.debug("Creating the internal Okapi module {} with interface version {}",
              okapiModule, interfaceVersion);
          return moduleManager.createList(Collections.singletonList(md), true, false);
        }).compose(x -> checkSuperTenant(okapiModule));
  }

  private Future<Void> checkSuperTenant(String okapiModule) {
    return tenantManager.get(XOkapiHeaders.SUPERTENANT_ID)
        .recover(cause -> {
          if (OkapiError.getType(cause) != ErrorType.NOT_FOUND) {
            return Future.failedFuture(cause); // something went badly wrong
          }
          logger.info("Creating the superTenant " + XOkapiHeaders.SUPERTENANT_ID);

          TenantDescriptor td = new TenantDescriptor();
          td.setId(XOkapiHeaders.SUPERTENANT_ID);
          td.setName(XOkapiHeaders.SUPERTENANT_ID);
          td.setDescription("Okapi built-in super tenant");
          SortedMap<String, Boolean> map = new TreeMap<>();
          map.put(okapiModule, true);
          Tenant tenant = new Tenant(td, map);
          return tenantManager.insert(tenant).mapEmpty();
        }).mapEmpty();
  }

  private Future<Void> startEnv() {
    logger.info("starting env");
    return envManager.init(vertx);
  }

  private Future<Void> startDiscovery() {
    logger.info("Starting discovery");
    return discoveryManager.init(vertx);
  }

  private Future<Void> startDeployment() {
    if (deploymentManager == null) {
      return Future.succeededFuture();
    }
    return deploymentManager.init();
  }

  private Future<Void> startListening() {
    Router router = Router.router(vertx);
    logger.debug("Setting up routes");

    //handle CORS
    CorsHelper.addCorsHandler(router, tenantManager);

    if (proxyService != null) {
      router.routeWithRegex("^/_/invoke/tenant/[^/ ]+/.*")
          .handler(proxyService::redirectProxy);
      // Note: This can not go into the InternalModule, it reads the req body,
      // and then we can not ctx.reroute(). Unless we do something trickier,
      // like a new HTTP request.
    }

    // everything else gets proxified to modules
    // Even internal functions, they are in the InternalModule
    if (proxyService != null) {
      router.route("/*").handler(proxyService::proxy);
    }

    logger.debug("About to start HTTP server");
    HttpServerOptions so = new HttpServerOptions()
        .setCompressionSupported(true)
        .setHandle100ContinueAutomatically(true)
        .setMaxInitialLineLength(MAX_INITIAL_LINE_LENGTH);
    return vertx.createHttpServer(so)
        .requestHandler(router)
        .invalidRequestHandler(MainVerticle::invalidRequestHandler)
        .listen(port)
        .onComplete(result -> {
          if (result.succeeded()) {
            logger.info("API Gateway started PID {}. Listening on port {}",
                ManagementFactory.getRuntimeMXBean().getName(), port);
          } else {
            logger.fatal("createHttpServer failed for port {}", port, result.cause());
          }
        })
        .mapEmpty();
  }

  static void invalidRequestHandler(HttpServerRequest httpServerRequest) {
    var cause = httpServerRequest.decoderResult().cause();
    var response = httpServerRequest.response();
    if (cause instanceof TooLongHttpLineException) {
      response.setStatusCode(HttpResponseStatus.REQUEST_URI_TOO_LONG.code())
          .end("Your request URI is too long.");
    } else if (cause instanceof TooLongHttpHeaderException) {
      response.setStatusCode(HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE.code())
          .end("Your HTTP request header fields are too large.");
    } else {
      HttpServerRequest.DEFAULT_INVALID_REQUEST_HANDLER.handle(httpServerRequest);
      return;
    }
    httpServerRequest.connection().close();
  }

  private Future<Void> startRedeploy() {
    return discoveryManager.restartModules();
  }

  private Future<Void> startTimers() {
    if (timerManager == null) {
      return Future.succeededFuture();
    }
    return timerManager.init(vertx, tenantManager, discoveryManager, proxyService);
  }
}
