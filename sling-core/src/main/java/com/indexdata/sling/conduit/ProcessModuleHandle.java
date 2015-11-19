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

  private Vertx vertx;
  private final ProcessDeploymentDescriptor desc;
  private Process p;

  public ProcessModuleHandle(ProcessDeploymentDescriptor desc) {
    this.desc = desc;
  }

  @Override
  public void start(Handler<AsyncResult<Void>> startFuture) {
    if (true) {
      System.err.println("ProcessModuleHandle:start async");

      vertx.executeBlocking(future -> {
        if (p == null) {
          try {
            p = Runtime.getRuntime().exec(desc.cmdline_start);
          } catch (IOException ex) {
            future.fail(ex);
            return;
          }
        }
        future.complete();
      }, false, result -> {
        if (result.failed()) {
          System.out.println("cb: failed");
          startFuture.handle(Future.failedFuture(result.cause()));
        } else {
          System.out.println("cb: success");
          startFuture.handle(Future.succeededFuture());
        }
      });
    } else {
      System.err.println("ProcessModuleHandle:start");
      if (p == null) {
        try {
          p = Runtime.getRuntime().exec(desc.cmdline_start);
        } catch (IOException ex) {
          startFuture.handle(Future.failedFuture(ex));
          return;
        }
      }
      startFuture.handle(Future.succeededFuture());

    // TODO: perhaps throw error if it's already started?
      // POST /init  (content is JSON struct with "url":"http://host:port"
      // GET /routes (response is JSON struct with routes definitions)
      // startFuture.complete((Void) new Object());
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

  @Override
  public void init(Vertx vertx) {
    this.vertx = vertx;
  }
}
