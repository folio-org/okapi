package org.folio.okapi;

import static java.lang.System.*;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;

class MainCluster {
  private MainCluster() {
    throw new IllegalAccessError("MainCluster");
  }

  public static void main(String[] args) {
    System.setProperty("vertx.logger-delegate-factory-class-name",
     "io.vertx.core.logging.Log4jLogDelegateFactory");
    Logger logger = OkapiLogger.get();
    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      if (res.failed()) {
        logger.error(res.cause().getMessage(), res.cause());
        exit(1);
      }
    });
  }

}
