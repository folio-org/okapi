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
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.Config;
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
  private String commandLine; // actual command line string
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
   * @param config Vertx. config
   */
  public ProcessModuleHandle(Vertx vertx, LaunchDescriptor desc, String id,
                             Ports ports, int port, JsonObject config) {
    this.vertx = vertx;
    this.id = id;
    this.exec = desc.getExec();
    this.cmdlineStart = desc.getCmdlineStart();
    this.cmdlineStop = desc.getCmdlineStop();
    this.env = desc.getEnv();
    this.port = port;
    this.ports = ports;
    this.process = null;
    this.tcpPortWaiting = new TcpPortWaiting(vertx, id, "localhost", port);

    Integer maxIterations = Config.getSysConfInteger("deploy.waitIterations",
        desc.getWaitIterations(), config);
    if (maxIterations != null) {
      tcpPortWaiting.setMaxIterations(maxIterations);
    }
  }

  private Future<Void> waitPortOpen(NetClient c, int iter) {
    return c.connect(port, "localhost")
        .compose(
            socket -> socket.close()
                .compose(y -> {
                  if (iter == 0) {
                    return Future.failedFuture(
                        messages.getMessage("11502", Integer.toString(port)));
                  }
                  Promise<Void> promise = Promise.promise();
                  vertx.setTimer(100, x ->
                      waitPortOpen(c, iter - 1).onComplete(promise));
                  return promise.future();
                }),
            noSocket -> Future.succeededFuture());
  }

  @Override
  public Future<Void> start() {
    if (process != null) {
      return Future.failedFuture("already started " + commandLine);
    }
    if (port == 0) {
      return start2();
    }
    // fail if port is already in use
    NetClientOptions options = new NetClientOptions().setConnectTimeout(200);
    NetClient netClient = vertx.createNetClient(options);
    return waitPortOpen(netClient, 5)
        .onComplete(x -> netClient.close())
        .compose(x -> start2());
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

  private NuProcess launch(EnvEntry[] env, String [] command) {

    NuProcessBuilder pb = new NuProcessBuilder(command);
    if (env != null) {
      for (EnvEntry nv : env) {
        pb.environment().put(nv.getName(), nv.getValue());
      }
    }
    exitCode = 0;
    pb.setProcessListener(this);
    return pb.start();
  }

  private Future<Void> start2() {
    commandLine = "";
    String[] l;
    if (exec != null) {
      if (!exec.contains("%p")) {
        return Future.failedFuture("Can not deploy: No %p in the exec line");
      }
      commandLine = exec.replace("%p", Integer.toString(port));
      l = commandLine.split(" ");
    } else if (cmdlineStart != null) {
      if (!cmdlineStart.contains("%p")) {
        return Future.failedFuture("Can not deploy: No %p in the cmdlineStart");
      }
      commandLine = cmdlineStart.replace("%p", Integer.toString(port));
      l = new String[]{"sh", "-c", commandLine};
    } else {
      return Future.failedFuture("Can not deploy: No exec, no CmdlineStart in LaunchDescriptor");
    }
    final String commandLineF = commandLine;
    process = launch(env, l);
    Promise<Void> promise = Promise.promise();
    // time to wait for process status.. when a port is present (always in real life)..
    // The waitReady will check if process eventually starts listening on port
    vertx.setTimer(port == 0 ? 3000 : 1000, timerRes -> {
      if (process.isRunning() || exitCode == 0) {
        promise.complete();
        return;
      }
      if (exitCode == Integer.MIN_VALUE) {
        promise.fail(messages.getMessage("11504", id, commandLineF));
      } else {
        promise.fail(messages.getMessage("11500", id, Integer.toString(exitCode)));
      }
    });
    return promise.future()
        .compose(x -> tcpPortWaiting.waitReady(process).onFailure(y -> stopProcess()));
  }

  Future<Void> waitPortToClose(int iter) {
    if (port == 0) {
      return Future.succeededFuture();
    }
    NetClientOptions options = new NetClientOptions().setConnectTimeout(50);
    NetClient c = vertx.createNetClient(options);
    return c.connect(port, "localhost").compose(
        socket -> socket.close()
            .otherwiseEmpty().compose(x -> {
              if (iter == 0) {
                return Future.failedFuture(messages.getMessage("11503", Integer.toString(port)));
              }
              Promise<Void> promise = Promise.promise();
              vertx.setTimer(100, id -> waitPortToClose(iter - 1).onComplete(promise));
              return promise.future();
            }),
        noSocket -> Future.succeededFuture());
  }

  @Override
  public Future<Void> stop() {
    if (process == null) {
      ports.free(port);
      return Future.succeededFuture();
    }
    if (cmdlineStop == null) {
      return stopProcess();
    }
    String commandLine = cmdlineStop.replace("%p", Integer.toString(port));
    String[] l = new String[]{"sh", "-c", commandLine};
    NuProcess pp = launch(env, l);
    Promise<Void> promise = Promise.promise();
    // time to wait for process that shuts down service.. when a port is present (always in prod)
    // The waitPortClose will wait for service to shut down
    vertx.setTimer(port == 0 ? 3000 : 1000, timerRes -> {
      if (pp.isRunning() || exitCode == 0) {
        promise.complete();
        return;
      }
      if (exitCode == Integer.MIN_VALUE) {
        promise.handle(Future.failedFuture(messages.getMessage("11504", id, commandLine)));
      } else {
        promise.handle(Future.failedFuture(messages.getMessage("11500", id,
            Integer.toString(exitCode))));
      }
    });
    return promise.future().compose(x -> {
      ports.free(port);
      return waitPortToClose(10);
    });
  }

  private Future<Void> stopProcess() {
    process.destroy(true);
    process = null;
    ports.free(port);
    return waitPortToClose(10);
  }
}
