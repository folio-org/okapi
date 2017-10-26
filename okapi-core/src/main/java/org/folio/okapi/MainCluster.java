package org.folio.okapi;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import static java.lang.System.*;

public class MainCluster {
  private MainCluster() {
    throw new IllegalAccessError("MainCluster");
  }

  public static void main(String[] args) {
    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      if (res.failed()) {
        // use logger after vert.x logger is configured in d.init
        Logger logger = LoggerFactory.getLogger("okapi");
        logger.error(res.cause());
        exit(1);
      }
    });
  }

}
