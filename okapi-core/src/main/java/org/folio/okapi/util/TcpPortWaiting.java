package org.folio.okapi.util;

import com.zaxxer.nuprocess.NuProcess;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
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

  private Future<Void> tryConnect(NuProcess process, int count) {
    NetClientOptions options = new NetClientOptions().setConnectTimeout(MILLISECONDS);
    NetClient c = vertx.createNetClient(options);
    logger.info("tryConnect() host {} port {} count {}", host, port, count);
    Promise<Void> promise = Promise.promise();
    c.connect(port, host, res -> {
      if (res.succeeded()) {
        logger.info("Connected to service at host {} port {} count {}", host, port, count);
        c.close().onComplete(x -> promise.complete());
      } else if (count < maxIterations && (process == null || process.isRunning())) {
        c.close().onComplete(x -> vertx.setTimer((long) (count + 1) * MILLISECONDS,
            id -> tryConnect(process, count + 1).onComplete(promise)));
      } else {
        c.close().onComplete(x ->
            promise.fail(messages.getMessage("11501",
                Integer.toString(port), res.cause().getMessage()))
        );
      }
    });
    return promise.future();
  }

  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Wait for process and server to be listening.
   * @param process Process to monitor
   * @return async result
   */
  public Future<Void> waitReady(NuProcess process) {
    if (port == 0) {
      return Future.succeededFuture();
    }
    if (process != null && !process.isRunning()) {
      process = null;
    }
    return tryConnect(process, 0);
  }
}
