package org.folio.okapi.bean;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Manages a list of available ports.
 * When a module is deployed, a new port may be allocated for it from this list.
 *
 */
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

  /**
   * Allocate a port.
   * @return the newly allocated port number, of -1 if none available
   */
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

  /**
   * Release a previously allocated port.
   * @param p The port to release.
   */
  public void free(int p) {
    if (p > 0) {
      logger.debug("free port " + p);
      ports[p - port_start] = false;
    }
  }
}
