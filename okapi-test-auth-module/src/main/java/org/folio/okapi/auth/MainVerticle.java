/*
 * Copyright (C) 2015 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.folio.okapi.auth;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.IOException;
import java.lang.management.ManagementFactory;

/**
 * The auth module provides two services: login and check. URI /login takes
 * username, password, and other parameters, and returns a token. URI /check
 * takes the token, and verifies that everything is all right. This is a very
 * trivial dummy module, that provides simple hard-coded authentication for any
 * user who can append '-password' to his username to make a fake password.
 *
 * @author heikki
 *
 * ...
 */
public class MainVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger("okapi-test-auth-module");

  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);
    Auth auth = new Auth();

    final int port = Integer.parseInt(System.getProperty("port", "9020"));

    logger.info("Starting auth " + ManagementFactory.getRuntimeMXBean().getName() + " on port " + port);

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
                      } else {
                        logger.fatal("okapi-test-auth-module failed: " + result.cause());
                        fut.fail(result.cause());
                      }
                    }
            );
  }

}
