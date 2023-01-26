package org.folio.okapi.util;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import java.util.HashMap;
import java.util.Map;
import org.folio.okapi.common.Messages;

/**
 * Use {@link NetClient} to check a port on localhost.
 *
 * <p>See also {@link TcpPortWaiting} that uses {@code WebClient}.
 */
public class PortChecker {
  private static final NetClientOptions NET_CLIENT_OPTIONS =
      new NetClientOptions().setConnectTimeout(200);
  private static final Map<Vertx, NetClient> NET_CLIENTS = new HashMap<>();

  /**
   * Wait until localhost:port is closed.
   *
   * @param port the port number at localhost
   * @param iter maximum number of attempts, pause for 100 ms between attempts, if reached fail
   */
  public static Future<Void> waitPortToClose(Vertx vertx, int port, int iter) {
    if (iter <= 0) {
      return Future.failedFuture(
          Messages.getInstance().getMessage("11502", Integer.toString(port)));
    }
    return Future.succeededFuture()
        .compose(x -> getNetClient(vertx).connect(port, "localhost"))
        .compose(
            socket ->
                socket.close()
                .otherwiseEmpty()
                .compose(x -> {
                  Promise<Void> p = Promise.promise();
                  vertx.setTimer(100, y -> waitPortToClose(vertx, port, iter - 1).onComplete(p));
                  return p.future();
                }),
            noSocket -> Future.succeededFuture());
  }

  private static NetClient getNetClient(Vertx vertx) {
    return NET_CLIENTS.computeIfAbsent(vertx, x -> vertx.createNetClient(NET_CLIENT_OPTIONS));
  }

}
