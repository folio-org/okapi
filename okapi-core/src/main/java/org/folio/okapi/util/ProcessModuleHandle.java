package org.folio.okapi.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;

@java.lang.SuppressWarnings({"squid:S1192"})
public class ProcessModuleHandle implements ModuleHandle {

  private final Logger logger = OkapiLogger.get();

  private final Vertx vertx;
  private final String exec;
  private final String cmdlineStart;
  private final String cmdlineStop;
  private final EnvEntry[] env;
  private Messages messages = Messages.getInstance();

  private Process p;
  private final int port;
  private final Ports ports;
  private int maxIterations = 30; // x*(x+1) * 0.1 seconds.
  private static final int MILLISECONDS = 200;

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

  public void setConnectIterMax(int iterations) {
    this.maxIterations = iterations;
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

  private void tryConnect(Handler<AsyncResult<Void>> startFuture, int count) {
    NetClientOptions options = new NetClientOptions().setConnectTimeout(MILLISECONDS);
    NetClient c = vertx.createNetClient(options);
    logger.debug("ProcessModuleHandle.tryConnect() port " + port + " count " + count);
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
        startFuture.handle(Future.failedFuture(messages.getMessage("11500", p.exitValue())));
      } else if (count < maxIterations) {
        vertx.setTimer((long) (count + 1) * MILLISECONDS,
          id -> tryConnect(startFuture, count + 1));
      } else {
        startFuture.handle(Future.failedFuture(messages.getMessage("11501",
          Integer.toString(port), res.cause().getMessage())));
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
          startFuture.handle(Future.failedFuture(messages.getMessage("11502", port)));
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
        String c = "";
        try {
          String[] l;
          if (exec != null) {
            if (!exec.contains("%p")) {
              future.fail("Can not deploy: No %p in the exec line");
              return;
            }
            c = exec.replace("%p", Integer.toString(port));
            l = c.split(" ");
          } else if (cmdlineStart != null) {
            if (!cmdlineStart.contains("%p")) {
              future.fail("Can not deploy: No %p in the cmdlineStart");
              return;
            }
            c = cmdlineStart.replace("%p", Integer.toString(port));
            l = new String[]{"sh", "-c", c};
          } else {
            future.fail("Can not deploy: No exec, no CmdlineStart in LaunchDescriptor");
            return;
          }
          ProcessBuilder pb = createProcessBuilder(l);
          pb.inheritIO();
          p = pb.start();
          p.waitFor(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
          logger.warn("Caught InterruptedException " + ex + " when starting " + c);
          Thread.currentThread().interrupt();
        } catch (IOException ex) {
          logger.warn("Caught IOException ", ex + " when starting " + c);
          future.fail(ex);
          return;
        }
        if (!p.isAlive() && p.exitValue() != 0) {
          future.handle(Future.failedFuture(messages.getMessage("11500", p.exitValue())));
          return;
        }
      }
      future.complete();
    }, false, result -> {
      if (result.failed()) {
        logger.debug("ProcessModuleHandle.start2() executeBlocking failed " + result.cause());
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
            vertx.setTimer(100, x -> waitPortToClose(stopFuture, iter - 1));
          } else {
            stopFuture.handle(Future.failedFuture(messages.getMessage("11503", port)));
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
      stopProcess(stopFuture);
    } else {
      vertx.executeBlocking(future -> {
        String c = "";
        try {
          c = cmdlineStop.replace("%p", Integer.toString(port));
          String[] l = new String[]{"sh", "-c", c};
          ProcessBuilder pb = createProcessBuilder(l);
          pb.inheritIO();
          Process pp = pb.start();
          logger.debug("Waiting for the port to be closed");
          pp.waitFor(30, TimeUnit.SECONDS); // 10 seconds for Dockers to stop
          logger.debug("Wait done");
          if (!pp.isAlive() && pp.exitValue() != 0) {
            future.handle(Future.failedFuture(messages.getMessage("11500", pp.exitValue())));
            return;
          }
        } catch (IOException ex) {
          logger.debug("Caught IOException " + ex + " when invoking " + c);
          future.fail(ex);
          return;
        } catch (InterruptedException ex) {
          logger.debug("Caught InterruptedException " + ex + " when invoking " + c);
          future.fail(ex);
          Thread.currentThread().interrupt();
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

  private void stopProcess(Handler<AsyncResult<Void>> stopFuture) {
    vertx.executeBlocking(future -> {
      p.destroy();
      while (p.isAlive()) {
        boolean exited = true;
        try {
          p.exitValue();
        } catch (IllegalThreadStateException e) {
          exited = false;
        } catch (Exception e) {
          logger.info(e);
          exited = false;
        }
        if (exited) {
          future.fail("Process exited but child processes exist");
          return;
        }
        try {
          p.waitFor();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
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
  }
}
