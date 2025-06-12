package org.folio.okapi.bean;

import java.util.Arrays;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;

/**
 * Manages a list of available ports.
 * When a module is deployed, a new port may be allocated for it from this list.
 *
 */
public class Ports {

  private final int portStart;
  private final int portEnd;
  private final Boolean[] portsEnabled;

  private final Logger logger = OkapiLogger.get();

  /**
   * Construct port handler.
   * @param portStart start port range
   * @param portEnd end port range (last port + 1)
   */
  public Ports(int portStart, int portEnd) {
    this.portStart = portStart;
    this.portEnd = portEnd;
    portsEnabled = new Boolean[portEnd - portStart];
    Arrays.fill(portsEnabled, false);
  }

  /**
   * Allocate a port.
   * @return the newly allocated port number, of -1 if none available
   */
  public int get() {
    for (int i = 0; i < portsEnabled.length; i++) {
      if (Boolean.FALSE.equals(portsEnabled[i])) {
        portsEnabled[i] = true;
        final int p = i + portStart;
        logger.debug("allocate port {}", p);
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
      logger.debug("free port {}", p);
      if (p >= portStart && p < portEnd) {
        portsEnabled[p - portStart] = false;
      }
    }
  }
}
