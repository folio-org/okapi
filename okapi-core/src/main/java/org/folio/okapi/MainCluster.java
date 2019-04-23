package org.folio.okapi;

import io.vertx.core.logging.Logger;
import static java.lang.System.*;
import org.folio.okapi.common.OkapiLogger;

class MainCluster {
  private MainCluster() {
    throw new IllegalAccessError("MainCluster");
  }

  public static void main(String[] args) {
    Logger logger = OkapiLogger.get();
    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      if (res.failed()) {
        logger.error(res.cause());
        exit(1);
      }
    });
  }

}
