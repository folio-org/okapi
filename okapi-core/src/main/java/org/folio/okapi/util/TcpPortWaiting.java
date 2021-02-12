package org.folio.okapi.util;

import com.zaxxer.nuprocess.NuProcess;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
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
    logger.info("tryConnect() host {} port {} count {}", host, port, count);
    return tryConnect()
        .onSuccess(res -> {
          logger.info("Connected to service at host {} port {} count {}", host, port, count);
        })
        .recover(cause -> {
          if (count < maxIterations && (process == null || process.isRunning())) {
            return Future.future(promise ->
                vertx.setTimer((long) (count + 1) * MILLISECONDS,
                    id -> tryConnect(process, count + 1).onComplete(promise)));
          } else {
            return Future.failedFuture(messages.getMessage("11501",
                Integer.toString(port), cause.getMessage()));
          }
        });
  }

  private Future<Void> tryConnect() {
    // don't use NetClient because container ports are immediately ready, instead check for HTTP
    WebClientOptions options = new WebClientOptions().setConnectTimeout(MILLISECONDS);
    WebClient c = WebClient.create(vertx, options);
    return c.get(port, host, "/")
        .send()
        .<Void>mapEmpty()
        .onComplete(result -> c.close());
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
