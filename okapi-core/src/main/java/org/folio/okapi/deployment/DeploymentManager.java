package org.folio.okapi.deployment;

import com.codahale.metrics.Timer;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.NodeDescriptor;
import org.folio.okapi.util.ModuleHandle;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.discovery.DiscoveryManager;
import org.folio.okapi.util.DropwizardHelper;
import org.folio.okapi.util.ModuleHandleFactory;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Manages deployment of modules.
 * This actually spawns processes and allocates ports for modules that are
 * to be run.
 */
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
    shutdownR(list.keySet().iterator(), fut);
  }

  private void shutdownR(Iterator<String> it, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>());
    } else {
      DeploymentDescriptor md = list.get(it.next());
      ModuleHandle mh = md.getModuleHandle();
      mh.stop(future -> {
        shutdownR(it, fut);
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
    logger.info("deploy instId " + id);
    LaunchDescriptor descriptor = md1.getDescriptor();
    ModuleHandle mh = ModuleHandleFactory.create(vertx, descriptor, ports, use_port);

    mh.start(future -> {
      if (future.succeeded()) {
        DeploymentDescriptor md2
                = new DeploymentDescriptor(md1.getInstId(), md1.getSrvcId(),
                        url, md1.getDescriptor(), mh);
        md2.setNodeId(md1.getNodeId() != null ? md1.getNodeId() : host);
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
    logger.info("undeploy instId " + id);
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

}
