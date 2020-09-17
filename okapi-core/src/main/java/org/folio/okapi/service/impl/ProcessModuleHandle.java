package org.folio.okapi.service.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.ModuleHandle;
import org.folio.okapi.util.TcpPortWaiting;

@java.lang.SuppressWarnings({"squid:S1192"})
public class ProcessModuleHandle implements ModuleHandle {

  private static final Logger logger = OkapiLogger.get();

  private final Vertx vertx;
  private final String exec;
  private final String id;
  private final String cmdlineStart;
  private final String cmdlineStop;
  private final EnvEntry[] env;
  private final Messages messages = Messages.getInstance();

  private Process process;
  private final int port;
  private final Ports ports;
  final TcpPortWaiting tcpPortWaiting;

  /**
   * Construct process module handler.
   * @param vertx Vert.x handle
   * @param desc launch descriptor
   * @param id if of process (possibly module ID)
   * @param ports ports handle
   * @param port listening port for module
   */
  public ProcessModuleHandle(Vertx vertx, LaunchDescriptor desc, String id,
                             Ports ports, int port) {
    this.vertx = vertx;
    this.id = id;
    this.exec = desc.getExec();
    this.cmdlineStart = desc.getCmdlineStart();
    this.cmdlineStop = desc.getCmdlineStop();
    this.env = desc.getEnv();
    this.port = port;
    this.ports = ports;
    this.process = null;
    this.tcpPortWaiting = new TcpPortWaiting(vertx, "localhost", port);
    if (desc.getWaitIterations() != null) {
      tcpPortWaiting.setMaxIterations(desc.getWaitIterations());
    }
    JsonObject config = vertx.getOrCreateContext().config();
    Integer maxIterations = config.getInteger("deploy.waitIterations");
    if (maxIterations != null) {
      tcpPortWaiting.setMaxIterations(maxIterations);
    }
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
          startFuture.handle(Future.failedFuture(
              messages.getMessage("11502", Integer.toString(port))));
        } else {
          start2(startFuture);
        }
      });
    } else {
      start2(startFuture);
    }
  }

  private static Process launch(Vertx vertx, String id, EnvEntry[] env,
                                String [] command) throws IOException {

    ProcessBuilder pb = new ProcessBuilder(command);
    if (env != null) {
      Map<String, String> penv = pb.environment();
      for (EnvEntry nv : env) {
        penv.put(nv.getName(), nv.getValue());
      }
    }
    Process process = pb.start();
    captureStream(vertx, id, process.getInputStream());
    captureStream(vertx, id, process.getErrorStream());
    return process;
  }

  private static void captureStream(Vertx vertx, String id, InputStream input) {
    vertx.executeBlocking(res -> {
      BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      String line = null;
      try {
        while ((line = reader.readLine()) != null) {
          logger.info("{} {}", id, line);
        }
      } catch (IOException e) {
        logger.error("{}", e.getMessage(), e);
      }
    });
  }

  @SuppressWarnings("indentation")
  private void start2(Handler<AsyncResult<Void>> startFuture) {
    vertx.executeBlocking(future -> {
      if (process == null) {
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
          process = launch(vertx, id, env, l);
          process.waitFor(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
          logger.warn("when starting {}", c, ex);
          Thread.currentThread().interrupt();
        } catch (IOException ex) {
          logger.warn("when starting {}", c, ex);
          future.fail(ex);
          return;
        }
        if (!process.isAlive() && process.exitValue() != 0) {
          future.handle(Future.failedFuture(messages.getMessage("11500", process.exitValue())));
          return;
        }
      }
      future.complete();
    }, false, result -> {
      if (result.failed()) {
        logger.debug("ProcessModuleHandle.start2() executeBlocking failed {}",
            result.cause().getMessage());
        startFuture.handle(Future.failedFuture(result.cause()));
      } else {
        start3(startFuture);
      }
    });
  }

  private void start3(Handler<AsyncResult<Void>> startFuture) {
    tcpPortWaiting.waitReady(process, x -> {
      if (x.failed()) {
        this.stopProcess(y -> startFuture.handle(Future.failedFuture(x.cause())));
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
            stopFuture.handle(Future.failedFuture(
                messages.getMessage("11503", Integer.toString(port))));
          }
        } else {
          stopFuture.handle(Future.succeededFuture());
        }
      });
    } else {
      stopFuture.handle(Future.succeededFuture());
    }
  }

  @SuppressWarnings("indentation")
  @Override
  public void stop(Handler<AsyncResult<Void>> stopFuture) {
    if (process == null) {
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
          Process pp = launch(vertx, id, env, l);
          logger.debug("Waiting for the port to be closed");
          pp.waitFor(30, TimeUnit.SECONDS); // 10 seconds for Dockers to stop
          logger.debug("Wait done");
          if (!pp.isAlive() && pp.exitValue() != 0) {
            future.handle(Future.failedFuture(messages.getMessage("11500", pp.exitValue())));
            return;
          }
        } catch (IOException ex) {
          logger.warn("when invoking {}", c, ex);
          future.fail(ex);
          return;
        } catch (InterruptedException ex) {
          logger.warn("when invoking {}", c, ex);
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

  @SuppressWarnings("indentation")
  private void stopProcess(Handler<AsyncResult<Void>> stopFuture) {
    vertx.executeBlocking(future -> {
      process.destroy();
      while (process.isAlive()) {
        boolean exited = true;
        try {
          process.exitValue();
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
          process.waitFor();
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
