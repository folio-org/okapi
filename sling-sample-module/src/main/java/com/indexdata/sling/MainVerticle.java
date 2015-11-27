/*
 * Copyright (c) 1995-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import static io.vertx.core.Vertx.vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.IOException;

public class MainVerticle extends AbstractVerticle {

  public void my_handle(RoutingContext ctx) {
    System.out.println("my_handle " + ctx.request());
    ctx.response().setStatusCode(200).end("It works");
  }
          
  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);

    final int port = Integer.parseInt(System.getProperty("port", "8080"));
    System.out.println("Started sample-module on port " + port);
    //enable reading body to string
    router.route("/sample*").handler(BodyHandler.create()); 
    router.get("/sample").handler(this::my_handle);

    vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(
                    port,
                    result -> {
                      if (result.succeeded()) {
                        fut.complete();
                      } else {
                        fut.fail(result.cause());
                        vertx.close();
                      }
                    }
            );
  }

  @Override
  public void stop(Future<Void> fut) throws IOException {
    fut.complete();
  }

}
