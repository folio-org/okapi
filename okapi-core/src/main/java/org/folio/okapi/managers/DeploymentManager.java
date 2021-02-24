package org.folio.okapi.managers;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.NodeDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.ModuleHandle;
import org.folio.okapi.service.impl.ModuleHandleFactory;
import org.folio.okapi.util.OkapiError;

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
  private final EventBus eventBus;
  private final JsonObject config;
  private final Messages messages = Messages.getInstance();

  /**
   * Construct deployment manager.
   * @param vertx Vert.x handle
   * @param dm Discovery manager
   * @param em Event manager
   * @param host host name for deployed services
   * @param listenPort listening port for deployment node
   * @param nodeName Logical node name
   * @param config configuration
   */
  public DeploymentManager(Vertx vertx, DiscoveryManager dm, EnvManager em,
                           String host, int listenPort, String nodeName, JsonObject config) {
    this.dm = dm;
    this.em = em;
    this.vertx = vertx;
    this.host = host;
    this.listenPort = listenPort;
    this.nodeName = nodeName;
    this.eventBus = vertx.eventBus();
    this.config = config;
    int portStart = Integer.parseInt(Config.getSysConf(
        "port_start", Integer.toString(listenPort + 1), config));
    int portEnd = Integer.parseInt(Config.getSysConf(
        "port_end", Integer.toString(portStart + 10), config));
    this.ports = new Ports(portStart, portEnd);
  }

  /**
   * Initialize deployment manager.
   * @returns async result
   */
  public Future<Void> init() {
    NodeDescriptor nd = new NodeDescriptor();
    nd.setUrl("http://" + host + ":" + listenPort);
    nd.setNodeId(host);
    nd.setNodeName(nodeName);
    eventBus.consumer(nd.getUrl() + "/deploy", message -> {
      String b = (String) message.body();
      DeploymentDescriptor dd = Json.decodeValue(b, DeploymentDescriptor.class);
      deploy(dd).onFailure(cause ->
          message.fail(OkapiError.getType(cause).ordinal(), cause.getMessage())
      ).onSuccess(res -> message.reply(Json.encodePrettily(res)));
    });
    eventBus.consumer(nd.getUrl() + "/undeploy", message -> {
      String instId = (String) message.body();
      undeploy(instId).onFailure(cause ->
          message.fail(400, cause.getMessage())
      ).onSuccess(res ->
          message.reply(null)
      );
    });
    return dm.addNode(nd);
  }

  /**
   * async shutdown of deployment manager.
   * @return fut async result
   */
  public Future<Void> shutdown() {
    logger.info("shutdown");
    List<Future> futures = new LinkedList<>();
    Collection<DeploymentDescriptor> col = list.values();
    for (DeploymentDescriptor dd : col) {
      ModuleHandle mh = dd.getModuleHandle();
      logger.info("shutting down {}", dd.getSrvcId());
      futures.add(mh.stop());
    }
    return CompositeFuture.all(futures).mapEmpty();
  }

  Future<DeploymentDescriptor> deploy(DeploymentDescriptor md1) {
    String id = md1.getInstId();

    if (id != null && list.containsKey(id)) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, messages.getMessage("10700", id)));
    }
    String srvc = md1.getSrvcId();
    if (srvc == null) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, messages.getMessage("10701")));
    }
    int usePort = ports.get();
    if (usePort == -1) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, messages.getMessage("10702")));
    }
    if (id == null) {
      id = UUID.randomUUID().toString();
      md1.setInstId(id);
    }
    logger.info("deploy instId {}", id);
    return deploy2(usePort, md1);
  }

  @SuppressWarnings("indentation")  // indentation of fail -> {
  private Future<DeploymentDescriptor> deploy2(int usePort, DeploymentDescriptor md1) {

    LaunchDescriptor descriptor = md1.getDescriptor();
    if (descriptor == null) {
      ports.free(usePort);
      return Future.failedFuture(new OkapiError(ErrorType.USER, messages.getMessage("10703")));
    }
    HashMap<String, EnvEntry> entries = new HashMap<>();
    EnvEntry[] env = descriptor.getEnv();
    if (env != null) {
      for (EnvEntry e : env) {
        entries.put(e.getName(), e);
      }
    }
    return em.get().compose(eres -> {
      for (EnvEntry er : eres) {
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
      String moduleUrl = "http://" + host + ":" + usePort;
      String moduleHost = host;
      if (descriptor.getDockerImage() != null) {
        moduleHost = Config.getSysConf("containerHost", host, config);
      }
      ModuleHandle mh = ModuleHandleFactory.create(vertx, descriptor,
          md1.getSrvcId(), ports, moduleHost, usePort, config);
      return mh.start().compose(res -> {
        DeploymentDescriptor md2
            = new DeploymentDescriptor(md1.getInstId(), md1.getSrvcId(),
            moduleUrl, descriptor, mh);
        md2.setNodeId(md1.getNodeId() != null ? md1.getNodeId() : host);
        list.put(md2.getInstId(), md2);
        return dm.add(md2).map(md2);
      }, fail -> {
        ports.free(usePort);
        logger.warn("Deploying {} failed", md1.getSrvcId());
        return Future.failedFuture(new OkapiError(ErrorType.USER, fail.getMessage()));
      });
    });
  }

  Future<Void> undeploy(String id) {
    logger.info("undeploy instId {}", id);
    if (!list.containsKey(id)) {
      return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND,
          messages.getMessage("10705", id)));
    }
    DeploymentDescriptor md = list.get(id);
    return dm.remove(md.getSrvcId(), md.getInstId()).compose(res -> {
      ModuleHandle mh = md.getModuleHandle();
      return mh.stop().compose(x -> {
        list.remove(id);
        return Future.succeededFuture();
      });
    }).mapEmpty();
  }

  Future<List<DeploymentDescriptor>> list() {
    List<DeploymentDescriptor> ml = new LinkedList<>();
    for (Map.Entry<String, DeploymentDescriptor> entry : list.entrySet()) {
      ml.add(entry.getValue());
    }
    return Future.succeededFuture(ml);
  }

  Future<DeploymentDescriptor> get(String id) {
    if (!list.containsKey(id)) {
      return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND,
          messages.getMessage("10705", id)));
    }
    return Future.succeededFuture(list.get(id));
  }
}
