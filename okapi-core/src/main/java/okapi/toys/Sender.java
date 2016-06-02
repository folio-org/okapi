/*
 * Copyright (c) 1995-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
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
