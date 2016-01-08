/*
 * Copyright (c) 1995-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling;

import com.indexdata.sling.conduit.service.ModuleService;
import com.indexdata.sling.conduit.service.TenantService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class MainVerticle extends AbstractVerticle {
  private final int port = Integer.parseInt(System.getProperty("port", "9130"));
  private final int port_start = Integer.parseInt(System.getProperty("port_start", "9131"));
  private final int port_end = Integer.parseInt(System.getProperty("port_end", "9140"));
  
  ModuleService ms;
  TenantService ts;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    ts = new TenantService(vertx);
    ms = new ModuleService(vertx, port_start, port_end, ts);
  }
      
  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);
    
    //hijack everything to conduit to allow for configuration
    router.route("/_*").handler(BodyHandler.create()); //enable reading body to string
    router.post("/_/modules/").handler(ms::create);
    router.delete("/_/modules/:id").handler(ms::delete);
    router.get("/_/modules/:id").handler(ms::get);
    router.post("/_/tenants").handler(ts::create);
    router.get("/_/tenants/").handler(ts::list);
    router.get("/_/tenants/:id").handler(ts::get);
    router.delete("/_/tenants/:id").handler(ts::delete);
    router.post("/_/tenants/:id/modules").handler(ts::enableModule);
    
    //everything else gets proxified to modules
    router.route("/*").handler(ms::proxy);
    
    System.out.println("API Gateway started PID " + ManagementFactory.getRuntimeMXBean().getName());
    
    vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(
                    // Retrieve the port from the configuration,
                    // default to 8080.
                    port,
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
