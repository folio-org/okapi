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
import io.vertx.ext.web.handler.BodyHandler;
import java.io.IOException;

/**
 *
 * @author jakub
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);

    String c = config().getString("service", "mock");
    //hijack everything to conduit to allow for configuration
    router.route("/conduit*").handler(BodyHandler.create()); //enable reading body to string
    //everything else gets proxified to modules
    router.get("/*").handler(null);

    vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(
                    // Retrieve the port from the configuration,
                    // default to 8080.
                    config().getInteger("http.port", 8080),
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
