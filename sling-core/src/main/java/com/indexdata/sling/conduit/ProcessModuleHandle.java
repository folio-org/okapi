/*
 * Copyright (c) 1995-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.io.IOException;

public class ProcessModuleHandle implements ModuleHandle {

  private final Vertx vertx;
  private final ProcessDeploymentDescriptor desc;
  private Process p;

  public ProcessModuleHandle(Vertx vertx, ProcessDeploymentDescriptor desc) {
    this.vertx = vertx;
    this.desc = desc;
  }

  @Override
  public void start(Handler<AsyncResult<Void>> startFuture) {
    System.err.println("ProcessModuleHandle:start " + desc.getCmdlineStart());
    if (true) {
      vertx.executeBlocking(future -> {
        if (p == null) {
          try {
            p = Runtime.getRuntime().exec(desc.getCmdlineStart());
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
    } else {
      if (p == null) {
        try {
          p = Runtime.getRuntime().exec(desc.getCmdlineStart());
        } catch (IOException ex) {
          startFuture.handle(Future.failedFuture(ex));
          return;
        }
      }
      startFuture.handle(Future.succeededFuture());
    }
  }

  @Override
  public void stop(Handler<AsyncResult<Void>> stopFuture) {
    System.err.println("ProcessModuleHandle:stop");
    if (p != null) {
      p.destroy();
    }
    stopFuture.handle(Future.succeededFuture());
  }

}
