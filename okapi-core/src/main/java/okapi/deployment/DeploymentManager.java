/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.deployment;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import okapi.bean.DeploymentDescriptor;
import okapi.util.ModuleHandle;
import okapi.bean.Ports;
import okapi.bean.ProcessDeploymentDescriptor;
import okapi.util.ProcessModuleHandle;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

public class DeploymentManager {
  LinkedHashMap<String, DeploymentDescriptor> list = new LinkedHashMap<>();
  Vertx vertx;
  Ports ports;
  String host;

  public DeploymentManager(Vertx vertx, String host, Ports ports) {
    this.vertx = vertx;
    this.host = host;
    this.ports = ports;
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
    String url = "http://" + host + ":" + use_port;
    ProcessDeploymentDescriptor descriptor = md1.getDescriptor();
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, descriptor,
            ports, use_port);
    ModuleHandle mh = pmh;
    mh.start(future -> {
      if (future.succeeded()) {
        DeploymentDescriptor md2 = new DeploymentDescriptor(id, md1.getName(),
                url, md1.getDescriptor(), mh);
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

  public void list(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    List<DeploymentDescriptor> ml = new ArrayList<>();
    for (String id : list.keySet()) {
      ml.add(list.get(id));
    }
    fut.handle(new Success<>(ml));
  }

  public void get(String id, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    if (!list.containsKey(id)) {
      fut.handle(new Failure<>(NOT_FOUND, "not found: " + id));
    } else {
      fut.handle(new Success<>(list.get(id)));
    }
  }

  public void update(DeploymentDescriptor md1, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    String id = md1.getId();
    if (!list.containsKey(id)) {
      fut.handle(new Failure<>(USER, "not found: " + id));
      return;
    }
    DeploymentDescriptor md0 = list.get(id);
    int use_port = ports.get();
    if (use_port == -1) {
      fut.handle(new Failure<>(INTERNAL, "all ports in use"));
      return;
    }
    String url = "http://" + host + ":" + use_port;
    ProcessDeploymentDescriptor descriptor = md1.getDescriptor();
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, descriptor,
            ports, use_port);
    ModuleHandle mh = pmh;
    mh.start(future -> {
      if (future.succeeded()) {
        DeploymentDescriptor md2 = new DeploymentDescriptor(id, md1.getName(),
                url, md1.getDescriptor(), mh);
        ModuleHandle mh0 = md0.getModuleHandle();
        mh0.stop(future0 -> {
          if (future0.succeeded()) {
            list.replace(id, md2);
            fut.handle(new Success<>(md2));
          } else {
            // could not stop existing module. Return cause of it
            mh.stop(future1 -> {
              // we don't care whether stop of new module fails
              fut.handle(new Failure<>(INTERNAL, future0.cause()));
            });
          }
        });
      } else {
        ports.free(use_port);
        fut.handle(new Failure<>(INTERNAL, future.cause()));
      }
    });
  }

}
