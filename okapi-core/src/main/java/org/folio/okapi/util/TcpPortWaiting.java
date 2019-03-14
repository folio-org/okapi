package org.folio.okapi.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;

public class TcpPortWaiting {
  
  private final Logger logger = OkapiLogger.get();
  private static final int MILLISECONDS = 200;
  private Messages messages = Messages.getInstance();
  
  private int maxIterations = 30; // x*(x+1) * 0.1 seconds.
  private int port;
  private Vertx vertx;

  public TcpPortWaiting(Vertx vertx, int port) {
    this.vertx = vertx;
    this.port = port;
  }

  private void tryConnect(Process process, int count, Handler<AsyncResult<Void>> startFuture) {
    NetClientOptions options = new NetClientOptions().setConnectTimeout(MILLISECONDS);
    NetClient c = vertx.createNetClient(options);
    logger.info("tryConnect() port " + port + " count " + count);
    c.connect(port, "localhost", res -> {
      if (res.succeeded()) {
        logger.info("Connected to service at port " + port + " count " + count);
        NetSocket socket = res.result();
        socket.close();
        if (process != null) {
          try {
            process.getErrorStream().close();
          } catch (Exception e) {
            logger.error("Closing streams failed: " + e);
          }
        }
        startFuture.handle(Future.succeededFuture());
      } else if (process != null && !process.isAlive() && process.exitValue() != 0) {
        logger.warn("Service returned with exit code " + process.exitValue());
        startFuture.handle(Future.failedFuture(messages.getMessage("11500", process.exitValue())));
      } else if (count < maxIterations) {
        vertx.setTimer((long) (count + 1) * MILLISECONDS,
          id -> tryConnect(process, count + 1, startFuture));
      } else {
        startFuture.handle(Future.failedFuture(messages.getMessage("11501",
          Integer.toString(port), res.cause().getMessage())));
      }
    });
  }
  
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  public void waitReady(Process process, Handler<AsyncResult<Void>> startFuture) {
    if (port == 0) {
      startFuture.handle(Future.succeededFuture());
    } else {
      tryConnect(process, 0, startFuture);
    }
  }
}
