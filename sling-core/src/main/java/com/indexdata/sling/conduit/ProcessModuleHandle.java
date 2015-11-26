/*
 * Copyright (c) 1995-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.io.IOException;

public class ProcessModuleHandle implements ModuleHandle {

  private final Vertx vertx;
  private final ProcessDeploymentDescriptor desc;
  private Process p;
  private final int port;

  public ProcessModuleHandle(Vertx vertx, ProcessDeploymentDescriptor desc,
          int port) {
    this.vertx = vertx;
    this.desc = desc;
    this.port = port;
  }

  @Override
  public void start(Handler<AsyncResult<Void>> startFuture) {
    final String cmdline = desc.getCmdlineStart().replace("%p", Integer.toString(port));
    vertx.executeBlocking(future -> {
      if (p == null) {
        try {
          p = Runtime.getRuntime().exec(cmdline);
        } catch (IOException ex) {
          future.fail(ex);
          return;
        }
      }
      future.complete();
    }, false, result -> {
      if (result.failed()) {
        startFuture.handle(Future.failedFuture(result.cause()));
      } else {
        startFuture.handle(Future.succeededFuture());
      }
    });
  }

  @Override
  public void stop(Handler<AsyncResult<Void>> stopFuture) {
    if (p != null) {
      p.destroy();
    }
    stopFuture.handle(Future.succeededFuture());
  }
  public int getPort() {
    return port;
  }
}
