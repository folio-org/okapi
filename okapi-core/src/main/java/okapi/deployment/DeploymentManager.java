/*
 * Copyright (C) 2016 Index Data
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
package okapi.deployment;

import com.codahale.metrics.Timer;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import okapi.bean.DeploymentDescriptor;
import okapi.bean.NodeDescriptor;
import okapi.util.ModuleHandle;
import okapi.bean.Ports;
import okapi.bean.ProcessDeploymentDescriptor;
import okapi.discovery.DiscoveryManager;
import okapi.util.DropwizardHelper;
import okapi.util.ProcessModuleHandle;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;
import static okapi.util.OkapiEvents.*;

public class DeploymentManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  LinkedHashMap<String, DeploymentDescriptor> list = new LinkedHashMap<>();
  Vertx vertx;
  Ports ports;
  String host;
  DiscoveryManager dm;
  private final int listenPort;

  public DeploymentManager(Vertx vertx, DiscoveryManager dm,
          String host, Ports ports, int listenPort) {
    this.dm = dm;
    this.vertx = vertx;
    this.host = host;
    this.listenPort = listenPort;
    this.ports = ports;
  }

  public void init(Handler<ExtendedAsyncResult<Void>> fut) {
    NodeDescriptor nd = new NodeDescriptor();
    nd.setUrl("http://" + host + ":" + listenPort);
    nd.setNodeId(host);
    dm.addNode(nd, fut);
  }

  public void shutdown(Handler<ExtendedAsyncResult<Void>> fut) {
    NodeDescriptor nd = new NodeDescriptor();
    nd.setUrl("http://" + host + ":" + listenPort);
    nd.setNodeId(host);
    dm.removeNode(nd, res -> {
      if (res.failed()) {
        logger.warn("shutdown: " + res.cause().getMessage());
      } else {
        logger.info("shutdown in progress");
      }
      shutdownR(fut);
    });
  }

  private void shutdownR(Handler<ExtendedAsyncResult<Void>> fut) {
    Iterator<String> it = list.keySet().iterator();
    if (!it.hasNext()) {
      fut.handle(new Success<>());
    } else {
      undeploy(it.next(), res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          shutdownR(fut);
        }
      });
    }
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
        dm.add(md2, res -> {
          fut.handle(new Success<>(md2));
        });
      } else {
        tim.stop();
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
      dm.remove(md.getSrvcId(), md.getInstId(), res -> {
        if (res.failed()) {
          tim.close();
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          ModuleHandle mh = md.getModuleHandle();
          mh.stop(future -> {
            if (future.failed()) {
              fut.handle(new Failure<>(INTERNAL, future.cause()));
            } else {
              fut.handle(new Success<>());
              tim.close();
              list.remove(id);
            }
          });
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
