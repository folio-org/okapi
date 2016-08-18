/*
 * Copyright (C) 2015-2016 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.util;

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
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import okapi.bean.Ports;
import okapi.bean.LaunchDescriptor;

public class ProcessModuleHandle implements ModuleHandle {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  private final Vertx vertx;
  private final LaunchDescriptor desc;
  private Process p;
  private final int port;
  private final Ports ports;
  private static final int MAX_ITERATIONS = 30; // x*(x+1) * 0.1 seconds..
  private static final long MILLISECONDS = 200;

  public ProcessModuleHandle(Vertx vertx, LaunchDescriptor desc,
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

        InputStream inputStream = p.getErrorStream();
        startFuture.handle(Future.failedFuture("Service returned with exit code"
                + " " + p.exitValue() + ". Standard error:\n"
                + OkapiStream.toString(inputStream)));
      } else if (count < MAX_ITERATIONS) {
        vertx.setTimer((count + 1) * MILLISECONDS, id -> tryConnect(startFuture, count + 1));
      } else {
        logger.error("Failed to connect to service at port " + port + " : " + res.cause().getMessage());
        startFuture.handle(Future.failedFuture(res.cause()));
      }
    });
  }

  @Override
  public void start(Handler<AsyncResult<Void>> startFuture) {
    if (desc == null ) {
      startFuture.handle(Future.failedFuture("No launchDescriptor"));
      return;
    }
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
          pb.redirectInput(Redirect.INHERIT)
                  .redirectOutput(Redirect.INHERIT);
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
