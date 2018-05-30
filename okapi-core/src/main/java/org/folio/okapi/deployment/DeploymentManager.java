package org.folio.okapi.deployment;

import com.codahale.metrics.Timer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.EnvEntry;
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
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.env.EnvManager;
import org.folio.okapi.util.CompList;

/**
 * Manages deployment of modules. This actually spawns processes and allocates
 * ports for modules that are to be run.
 */
public class DeploymentManager {

  private final Logger logger = OkapiLogger.get();
  private final LinkedHashMap<String, DeploymentDescriptor> list = new LinkedHashMap<>();
  private final Vertx vertx;
  private final Ports ports;
  private final String host;
  private final DiscoveryManager dm;
  private final EnvManager em;
  private final int listenPort;
  private final String nodeName;

  public DeploymentManager(Vertx vertx, DiscoveryManager dm, EnvManager em,
    String host, Ports ports, int listenPort, String nodeName) {
    this.dm = dm;
    this.em = em;
    this.vertx = vertx;
    this.host = host;
    this.listenPort = listenPort;
    this.ports = ports;
    this.nodeName = nodeName;
  }

  public void init(Handler<ExtendedAsyncResult<Void>> fut) {
    NodeDescriptor nd = new NodeDescriptor();
    nd.setUrl("http://" + host + ":" + listenPort);
    nd.setNodeId(host);
    nd.setNodeName(nodeName);
    dm.addNode(nd, fut);
  }

  public void shutdown(Handler<ExtendedAsyncResult<Void>> fut) {
    logger.info("fast shutdown");
    CompList<Void> futures = new CompList<>(INTERNAL);
    Collection<DeploymentDescriptor > col = list.values();
    for (DeploymentDescriptor dd : col) {
      ModuleHandle mh = dd.getModuleHandle();
      Future<Void> f = Future.future();
      mh.stop(f::handle);
      futures.add(f);
    }
    futures.all(fut);
  }

  public void deploy(DeploymentDescriptor md1, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    String id = md1.getInstId();
    if (id != null && list.containsKey(id)) {
      fut.handle(new Failure<>(USER, "already deployed: " + id));
      return;
    }
    String srvc = md1.getSrvcId();
    if (srvc == null) {
      fut.handle(new Failure<>(USER, "Needs srvcId"));
      return;
    }
    Timer.Context tim = DropwizardHelper.getTimerContext("deploy." + srvc + ".deploy");

    int usePort = ports.get();
    if (usePort == -1) {
      fut.handle(new Failure<>(USER, "all ports in use"));
      tim.close();
      return;
    }
    String url = "http://" + host + ":" + usePort;

    if (id == null) {
      id = UUID.randomUUID().toString();
      md1.setInstId(id);
    }
    logger.info("deploy instId " + id);
    deploy2(fut, tim, usePort, md1, url);
  }

  private void deploy2(Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut,
    Timer.Context tim, int usePort, DeploymentDescriptor md1, String url) {

    LaunchDescriptor descriptor = md1.getDescriptor();
    if (descriptor == null) {
      ports.free(usePort);
      fut.handle(new Failure<>(USER, "No LaunchDescriptor"));
      tim.close();
      return;
    }
    HashMap<String, EnvEntry> entries = new HashMap<>();
    EnvEntry[] env = descriptor.getEnv();
    if (env != null) {
      for (EnvEntry e : env) {
        entries.put(e.getName(), e);
      }
    }
    em.get(eres -> {
      if (eres.failed()) {
        ports.free(usePort);
        fut.handle(new Failure<>(INTERNAL, "get env: " + eres.cause().getMessage()));
        tim.close();
      } else {
        for (EnvEntry er : eres.result()) {
          entries.put(er.getName(), er);
        }
        if (entries.size() > 0) {
          EnvEntry[] nenv = new EnvEntry[entries.size()];
          int i = 0;
          for (Entry<String, EnvEntry> key : entries.entrySet()) {
            nenv[i++] = key.getValue();
          }
          descriptor.setEnv(nenv);
        }
        ModuleHandle mh = ModuleHandleFactory.create(vertx, descriptor, md1.getSrvcId(), ports, usePort);
        mh.start(future -> {
          if (future.succeeded()) {
            DeploymentDescriptor md2
              = new DeploymentDescriptor(md1.getInstId(), md1.getSrvcId(),
                url, md1.getDescriptor(), mh);
            md2.setNodeId(md1.getNodeId() != null ? md1.getNodeId() : host);
            list.put(md2.getInstId(), md2);
            tim.close();
            dm.add(md2, res -> fut.handle(new Success<>(md2)));
          } else {
            tim.close();
            ports.free(usePort);
            logger.warn("Deploying " + md1.getSrvcId() + " failed");
            fut.handle(new Failure<>(USER, future.cause()));
          }
        });
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
              tim.close();
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
    for (Map.Entry<String, DeploymentDescriptor> entry : list.entrySet()) {
      ml.add(entry.getValue());
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
