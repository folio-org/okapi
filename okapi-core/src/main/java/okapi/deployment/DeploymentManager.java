/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.deployment;

import com.codahale.metrics.Timer;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import okapi.bean.DeploymentDescriptor;
import okapi.util.ModuleHandle;
import okapi.bean.Ports;
import okapi.bean.ProcessDeploymentDescriptor;
import okapi.util.DropwizardHelper;
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
  private EventBus eb;
  private final String eventBusName = "okapi.conf.discovery";

  public DeploymentManager(Vertx vertx, String host, Ports ports) {
    this.vertx = vertx;
    this.host = host;
    this.ports = ports;
    this.eb = vertx.eventBus();
  }

  public void deploy(DeploymentDescriptor md1, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    String id = md1.getInstId();
    if (id != null) {
      if (list.containsKey(id)) {
        fut.handle(new Failure<>(USER, "already deployed: " + id));
        return;
      }
    }
    String srvc = md1.getSrvcId();
    Timer.Context tim = DropwizardHelper.getTimerContext("deploy." + srvc + ".deploy");

    int use_port = ports.get();
    if (use_port == -1) {
      fut.handle(new Failure<>(INTERNAL, "all ports in use"));
      return;
    }
    String url = "http://" + host + ":" + use_port;

    if (id == null) {
      id = host + "-" + use_port;
      md1.setInstId(id);
    }
    ProcessDeploymentDescriptor descriptor = md1.getDescriptor();
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, descriptor,
            ports, use_port);
    ModuleHandle mh = pmh;
    mh.start(future -> {
      if (future.succeeded()) {
        DeploymentDescriptor md2
                = new DeploymentDescriptor(md1.getInstId(), md1.getSrvcId(),
                        url, md1.getDescriptor(), mh);
        md2.setNodeId(host);
        list.put(md2.getInstId(), md2);
        tim.stop();
        final String s = Json.encodePrettily(md2);
        eb.send(eventBusName + ".deploy", s);
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
      Timer.Context tim = DropwizardHelper.getTimerContext("deploy." + id + ".undeploy");
      DeploymentDescriptor md = list.get(id);
      final String s = Json.encodePrettily(md);
      eb.send(eventBusName + ".undeploy", s);
      ModuleHandle mh = md.getModuleHandle();
      mh.stop(future -> {
        if (future.succeeded()) {
          fut.handle(new Success<>());
          tim.close();
          list.remove(id);
        } else {
          fut.handle(new Failure<>(INTERNAL, future.cause()));
        }
      });
    }
  }

  public void list(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    List<DeploymentDescriptor> ml = new LinkedList<>();
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
    String id = md1.getInstId();
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
    Timer.Context tim = DropwizardHelper.getTimerContext("DeploymentManager." + id + ".update");
    String url = "http://" + host + ":" + use_port;
    ProcessDeploymentDescriptor descriptor = md1.getDescriptor();
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, descriptor,
            ports, use_port);
    ModuleHandle mh = pmh;
    mh.start(future -> {
      if (future.succeeded()) {
        DeploymentDescriptor md2 = new DeploymentDescriptor(id, md1.getSrvcId(),
                url, md1.getDescriptor(), mh);
        md2.setNodeId(host);
        ModuleHandle mh0 = md0.getModuleHandle();
        mh0.stop(future0 -> {
          if (future0.succeeded()) {
            list.replace(id, md2);
            tim.close();
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
