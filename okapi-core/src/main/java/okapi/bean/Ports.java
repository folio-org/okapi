/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
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
