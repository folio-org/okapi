/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import okapi.service.ModuleService;
import okapi.service.TenantService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import okapi.bean.Modules;
import okapi.service.HealthService;
import okapi.service.ModuleDbService;
import okapi.service.ProxyService;

public class MainVerticle extends AbstractVerticle {
  private final int port = Integer.parseInt(System.getProperty("port", "9130"));
  private final int port_start = Integer.parseInt(System.getProperty("port_start", "9131"));
  private final int port_end = Integer.parseInt(System.getProperty("port_end", "9140"));
  
  HealthService hc;
  ModuleService ms;
  ModuleDbService moduleDbService;
  ProxyService ps;
  TenantService ts;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    hc = new HealthService();
    ts = new TenantService(vertx);
    Modules modules = new Modules();
    ms = new ModuleService(vertx, modules, port_start, port_end);
    moduleDbService = new ModuleDbService(vertx, ms);
    ps = new ProxyService(vertx, modules, ts);
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
            .allowedMethod(HttpMethod.POST));

    //hijack everything to conduit to allow for configuration
    router.route("/_*").handler(BodyHandler.create()); //enable reading body to string
    router.post("/_/modules/").handler(moduleDbService::create);
    router.delete("/_/modules/:id").handler(moduleDbService::delete);
    router.get("/_/modules/:id").handler(moduleDbService::get);
    router.get("/_/modules/").handler(moduleDbService::list);
    router.post("/_/tenants").handler(ts::create);
    router.get("/_/tenants/").handler(ts::list);
    router.get("/_/tenants/:id").handler(ts::get);
    router.delete("/_/tenants/:id").handler(ts::delete);
    router.post("/_/tenants/:id/modules").handler(ts::enableModule);
    router.get("/_/tenants/:id/modules").handler(ts::listModules);
    router.get("/_/health").handler(hc::get);
    router.delete("/_/initmodules").handler(moduleDbService::init);
    router.get("/_/reloadmodules").handler(moduleDbService::reloadModules);

    router.route("/_*").handler(this::NotFound);
    
    //everything else gets proxified to modules
    router.route("/*").handler(ps::proxy);
    
    System.out.println("API Gateway started PID "
      + ManagementFactory.getRuntimeMXBean().getName()
      + ". Listening on port " + port );
    
    vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(port,
                    result -> {
                      if (result.succeeded()) {
                        fut.complete();
                      } else {
                        fut.fail(result.cause());
                      }
                    }
            );
  }

  @Override
  public void stop(Future<Void> fut) throws IOException {
    fut.complete();
  }
}
