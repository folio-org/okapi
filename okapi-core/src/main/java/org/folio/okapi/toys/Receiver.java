package org.folio.okapi.toys;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

/**
 *
 * @author jakub
 */
public class Receiver {

  public Receiver(Vertx vertx) {
    System.out.println("Enabling receiver on okapi.toys.messaging");
    EventBus eb = vertx.eventBus();

    eb.consumer("okapi.toys.messaging", message -> {
      System.out.println("I have received a message: " + message.body());
    });
  }


}
