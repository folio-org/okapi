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
package okapi.header;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class MainVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger("okapi-test-header-module");

  public void my_header_handle(RoutingContext ctx) {
    String h = ctx.request().getHeader("X-my-header");
    if (h == null) {
      h = "foo";
    } else {
      h += ",foo";
    }
    final String hv = h;
    ctx.response().putHeader("X-my-header", hv);
    ctx.request().endHandler(x -> {
      ctx.response().end();
    });
  }

  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);

    final int port = Integer.parseInt(System.getProperty("port", "8080"));
    logger.info("Starting header " + ManagementFactory.getRuntimeMXBean().getName() + " on port " + port);

    router.get("/sample").handler(this::my_header_handle);
    router.post("/sample").handler(this::my_header_handle);

    vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(
                    port,
                    result -> {
                      if (result.succeeded()) {
                        fut.complete();
                      } else {
                        fut.fail(result.cause());
                        logger.error("header failed: " + result.cause());
                      }
                    }
            );
  }
}
