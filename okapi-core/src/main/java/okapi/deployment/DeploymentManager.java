/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.deployment;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.LinkedHashMap;
import okapi.bean.DeploymentDescriptor;
import okapi.bean.ModuleHandle;
import okapi.bean.Ports;
import okapi.bean.ProcessModuleHandle;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

public class DeploymentManager {
  LinkedHashMap<String, DeploymentDescriptor> list = new LinkedHashMap<>();
  Vertx vertx;
  Ports ports;

  public DeploymentManager(Vertx vertx, int port_start, int port_end) {
    this.vertx = vertx;
    this.ports = new Ports(port_start, port_end);
  }

  public void deploy(DeploymentDescriptor md1, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    String id = md1.getId();
    if (list.containsKey(id)) {
      fut.handle(new Failure<>(USER, "already deployed: " + id));
      return;
    }
    int use_port = ports.get();
    if (use_port == -1) {
      fut.handle(new Failure<>(INTERNAL, "all ports in use"));
      return;
    }
    String url = "http://localhost:" + use_port;
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, md1.getDescriptor(), ports, use_port);
    ModuleHandle mh = pmh;
    mh.start(future -> {
      if (future.succeeded()) {
        DeploymentDescriptor md2 = new DeploymentDescriptor(id, url,
                md1.getDescriptor(), mh);
        list.put(id, md2);
        fut.handle(new Success<>(md2));
      } else {
        ports.free(use_port);
        fut.handle(new Failure<>(INTERNAL, future.cause()));
      }
    });
  }

  public void undeploy(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!list.containsKey(id)) {
      fut.handle(new Failure<>(NOT_FOUND, "not found: " + id));
    } else {
      DeploymentDescriptor md = list.get(id);
      ModuleHandle mh = md.getModuleHandle();
      mh.stop(future -> {
        if (future.succeeded()) {
          fut.handle(new Success<>());
          list.remove(id);
        } else {
          fut.handle(new Failure<>(INTERNAL, future.cause()));
        }
      });
    }
  }
}
