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
import static io.vertx.core.Vertx.vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.IOException;

public class MainVerticle extends AbstractVerticle {
  private int port = Integer.parseInt(System.getProperty("port", "9130"));
  private int port_start = Integer.parseInt(System.getProperty("port_start", "9131"));
  private int port_end = Integer.parseInt(System.getProperty("port_end", "9140"));
  
  ModuleService ms;
  TenantService ts;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    ms = new ModuleService(vertx, port_start, port_end);
    ts = new TenantService(vertx);
  }
      
  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);
    
    //hijack everything to conduit to allow for configuration
    router.route("/_*").handler(BodyHandler.create()); //enable reading body to string
    router.post("/_/modules/").handler(ms::create);
    router.delete("/_/modules/:id").handler(ms::delete);
    router.get("/_/modules/:id").handler(ms::get);
    router.post("/_/tenant").handler(ts::create);
    router.get("/_/tenant/:id").handler(ts::get);
    router.delete("/_/tenant/:id").handler(ts::delete);
    
    //everything else gets proxified to modules
    router.route("/*").handler(ms::proxy);
    
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
