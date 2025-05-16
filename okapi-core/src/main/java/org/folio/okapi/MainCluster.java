package org.folio.okapi;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector;
import org.apache.logging.log4j.core.util.Constants;
import org.folio.okapi.common.OkapiLogger;

class MainCluster {
  private MainCluster() {
    throw new IllegalAccessError("MainCluster");
  }

  public static void main(String[] args) {
    System.setProperty("vertx.logger-delegate-factory-class-name",
        "io.vertx.core.logging.Log4jLogDelegateFactory");
    setLog4jContextSelector();
    Logger logger = OkapiLogger.get();
    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      if (res.failed()) {
        if (res.cause() instanceof StopVerticle) {
          System.exit(0);
        }
        logger.error(res.cause().getMessage(), res.cause());
        System.exit(1);
      }
    });
  }

  static void setLog4jContextSelector() {
    if (System.getProperty(Constants.LOG4J_CONTEXT_SELECTOR) != null) {
      return;
    }
    // set default: asynchronous loggers for low-latency logging
    System.setProperty(Constants.LOG4J_CONTEXT_SELECTOR,
        AsyncLoggerContextSelector.class.getName());
  }
}
