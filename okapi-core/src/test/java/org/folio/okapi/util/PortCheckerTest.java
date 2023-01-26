package org.folio.okapi.util;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.atomic.AtomicReference;
import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class PortCheckerTest {

  @Test
  void utilityClass() {
    UtilityClassTester.assertUtilityClass(PortChecker.class);
  }

  @Test
  void openPort(Vertx vertx, VertxTestContext vtc) {
    Checkpoint connect = vtc.checkpoint(2);
    Checkpoint failure = vtc.checkpoint();
    vertx.createNetServer()
    .connectHandler(x -> connect.flag())
    .listen(0)
    .compose(netServer -> PortChecker.waitPortToClose(vertx, netServer.actualPort(), 2))
    .onFailure(x -> failure.flag());
  }

  Future<NetServer> getClosingNetServer(Vertx vertx) {
    AtomicReference<NetServer> netServer = new AtomicReference<>();
    return vertx.createNetServer()
        .connectHandler(socket -> netServer.get().close())
        .listen(0)
        .onSuccess(netServer::set);
  }

  @Test
  void closingPort(Vertx vertx, VertxTestContext vtc) {
    getClosingNetServer(vertx)
    .compose(netServer -> PortChecker.waitPortToClose(vertx, netServer.actualPort(), 2))
    .onComplete(vtc.succeedingThenComplete());
  }

  @Test
  void closingPortTooLate(Vertx vertx, VertxTestContext vtc) {
    getClosingNetServer(vertx)
    .compose(netServer -> PortChecker.waitPortToClose(vertx, netServer.actualPort(), 1))
    .onComplete(vtc.failingThenComplete());
  }

  @Test
  void parallel(Vertx vertx, VertxTestContext vtc) {
    Checkpoint c1 = vtc.checkpoint();
    Checkpoint c2 = vtc.checkpoint();
    Checkpoint c3 = vtc.checkpoint();
    Checkpoint c4 = vtc.checkpoint();
    getClosingNetServer(vertx).compose(netServer1 ->
    getClosingNetServer(vertx).compose(netServer2 ->
    getClosingNetServer(vertx).compose(netServer3 ->
    getClosingNetServer(vertx).compose(netServer4 -> {
      PortChecker.waitPortToClose(vertx, netServer1.actualPort(), 2).onSuccess(x -> c1.flag());
      PortChecker.waitPortToClose(vertx, netServer2.actualPort(), 2).onSuccess(x -> c4.flag());
      PortChecker.waitPortToClose(vertx, netServer3.actualPort(), 2).onSuccess(x -> c3.flag());
      PortChecker.waitPortToClose(vertx, netServer4.actualPort(), 2).onSuccess(x -> c2.flag());
      return Future.succeededFuture();
    }))));
  }

}
