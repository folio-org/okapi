package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.Json;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.HealthDescriptor;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.NodeDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.service.DeploymentStore;
import org.folio.okapi.util.FuturisedHttpClient;
import org.folio.okapi.util.JsonDecoder;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.LockedTypedMap2;
import org.folio.okapi.util.OkapiError;


/**
 * Keeps track of which modules are running where. Uses a shared map to list
 * running modules on the different nodes. Maps a SrvcId to a
 * DeploymentDescriptor. Can also invoke deployment, and record the result in
 * its map.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class DiscoveryManager implements NodeListener {
  private final Logger logger = OkapiLogger.get();

  private final LockedTypedMap2<DeploymentDescriptor> deployments
      = new LockedTypedMap2<>(DeploymentDescriptor.class);
  private final LockedTypedMap1<NodeDescriptor> nodes = new LockedTypedMap1<>(NodeDescriptor.class);
  private Vertx vertx;
  private ClusterManager clusterManager;
  private ModuleManager moduleManager;
  private FuturisedHttpClient httpClient;
  private final DeploymentStore deploymentStore;
  private final Messages messages = Messages.getInstance();
  private DeliveryOptions deliveryOptions;

  /**
   * Initialize discovery manager.
   * @param vertx Vert.x handle
   * @return future result
   */
  public Future<Void> init(Vertx vertx) {
    this.vertx = vertx;
    this.httpClient = new FuturisedHttpClient(vertx);
    deliveryOptions = new DeliveryOptions().setSendTimeout(36000000); // 1 hour
    return deployments.init(vertx, "discoveryList", false).compose(x ->
        nodes.init(vertx, "discoveryNodes", false));
  }

  /**
   * async shutdown discovery manager.
   * @return fut async result
   */
  public Future<Void> shutdown() {
    logger.info("shutdown");
    if (clusterManager != null) {
      return Future.succeededFuture();
    }
    return deployments.clear();
  }

  /**
   * Restart modules that were persisted in storage.
   * @return async result
   */
  public Future<Void> restartModules() {
    return deploymentStore.getAll().compose(result -> {
      List<Future<DeploymentDescriptor>> futures = new LinkedList<>();
      for (DeploymentDescriptor dd : result) {
        futures.add(deployments.get(dd.getSrvcId(), dd.getInstId()).compose(d -> {
          if (d == null) {
            logger.info("Restart: adding {} {}", dd.getSrvcId(), dd.getInstId());
            return addAndDeployIgnoreError(dd);
          } else {
            logger.info("Restart: skipping {} {}", dd.getSrvcId(), dd.getInstId());
            return Future.succeededFuture();
          }
        }));
      }
      return GenericCompositeFuture.all(futures).mapEmpty();
    });
  }

  public DiscoveryManager(DeploymentStore ds) {
    deploymentStore = ds;
  }

  public void setClusterManager(ClusterManager mgr) {
    this.clusterManager = mgr;
    mgr.nodeListener(this);
  }

  public void setModuleManager(ModuleManager mgr) {
    this.moduleManager = mgr;
  }

  Future<Void> add(DeploymentDescriptor md) {
    return deployments.getKeys().compose(res -> {
      Future<Void> future = Future.succeededFuture();
      for (String moduleId : res) {
        future = future.compose(a -> deployments.get(moduleId, md.getInstId()).compose(b -> {
          if (b != null) {
            return Future.failedFuture(new OkapiError(ErrorType.USER,
                messages.getMessage("10809", md.getInstId())));
          }
          return Future.succeededFuture();
        }));
      }
      return future.compose(res2 -> deployments.add(md.getSrvcId(), md.getInstId(), md)).mapEmpty();
    });
  }

  Future<DeploymentDescriptor> addAndDeploy(DeploymentDescriptor dd) {
    return addAndDeploy0(dd).compose(res -> deploymentStore.insert(res).map(res));
  }

  Future<DeploymentDescriptor> addAndDeployIgnoreError(DeploymentDescriptor dd) {
    return addAndDeploy0(dd).recover(cause -> {
      logger.warn("Deployment of {} {} failed (ignored): {}", dd.getSrvcId(), dd.getInstId(),
          cause.getMessage());
      return Future.succeededFuture();
    });
  }

  /**
   * Adds a service to the discovery, and optionally deploys it too.
   *
   * <ol>
   * <li>e have LaunchDescriptor and NodeId: Deploy on that node.</li>
   * <li>NodeId, but no LaunchDescriptor: Fetch the module, use itsLaunchDescriptor, and
   * deploy.</li>
   * <li>No nodeId: Do not deploy at all, just record the existence (URL and instId) of
   * the module</li>
   * </ol>
   */
  private Future<DeploymentDescriptor> addAndDeploy0(DeploymentDescriptor dd) {
    final String id = dd.getSrvcId();
    logger.info("addAndDeploy: {} {} {}", dd.getNodeId(), id,
        dd.getDescriptor() == null ? null : dd.getDescriptor().getDockerImage());
    if (id == null) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, messages.getMessage("10800")));
    }
    LaunchDescriptor launchDesc = dd.getDescriptor();
    final String nodeId = dd.getNodeId();
    if (nodeId == null) {
      if (launchDesc != null) {
        return Future.failedFuture(new OkapiError(ErrorType.USER, messages.getMessage("10803")));
      }
      if (dd.getInstId() == null) {
        return Future.failedFuture(new OkapiError(ErrorType.USER, messages.getMessage("10802")));
      }
      return add(dd).map(dd);
    }
    if (launchDesc != null) {
      return callDeploy(nodeId, dd);
    }
    return moduleManager.get(id).compose(md -> {
      LaunchDescriptor modLaunchDesc = md.getLaunchDescriptor();
      if (modLaunchDesc == null) {
        return Future.failedFuture(new OkapiError(ErrorType.USER,
            messages.getMessage("10804", id)));
      }
      dd.setDescriptor(modLaunchDesc);
      return callDeploy(nodeId, dd);
    });
  }

  /**
   * Helper to actually launch (deploy) a module on a node.
   */
  private Future<DeploymentDescriptor> callDeploy(String nodeId, DeploymentDescriptor dd) {
    return getNode(nodeId)
        .flatMap(nodeDescriptor -> {
          String url = nodeDescriptor.getUrl() + "/deploy";
          return vertx.eventBus().request(url, Json.encode(dd), deliveryOptions)
              .recover(e -> Future.failedFuture(new OkapiError(ErrorType.USER, e.getMessage())));
        })
        .map(message -> JsonDecoder.decode((String) message.body(), DeploymentDescriptor.class));
  }

  Future<Void> removeAndUndeploy(String srvcId, String instId) {
    logger.info("removeAndUndeploy: srvcId {} instId {}", srvcId, instId);
    return deployments.getNotFound(srvcId, instId).compose(res -> {
      List<DeploymentDescriptor> ddList = new LinkedList<>();
      ddList.add(res);
      return removeAndUndeploy(ddList);
    });
  }

  Future<Void> removeAndUndeploy(String srvcId) {
    logger.info("removeAndUndeploy: srvcId {}", srvcId);
    return deployments.get(srvcId).compose(res -> {
      if (res == null) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, srvcId));
      }
      return removeAndUndeploy(res);
    });
  }

  Future<Void> removeAndUndeploy() {
    logger.info("removeAndUndeploy all");
    return this.get().compose(this::removeAndUndeploy);
  }

  private Future<Void> removeAndUndeploy(List<DeploymentDescriptor> ddList) {

    List<Future<Void>> futures = new LinkedList<>();
    for (DeploymentDescriptor dd : ddList) {
      futures.add(callUndeploy(dd)
          .compose(res -> deploymentStore.delete(dd.getInstId()))
          .mapEmpty());
    }
    return GenericCompositeFuture.all(futures).mapEmpty();
  }

  private Future<Void> callUndeploy(DeploymentDescriptor md) {

    logger.info("callUndeploy srvcId={} instId={} node={}",
        md.getSrvcId(), md.getInstId(), md.getNodeId());
    final String nodeId = md.getNodeId();
    if (nodeId == null) {
      return remove(md.getSrvcId(), md.getInstId()).mapEmpty();
    }
    return getNode(nodeId).compose(res ->
        vertx.eventBus().request(res.getUrl() + "/undeploy", md.getInstId(),
            deliveryOptions).mapEmpty()
    );
  }

  Future<Boolean> remove(String srvcId, String instId) {
    return deployments.remove(srvcId, instId);
  }

  private boolean isAlive(DeploymentDescriptor md, Collection<NodeDescriptor> nodes) {
    final String id = md.getNodeId();
    if (id == null) {
      return true;
    }
    boolean found = false;
    for (NodeDescriptor node : nodes) {
      final String nodeName = node.getNodeName();
      final String nodeId = node.getNodeId();
      if (id.equals(nodeId) || id.equals(nodeName)) {
        found = true;
        break;
      }
    }
    logger.debug("isAlive nodeId={} {}", id, found);
    return found;
  }

  Future<Void> autoDeploy(ModuleDescriptor md) {

    logger.info("autoDeploy {}", md.getId());
    // internal Okapi modules is not part of discovery so ignore it
    if (md.getId().startsWith(XOkapiHeaders.OKAPI_MODULE)) {
      return Future.succeededFuture();
    }
    return deployments.get(md.getId()).compose(res -> {
      if (res != null) {
        logger.info("autoDeploy {} already deployed", md.getId());
        return Future.succeededFuture(); // already deployed
      }
      return nodes.getKeys().compose(allNodes -> autoDeploy2(md, allNodes));
    });
  }

  private Future<Void> autoDeploy2(ModuleDescriptor md, Collection<String> allNodes) {
    LaunchDescriptor modLaunchDesc = md.getLaunchDescriptor();
    List<Future<DeploymentDescriptor>> futures = new LinkedList<>();
    // deploy on all nodes for now
    for (String node : allNodes) {
      // check if we have deploy on node
      logger.info("autoDeploy {} must deploy on node {}", md.getId(), node);
      DeploymentDescriptor dd = new DeploymentDescriptor();
      dd.setDescriptor(modLaunchDesc);
      dd.setSrvcId(md.getId());
      dd.setNodeId(node);
      futures.add(addAndDeploy(dd));
    }
    return GenericCompositeFuture.all(futures).mapEmpty();
  }

  Future<Void> autoUndeploy(ModuleDescriptor md) {

    logger.info("autoUndeploy {}", md.getId());
    if (md.getId().startsWith(XOkapiHeaders.OKAPI_MODULE)) {
      return Future.succeededFuture();
    }
    return deployments.get(md.getId()).compose(deploymentList -> {
      if (deploymentList == null) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, md.getId()));
      }
      List<Future<Void>> futures = new LinkedList<>();
      for (DeploymentDescriptor dd : deploymentList) {
        if (dd.getNodeId() != null) {
          futures.add(callUndeploy(dd));
        }
      }
      return GenericCompositeFuture.all(futures).mapEmpty();
    });
  }

  Future<List<DeploymentDescriptor>> getNonEmpty(String srvcId) {
    return get(srvcId).compose(res -> {
      if (res.isEmpty()) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, srvcId));
      }
      return Future.succeededFuture(res);
    });
  }

  Future<List<DeploymentDescriptor>> get(String srvcId) {
    return deployments.get(srvcId).compose(result -> {
      if (result == null) {
        return Future.succeededFuture(new LinkedList<>());
      }
      return nodes.getAll().compose(nodeRes -> {
        Collection<NodeDescriptor> nodesCollection = nodeRes.values();
        Iterator<DeploymentDescriptor> it = result.iterator();
        while (it.hasNext()) {
          DeploymentDescriptor md = it.next();
          if (!isAlive(md, nodesCollection)) {
            it.remove();
          }
        }
        return Future.succeededFuture(result);
      });
    });
  }

  /**
   * Get all known DeploymentDescriptors (all services on all nodes).
   */
  public Future<List<DeploymentDescriptor>> get() {
    return deployments.getKeys().compose(keys -> {
      List<DeploymentDescriptor> all = new LinkedList<>();
      List<Future<NodeDescriptor>> futures = new LinkedList<>();
      for (String s : keys) {
        futures.add(this.get(s).compose(res -> {
          all.addAll(res);
          return Future.succeededFuture();
        }));
      }
      return GenericCompositeFuture.all(futures).map(all);
    });
  }

  Future<DeploymentDescriptor> get(String srvcId, String instId) {
    return deployments.getNotFound(srvcId, instId).compose(md ->
        nodes.getAll().compose(nodeRes -> {
          Collection<NodeDescriptor> nodesCollection = nodeRes.values();
          // check that the node is alive, but only on non-url instances
          if (!isAlive(md, nodesCollection)) {
            return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND,
                messages.getMessage("10805")));
          }
          return Future.succeededFuture(md);
        })
    );
  }

  private Future<List<HealthDescriptor>> healthList(List<DeploymentDescriptor> list) {
    List<HealthDescriptor> all = new LinkedList<>();
    List<Future<Void>> futures = new LinkedList<>();
    for (DeploymentDescriptor md : list) {
      futures.add(health(md).compose(x -> {
        all.add(x);
        return Future.succeededFuture();
      }));
    }
    return GenericCompositeFuture.all(futures).compose(x -> Future.succeededFuture(all));
  }

  Future<HealthDescriptor> fail(Throwable cause, HealthDescriptor hd) {

    hd.setHealthMessage("Fail: " + cause.getMessage());
    hd.setHealthStatus(false);
    return Future.succeededFuture(hd);
  }

  Future<HealthDescriptor> health(DeploymentDescriptor md) {

    HealthDescriptor hd = new HealthDescriptor();
    String url = md.getUrl();
    hd.setInstId(md.getInstId());
    hd.setSrvcId(md.getSrvcId());
    if (url == null || url.length() == 0) {
      hd.setHealthMessage("Unknown");
      hd.setHealthStatus(false);
      return Future.succeededFuture(hd);
    }
    Promise<HealthDescriptor> promise = Promise.promise();
    httpClient.request(new RequestOptions().setAbsoluteURI(url).setMethod(HttpMethod.GET), req -> {
      if (req.failed()) {
        promise.handle(fail(req.cause(), hd));
        return;
      }
      req.result().end();
      req.result().response().onFailure(cause ->
          promise.handle(fail(cause, hd))
      ).onSuccess(response -> {
        response.endHandler(x -> {
          hd.setHealthMessage("OK");
          hd.setHealthStatus(true);
          promise.complete(hd);
        });
        response.exceptionHandler(e -> promise.handle(fail(e.getCause(), hd)));
      });
    });
    return promise.future();
  }

  Future<List<HealthDescriptor>> health() {
    return get().compose(this::healthList);
  }

  Future<HealthDescriptor> health(String srvcId, String instId) {
    return DiscoveryManager.this.get(srvcId, instId).compose(this::health);
  }

  Future<List<HealthDescriptor>> health(String srvcId) {
    return getNonEmpty(srvcId).compose(this::healthList);
  }

  Future<Void> addNode(NodeDescriptor nd) {
    if (clusterManager != null) {
      nd.setNodeId(clusterManager.getNodeId());
    }
    return nodes.put(nd.getNodeId(), nd);
  }

  /**
   * Translate node url or node name to its id. If not found, returns the id itself.
   *
   * @param nodeRef node ID, node URL or node name
   * @return future with id
   */
  private Future<String> getNodeId(String nodeRef) {
    return getNodes().compose(result -> {
      for (NodeDescriptor nd : result) {
        if (nodeRef.compareTo(nd.getUrl()) == 0) {
          return Future.succeededFuture(nd.getNodeId());
        }
        String nm = nd.getNodeName();
        if (nm != null && nodeRef.compareTo(nm) == 0) {
          return Future.succeededFuture(nd.getNodeId());
        }
      }
      return Future.succeededFuture(nodeRef); // try with the original id
    });
  }

  Future<NodeDescriptor> getNode(String nodeRef) {
    return getNodeId(nodeRef)
        .compose(nodeId -> getNode1(nodeId))
        .compose(nodeDescriptor -> {
          if (nodeDescriptor == null) {
            return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND,
                messages.getMessage("10806", nodeRef)));
          }
          return Future.succeededFuture(nodeDescriptor);
        });
  }

  private Future<NodeDescriptor> getNode1(String nodeId) {
    if (clusterManager != null) {
      List<String> n = clusterManager.getNodes();
      if (!n.contains(nodeId)) {
        return Future.succeededFuture(null);
      }
    }
    return nodes.get(nodeId);
  }

  Future<NodeDescriptor> updateNode(String nodeId, NodeDescriptor nd) {
    if (clusterManager != null) {
      List<String> n = clusterManager.getNodes();
      if (!n.contains(nodeId)) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND,
            messages.getMessage("10806", nodeId)));
      }
    }
    return nodes.getNotFound(nodeId).compose(res -> {
      if (!res.getNodeId().equals(nd.getNodeId()) || !nd.getNodeId().equals(nodeId)) {
        return Future.failedFuture(new OkapiError(ErrorType.USER,
            messages.getMessage("10807", nodeId)));
      }
      if (!res.getUrl().equals(nd.getUrl())) {
        return Future.failedFuture(new OkapiError(ErrorType.USER,
            messages.getMessage("10808", nodeId)));
      }
      return nodes.put(nodeId, nd).map(nd);
    });
  }

  Future<List<NodeDescriptor>> getNodes() {
    return nodes.getKeys().compose(keys -> {
      if (clusterManager != null) {
        List<String> n = clusterManager.getNodes();
        keys.retainAll(n);
      }
      List<NodeDescriptor> nodes = new LinkedList<>();
      List<Future<Void>> futures = new LinkedList<>();
      for (String nodeId : keys) {
        futures.add(getNode1(nodeId).compose(x -> {
          nodes.add(x);
          return Future.succeededFuture();
        }));
      }
      return GenericCompositeFuture.all(futures).map(nodes);
    });
  }

  @Override
  public void nodeAdded(String nodeID) {
    logger.info("node.add {}", nodeID);
  }

  @Override
  public void nodeLeft(String nodeID) {
    nodes.remove(nodeID).onComplete(res ->
        logger.info("node.remove {} result={}", nodeID, res.result()));
  }

  /**
   * Whether the node id of the cluster manager is the maximum node id of
   * all nodes of the cluster manager.
   *
   * <p>Return true if running without cluster manager.
   */
  boolean isLeader() {
    if (clusterManager == null) {
      return true;
    }
    List<String> nodeIds = clusterManager.getNodes();
    return clusterManager.getNodeId().equals(Collections.max(nodeIds));
  }
}
