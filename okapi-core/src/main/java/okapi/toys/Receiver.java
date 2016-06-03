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
