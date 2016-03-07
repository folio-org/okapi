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
import java.io.IOException;
import java.lang.management.ManagementFactory;
import okapi.bean.Modules;
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

public class MainVerticle extends AbstractVerticle {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private int port;
  private int port_start;
  private int port_end;
  private String storage;

  HealthService hc;
  ModuleManager ms;
  ModuleWebService moduleWebService;
  ProxyService ps;
  TenantWebService tenantWebService;


  // Little helper to get a config value
  // First from System (-D on command line),
  // then from config (from the way the vertcle gets deployed, f.ex. in tests
  // finally a default value
  static String conf(String key, String def, JsonObject c) {
    return System.getProperty(key, c.getString(key,def));
  }

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);

    JsonObject config = context.config();
    port = Integer.parseInt(conf("port", "9130",config));
    port_start = Integer.parseInt(conf("port_start", Integer.toString(port+1),config));
    port_end = Integer.parseInt(conf("port_end", Integer.toString(port_start+10),config));
    storage = conf("storage","inmemory",config);
    hc = new HealthService();

    TenantStore tenantStore = null;
    TenantManager tman = new TenantManager();

    Modules modules = new Modules();
    ms = new ModuleManager(vertx, modules, port_start, port_end);
    ModuleStore moduleStore = null;
    TimeStampStore timeStampStore = null;

    switch (storage) {
      case "mongo":
        MongoHandle mongo = new MongoHandle(vertx);
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
        System.out.println("FATAL: Unknown storage type '" + storage + "'");
        System.exit(1);
    }
    moduleWebService = new ModuleWebService(vertx, ms, moduleStore, timeStampStore );
    tenantWebService = new TenantWebService(vertx, tman, tenantStore);
    ps = new ProxyService(vertx, modules, tman);
  }

  public void NotFound(RoutingContext ctx) {
    ctx.response().setStatusCode(404).end("Okapi: unrecognized service");
  }

  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);
       
    //handle CORS
    router.route().handler(CorsHandler.create("*")
            .allowedMethod(HttpMethod.PUT)
            .allowedMethod(HttpMethod.DELETE)
            .allowedMethod(HttpMethod.GET)
            .allowedMethod(HttpMethod.POST)
            //allow request headers
            .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
            //expose response headers
            .exposedHeader(HttpHeaders.LOCATION.toString())
    );

    //hijack everything to conduit to allow for configuration
    router.route("/_*").handler(BodyHandler.create()); //enable reading body to string

    router.post("/_/modules/").handler(moduleWebService::create);
    router.delete("/_/modules/:id").handler(moduleWebService::delete);
    router.get("/_/modules/:id").handler(moduleWebService::get);
    router.get("/_/modules/").handler(moduleWebService::list);
    router.put("/_/modules/:id").handler(moduleWebService::update);

    router.post("/_/tenants").handler(tenantWebService::create);
    router.get("/_/tenants/").handler(tenantWebService::list);
    router.get("/_/tenants/:id").handler(tenantWebService::get);
    router.put("/_/tenants/:id").handler(tenantWebService::update);
    router.delete("/_/tenants/:id").handler(tenantWebService::delete);
    router.post("/_/tenants/:id/modules").handler(tenantWebService::enableModule);
    router.delete("/_/tenants/:id/modules/:mod").handler(tenantWebService::disableModule);
    router.get("/_/tenants/:id/modules").handler(tenantWebService::listModules);
    router.get("/_/health").handler(hc::get);

    // Endpoints for internal testing only.
    // The reload points can be removed as soon as we have a good integration
    // test that verifies that changes propagate across a cluster...
    router.get("/_/test/reloadmodules").handler(moduleWebService::reloadModules);
    router.get("/_/test/reloadtenant/:id").handler(tenantWebService::reloadTenant);

    router.route("/_*").handler(this::NotFound);
    
    //everything else gets proxified to modules
    router.route("/*").handler(ps::proxy);
    

    startListening(router, 3, 100, fut);
  }

  /**
   * Start the http server and start listening.
   * If it fails, retries a few times. If a (test) program terminates quickly,
   * the port may still be in use when the next (test) program starts up the
   * main verticle. 
   * @param router
   * @param retries
   * @param fut 
   */
  private void startListening(Router router, int retries, int delay, Future<Void> fut) {
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
            if ( retries > 0 ) {
              logger.fatal("createHttpServer failed: "
                + result.cause().getMessage()+ " Retry #" + retries + " " + delay + "ms");
              vertx.setTimer(delay, id -> {
                startListening(router, retries-1, delay*2, fut);
              });
            } else {
              logger.fatal("createHttpServer failed", result.cause() );
              fut.fail(result.cause());
            }
          }
        }
      );

  }

  @Override
  public void stop(Future<Void> fut) throws IOException {
    fut.complete();
  }
}
