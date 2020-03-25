package org.folio.okapi.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;

public class TcpPortWaiting {

  private final Logger logger = OkapiLogger.get();
  private static final int MILLISECONDS = 200;
  private final Messages messages = Messages.getInstance();

  private int maxIterations = 30; // x*(x+1) * 0.1 seconds.
  private final Vertx vertx;
  private final String host;
  private final int port;

  /**
   * Create TCP port waiting utility.
   * @param vertx Vert.x handle
   * @param host host for server that utility it waiting for
   * @param port port for server; special value 0 will disable waiting for the server
   */
  public TcpPortWaiting(Vertx vertx, String host, int port) {
    this.vertx = vertx;
    this.host = host;
    this.port = port;
  }

  private void tryConnect(Process process, int count, Handler<AsyncResult<Void>> startFuture) {
    NetClientOptions options = new NetClientOptions().setConnectTimeout(MILLISECONDS);
    NetClient c = vertx.createNetClient(options);
    logger.info("tryConnect() host {} port {} count {}", host, port, count);
    c.connect(port, host, res -> {
      if (res.succeeded()) {
        logger.info("Connected to service at host {} port {} count {}", host, port, count);
        NetSocket socket = res.result();
        socket.close();
        if (process != null) {
          try {
            process.getErrorStream().close();
          } catch (Exception e) {
            logger.error("Closing streams failed: {}", e.getMessage(), e);
          }
        }
        startFuture.handle(Future.succeededFuture());
      } else if (process != null && !process.isAlive() && process.exitValue() != 0) {
        logger.warn("Service returned with exit code {}", process.exitValue());
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

  /**
   * Wait for process and server to be listening.
   * @param process Process to monitor
   * @param startFuture async result
   */
  public void waitReady(Process process, Handler<AsyncResult<Void>> startFuture) {
    if (port == 0) {
      startFuture.handle(Future.succeededFuture());
    } else {
      tryConnect(process, 0, startFuture);
    }
  }
}
