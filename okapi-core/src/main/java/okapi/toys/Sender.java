/*
 * Copyright (C) 2016 Index Data
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
package okapi.toys;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * @author jakub
 */
public class Sender {
  private final Vertx vertx;
  public Sender(Vertx vertx) {
    System.out.println("Enabling sender on okapi.toys.message");
    this.vertx = vertx;
  }  
  
  public void send(RoutingContext ctx) {
    String message = ctx.request().getParam("message");
    EventBus eventBus = vertx.eventBus();
    eventBus.publish("okapi.toys.messaging", message);
    ctx.response().end("Accepted message "+message);
  }
  
}
