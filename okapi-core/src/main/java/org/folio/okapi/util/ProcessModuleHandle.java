package org.folio.okapi.util;

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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.EnvEntry;

public class ProcessModuleHandle implements ModuleHandle {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  private final Vertx vertx;
  final String exec;
  final String cmdlineStart;
  final String cmdlineStop;
  final EnvEntry[] env;

  private Process p;
  private final int port;
  private final Ports ports;
  private static final int MAX_ITERATIONS = 30; // x*(x+1) * 0.1 seconds.
  private static final long MILLISECONDS = 200;

  public ProcessModuleHandle(Vertx vertx, LaunchDescriptor desc,
          Ports ports, int port) {
    this.vertx = vertx;

    this.exec = desc.getExec();
    this.cmdlineStart = desc.getCmdlineStart();
    this.cmdlineStop = desc.getCmdlineStop();
    this.env = desc.getEnv();
    this.port = port;
    this.ports = ports;
    this.p = null;
  }

  private ProcessBuilder createProcessBuilder(String[] l) {
    ProcessBuilder pb = new ProcessBuilder(l);
    if (env != null) {
      Map<String, String> penv = pb.environment();
      for (EnvEntry nv : env) {
        penv.put(nv.getName(), nv.getValue());
      }
    }
    return pb;
  }

  private void serviceFailed(Handler<AsyncResult<Void>> startFuture, int exitValue) {
    logger.warn("Service returned with exit code " + p.exitValue());
    startFuture.handle(Future.failedFuture("Service returned with exit code "
      + exitValue));
  }

  private void tryConnect(Handler<AsyncResult<Void>> startFuture, int count) {
    NetClientOptions options = new NetClientOptions().setConnectTimeout(200);
    NetClient c = vertx.createNetClient(options);
    c.connect(port, "localhost", res -> {
      if (res.succeeded()) {
        logger.info("Connected to service at port " + port + " count " + count);
        NetSocket socket = res.result();
        socket.close();
        try {
          p.getErrorStream().close();
        } catch (Exception e) {
          logger.error("Closing streams failed: " + e);
        }
        startFuture.handle(Future.succeededFuture());
      } else if (!p.isAlive() && p.exitValue() != 0) {
        logger.warn("Service returned with exit code " + p.exitValue());
        startFuture.handle(Future.failedFuture("Service returned with exit code "
          + p.exitValue()));
      } else if (count < MAX_ITERATIONS) {
        vertx.setTimer((count + 1) * MILLISECONDS, id -> tryConnect(startFuture, count + 1));
      } else {
        logger.error("Failed to connect to service at port " + port + " : " + res.cause().getMessage());
        startFuture.handle(Future.failedFuture("Deployment failed. "
          + "Could not connect to port " + port + ": " + res.cause().getMessage()));
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
    vertx.executeBlocking(future -> {
      if (p == null) {
        try {
          String[] l = new String[0];
          if (exec != null) {
            if (!exec.contains("%p")) {
              future.fail("Can not deploy: No %p in the exec line");
              return;
            }
            String c = exec.replace("%p", Integer.toString(port));
            l = c.split(" ");
          } else if (cmdlineStart != null) {
            if (!cmdlineStart.contains("%p")) {
              future.fail("Can not deploy: No %p in the cmdlineStart");
              return;
            }
            String c = cmdlineStart.replace("%p", Integer.toString(port));
            l = new String[]{"sh", "-c", c};
          } else {
            future.fail("Can not deploy: No exec, no CmdlineStart in LaunchDescriptor");
            return;
          }
          ProcessBuilder pb = createProcessBuilder(l);
          pb.inheritIO();
          p = pb.start();
        } catch (IOException ex) {
          logger.warn("Deployment failed: " + ex.getMessage());
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

  private void waitPortToClose(Handler<AsyncResult<Void>> stopFuture, int iter) {
    if (port > 0) {
      // fail if port is already in use
      NetClientOptions options = new NetClientOptions().setConnectTimeout(50);
      NetClient c = vertx.createNetClient(options);
      c.connect(port, "localhost", res -> {
        if (res.succeeded()) {
          NetSocket socket = res.result();
          socket.close();
          if (iter > 0) {
            vertx.setTimer(100, x -> {
              waitPortToClose(stopFuture, iter - 1);
            });
          } else {
            logger.error("port " + port + " not shut down");
            stopFuture.handle(Future.failedFuture("port " + port + " not shut down"));
          }
        } else {
          stopFuture.handle(Future.succeededFuture());
        }
      });
    } else {
      stopFuture.handle(Future.succeededFuture());
    }
  }

  @Override
  public void stop(Handler<AsyncResult<Void>> stopFuture) {
    if (p == null) {
      ports.free(port);
      stopFuture.handle(Future.succeededFuture());
      return;
    }
    if (cmdlineStop == null) {
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
          waitPortToClose(stopFuture, 10);
        }
      });
    } else {
      vertx.executeBlocking(future -> {
        try {
          String c = cmdlineStop.replace("%p", Integer.toString(port));
          String[] l = new String[]{"sh", "-c", c};
          ProcessBuilder pb = createProcessBuilder(l);
          pb.inheritIO();
          Process start = pb.start();
          logger.debug("Waiting for the port to be closed");
          start.waitFor(30, TimeUnit.SECONDS); // 10 seconds for Dockers to stop
          logger.debug("Wait done");
        } catch (IOException | InterruptedException ex) {
          logger.debug("Caught exception " + ex);
          future.fail(ex);
          return;
        }
        future.complete();
      }, false, result -> {
        if (result.failed()) {
          stopFuture.handle(Future.failedFuture(result.cause()));
        } else {
          ports.free(port);
          waitPortToClose(stopFuture, 10);
        }
      });
    }
  }
}
