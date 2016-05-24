/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import okapi.util.ModuleHandle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import java.io.IOException;
import okapi.bean.Ports;
import okapi.bean.ProcessDeploymentDescriptor;

public class ProcessModuleHandle implements ModuleHandle {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  private final Vertx vertx;
  private final ProcessDeploymentDescriptor desc;
  private Process p;
  private final int port;
  private final Ports ports;
  private static final int max_iterations = 30; // x*(x+1) * 0.1 seconds..

  public ProcessModuleHandle(Vertx vertx, ProcessDeploymentDescriptor desc,
          Ports ports, int port) {
    this.vertx = vertx;
    this.desc = desc;
    this.port = port;
    this.ports = ports;
    this.p = null;
  }

  private void tryConnect(Handler<AsyncResult<Void>> startFuture, int count) {
    NetClientOptions options = new NetClientOptions().setConnectTimeout(200);
    NetClient c = vertx.createNetClient(options);
    c.connect(port, "localhost", res -> {
      if (res.succeeded()) {
        logger.info("Connected to service at port " + port + " count " + count);
        NetSocket socket = res.result();
        socket.close();
        startFuture.handle(Future.succeededFuture());
      } else if (!p.isAlive() && p.exitValue() != 0) {
        startFuture.handle(Future.failedFuture("Exit failure for service"));
      } else if (count < max_iterations) {
        vertx.setTimer((count + 1) * 200, id -> {
          tryConnect(startFuture, count + 1);
        });
      } else {
        logger.error("Failed to connect to service at port " + port + " : " + res.cause().getMessage());
        startFuture.handle(Future.failedFuture(res.cause()));
      }
    });
  }

  @Override
  public void start(Handler<AsyncResult<Void>> startFuture) {
    if (port > 0) {
      // fail if port is already in use
      NetClientOptions options = new NetClientOptions().setConnectTimeout(200);
      NetClient c = vertx.createNetClient(options);
      c.connect(port, "localhost", res -> {
        if (res.succeeded()) {
          NetSocket socket = res.result();
          socket.close();
          logger.error("Failed to start service on port " + port + " : already in use");
          startFuture.handle(Future.failedFuture("port " + port + " already in use"));
        } else {
          start2(startFuture);
        }
      });
    } else {
      start2(startFuture);
    }
  }

  private void start2(Handler<AsyncResult<Void>> startFuture) {
    final String exec = desc.getExec();
    final String cmdlineStart = desc.getCmdlineStart();
    vertx.executeBlocking(future -> {
      if (p == null) {
        try {
          String[] l = new String[0];
          if (exec != null) {
            String c = exec.replace("%p", Integer.toString(port));
            l = c.split(" ");
          } else if (cmdlineStart != null) {
            String c = cmdlineStart.replace("%p", Integer.toString(port));
            l = new String[]{"sh", "-c", c};
          }
          ProcessBuilder pb = new ProcessBuilder(l);
          pb.inheritIO();
          p = pb.start();
        } catch (IOException ex) {
          future.fail(ex);
          return;
        }
      }
      future.complete();
    }, false, result -> {
      if (result.failed()) {
        startFuture.handle(Future.failedFuture(result.cause()));
      } else if (port > 0) {
        tryConnect(startFuture, 0);
      } else {
        startFuture.handle(Future.succeededFuture());
      }
    });
  }

  @Override
  public void stop(Handler<AsyncResult<Void>> stopFuture) {
    if (p == null) {
      ports.free(port);
      stopFuture.handle(Future.succeededFuture());
      return;
    }
    final String cmdline = desc.getCmdlineStop();
    if (cmdline == null) {
      vertx.executeBlocking(future -> {
        p.destroy();
        while (p.isAlive()) {
          boolean exited = true;
          try {
            int r = p.exitValue();
          } catch (Exception e) {
            exited = false;
          }
          if (exited) {
            future.fail("Process exited but child processes exist");
            return;
          }
          try {
            int x = p.waitFor();
          } catch (InterruptedException ex) {
          }
        }
        future.complete();
      }, false, result -> {
        if (result.failed()) {
          stopFuture.handle(Future.failedFuture(result.cause()));
        } else {
          ports.free(port);
          stopFuture.handle(Future.succeededFuture());
        }
      });
    } else {
      vertx.executeBlocking(future -> {
        try {
          String c = cmdline.replace("%p", Integer.toString(port));
          String[] l = new String[]{"sh", "-c", c};
          ProcessBuilder pb = new ProcessBuilder(l);
          pb.inheritIO();
          pb.start();
        } catch (IOException ex) {
          future.fail(ex);
          return;
        }
        future.complete();
      }, false, result -> {
        if (result.failed()) {
          stopFuture.handle(Future.failedFuture(result.cause()));
        } else {
          ports.free(port);
          stopFuture.handle(Future.succeededFuture());
        }
      });
    }
  }
}
