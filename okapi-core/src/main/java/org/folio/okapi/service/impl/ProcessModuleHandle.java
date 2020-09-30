package org.folio.okapi.service.impl;

import com.zaxxer.nuprocess.NuAbstractProcessHandler;
import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessBuilder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
public class ProcessModuleHandle extends NuAbstractProcessHandler implements ModuleHandle {

  private static final Logger logger = OkapiLogger.get();

  private final Vertx vertx;
  private final String exec;
  private final String id;
  private final String cmdlineStart;
  private final String cmdlineStop;
  private final EnvEntry[] env;
  private final Messages messages = Messages.getInstance();

  private NuProcess process;
  private int exitCode;
  private final int port;
  private final Ports ports;
  final TcpPortWaiting tcpPortWaiting;

  /**
   * Construct process module handler.
   * @param vertx Vert.x handle
   * @param desc launch descriptor
   * @param id process identifier used for logging (possibly module ID)
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
  public Future<Void> start() {
    if (port == 0) {
      return start2();
    }
    // fail if port is already in use
    NetClientOptions options = new NetClientOptions().setConnectTimeout(200);
    NetClient c = vertx.createNetClient(options);
    return c.connect(port, "localhost").compose(socket -> {
      socket.close();
      return Future.failedFuture(messages.getMessage("11502", Integer.toString(port)));
    }, fail -> start2());
  }

  @Override
  public void onStdout(ByteBuffer buffer, boolean closed) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    String s = new String(bytes, StandardCharsets.UTF_8);
    int prev = 0;
    // one log per line
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '\n') {
        // omit \n in output
        logger.info("{} {}", id, s.substring(prev, i));
        prev = i + 1;
      }
    }
  }

  @Override
  public void onStderr(ByteBuffer buffer, boolean closed) {
    onStdout(buffer, closed);
  }

  @Override
  public void onExit(int code) {
    exitCode = code;
  }

  private NuProcess launch(Vertx vertx, String id, EnvEntry[] env,
                           String [] command) {

    NuProcessBuilder pb = new NuProcessBuilder(command);
    if (env != null) {
      for (EnvEntry nv : env) {
        pb.environment().put(nv.getName(), nv.getValue());
      }
    }
    exitCode = 0;
    pb.setProcessListener(this);
    NuProcess process = pb.start();
    return process;
  }

  @SuppressWarnings("indentation")
  private Future<Void> start2() {
    return vertx.executeBlocking(future -> {
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
        }
        if (!process.isRunning() && exitCode != 0) {
          if (exitCode == Integer.MIN_VALUE) {
            future.handle(Future.failedFuture(messages.getMessage("11504", c)));
          } else {
            future.handle(Future.failedFuture(messages.getMessage("11500", exitCode)));
          }
          return;
        }
      }
      future.complete();
    }, false).compose(x -> start3());
  }

  private Future<Void> start3() {
    return tcpPortWaiting.waitReady(process).onFailure(x -> stopProcess());
  }

  private Future<Void> waitPortToClose(int iter) {
    if (port == 0) {
      return Future.succeededFuture();
    }
    NetClientOptions options = new NetClientOptions().setConnectTimeout(50);
    NetClient c = vertx.createNetClient(options);
    return c.connect(port, "localhost").compose(socket -> {
      socket.close();
      if (iter > 0) {
        Promise<Void> promise = Promise.promise();
        vertx.setTimer(100, id -> waitPortToClose(iter - 1).onComplete(promise::handle));
        return promise.future();
      } else {
        return Future.failedFuture(messages.getMessage("11503", Integer.toString(port)));
      }
    }, fail -> Future.succeededFuture());
  }

  @SuppressWarnings("indentation")
  @Override
  public Future<Void> stop() {
    if (process == null) {
      ports.free(port);
      return Future.succeededFuture();
    }
    if (cmdlineStop == null) {
      return stopProcess();
    }
    return vertx.executeBlocking(promise -> {
      String c = "";
      try {
        c = cmdlineStop.replace("%p", Integer.toString(port));
        String[] l = new String[]{"sh", "-c", c};
        NuProcess pp = launch(vertx, id, env, l);
        logger.debug("Waiting for the port to be closed");
        pp.waitFor(30, TimeUnit.SECONDS); // 10 seconds for Dockers to stop
        logger.debug("Wait done");
        if (!pp.isRunning() && exitCode != 0) {
          if (exitCode == Integer.MIN_VALUE) {
            promise.handle(Future.failedFuture(messages.getMessage("11504", c)));
          } else {
            promise.handle(Future.failedFuture(messages.getMessage("11500", exitCode)));
          }
          return;
        }
      } catch (InterruptedException ex) {
        logger.warn("when invoking {}", c, ex);
        promise.fail(ex);
        Thread.currentThread().interrupt();
        return;
      }
      promise.complete();
    }, false).compose(x -> {
      ports.free(port);
      return waitPortToClose(10);
    });
  }

  @SuppressWarnings("indentation")
  private Future<Void> stopProcess() {
    return vertx.executeBlocking(promise -> {
      process.destroy(true);
      while (process.isRunning()) {
        boolean exited = true;
        try {
          process.waitFor(0, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
      promise.complete();
    }, false).compose(res -> {
      ports.free(port);
      return waitPortToClose(10);
    });
  }
}
