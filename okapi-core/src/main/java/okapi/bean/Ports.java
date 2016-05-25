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
package okapi.bean;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Ports {

  private final int port_start;
  private final int port_end;
  private final Boolean[] ports;

  private final Logger logger = LoggerFactory.getLogger("Ports");

  public Ports(int port_start, int port_end) {
    this.port_start = port_start;
    this.port_end = port_end;
    this.ports = new Boolean[port_end - port_start];
    for (int i = 0; i < ports.length; i++) {
      ports[i] = false;
    }
  }

  public int get() {
    for (int i = 0; i < ports.length; i++) {
      if (ports[i] == false) {
        ports[i] = true;
        final int p = i + port_start;
        logger.debug("allocate port " + p);
        return p;
      }
    }
    return -1;
  }

  public void free(int p) {
    if (p > 0) {
      logger.debug("free port " + p);
      ports[p - port_start] = false;
    }
  }
}
