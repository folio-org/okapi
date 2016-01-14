/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.auth;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.IOException;

/**
 * The auth module provides two services: login and check.
 *  /login takes username, password, and other parameters, and returns a token
 *  /check takes the token, and verifies that everything is all right
 * This is a very trivial dummy module, that provides simple hard-coded 
 * authentication for any user who can append '36' to his username to make
 * a fake password.
 *
 * @author heikki
 *
 * ...
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);
    Auth auth = new Auth();
    
    final int port = Integer.parseInt(System.getProperty("port", "9020"));
    
    router.post("/login").handler(BodyHandler.create());
    router.post("/login").handler(auth::login);
    router.route("/login").handler(auth::accept);  
    router.route("/*").handler(auth::check);
      
    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(
        port,
        result -> {
          if (result.succeeded()) {
            fut.complete();
            System.out.println("Auth listening on " + port);
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
