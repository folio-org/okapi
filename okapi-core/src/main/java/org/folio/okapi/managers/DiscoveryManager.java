package org.folio.okapi.managers;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
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
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.service.DeploymentStore;
import org.folio.okapi.util.CompList;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.LockedTypedMap2;


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
  private HttpClient httpClient;
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
    this.httpClient = vertx.createHttpClient();
    deliveryOptions = new DeliveryOptions().setSendTimeout(300000); // 5 minutes
    return deployments.init(vertx, "discoveryList").compose(x ->
        nodes.init(vertx, "discoveryNodes"));
  }

  /**
   * Restart modules that were persisted in storage.
   * @return async result
   */
  public Future<Void> restartModules() {
    return deploymentStore.getAll().compose(result -> {
      List<Future> futures = new LinkedList<>();
      for (DeploymentDescriptor dd : result) {
        Promise<DeploymentDescriptor> promise = Promise.promise();
        addAndDeploy0(dd, promise::handle);
        futures.add(promise.future());
      }
      return CompositeFuture.all(futures).mapEmpty();
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

  void add(DeploymentDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    deployments.getKeys(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      CompList<Void> futures = new CompList<>(ErrorType.INTERNAL);
      for (String moduleId : res.result()) {
        Promise<Void> promise = Promise.promise();
        futures.add(promise);
        deployments.get(moduleId, md.getInstId(), r -> {
          if (r.succeeded()) {
            promise.fail("dup InstId");
            return;
          }
          promise.complete();
        });
      }
      futures.all(res2 -> {
        if (res2.failed()) {
          fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10809", md.getInstId())));
          return;
        }
        deployments.add(md.getSrvcId(), md.getInstId(), md, fut);
      });
    });
  }

  void addAndDeploy(DeploymentDescriptor dd,
                    Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    addAndDeploy0(dd, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      deploymentStore.insert(res.result()).onComplete(res1 -> {
        if (res1.failed()) {
          fut.handle(new Failure<>(ErrorType.INTERNAL, res1.cause()));
          return;
        }
        fut.handle(new Success<>(res.result()));
      });
    });
  }

  /**
   * Adds a service to the discovery, and optionally deploys it too.
   * <p>
   *   1: We have LaunchDescriptor and NodeId: Deploy on that node.
   *   2: NodeId, but no LaunchDescriptor: Fetch the module, use its LaunchDescriptor, and deploy.
   *   3: No nodeId: Do not deploy at all, just record the existence (URL and instId) of the module.
   * </p>
   */
  private void addAndDeploy0(DeploymentDescriptor dd,
                             Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {

    String tmp = Json.encodePrettily(dd);
    logger.info("addAndDeploy: {}", tmp);
    final String modId = dd.getSrvcId();
    if (modId == null) {
      fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10800")));
      return;
    }
    moduleManager.get(modId, gres -> {
      if (gres.failed()) {
        if (gres.getType() == ErrorType.NOT_FOUND) {
          fut.handle(new Failure<>(ErrorType.NOT_FOUND, messages.getMessage("10801", modId)));
        } else {
          fut.handle(new Failure<>(gres.getType(), gres.cause()));
        }
      } else {
        addAndDeploy1(dd, gres.result(), fut);
      }
    });
  }

  private void addAndDeploy1(DeploymentDescriptor dd, ModuleDescriptor md,
                             Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {

    LaunchDescriptor launchDesc = dd.getDescriptor();
    final String nodeId = dd.getNodeId();
    if (nodeId == null) {
      if (launchDesc == null) { // 3: externally deployed
        if (dd.getInstId() == null) {
          fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10802")));
        } else {
          add(dd, res -> { // just add it
            if (res.failed()) {
              fut.handle(new Failure<>(res.getType(), res.cause()));
            } else {
              fut.handle(new Success<>(dd));
            }
          });
        }
      } else {
        fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10803")));
      }
    } else {
      if (launchDesc == null) {
        addAndDeploy2(dd, md, fut, nodeId);
      } else { // Have a launch descriptor already in dd
        callDeploy(nodeId, dd, fut);
      }
    }
  }

  private void addAndDeploy2(DeploymentDescriptor dd, ModuleDescriptor md,
                             Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut,
                             String nodeId) {
    String modId = dd.getSrvcId();
    LaunchDescriptor modLaunchDesc = md.getLaunchDescriptor();
    if (modLaunchDesc == null) {
      fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10804", modId)));
    } else {
      dd.setDescriptor(modLaunchDesc);
      callDeploy(nodeId, dd, fut);
    }
  }

  /**
   * Helper to actually launch (deploy) a module on a node.
   */
  private void callDeploy(String nodeId, DeploymentDescriptor dd,
                          Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {

    getNode(nodeId).onComplete(nodeRes -> {
      if (nodeRes.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, nodeRes.cause()));
        return;
      }
      NodeDescriptor nodeDescriptor = nodeRes.result();
      if (nodeDescriptor == null) {
        fut.handle(new Failure<>(ErrorType.NOT_FOUND, nodeId));
        return;
      }
      String reqData = Json.encode(dd);
      vertx.eventBus().request(nodeDescriptor.getUrl() + "/deploy", reqData,
          deliveryOptions, ar -> {
            if (ar.failed()) {
              fut.handle(new Failure<>(ErrorType.USER, ar.cause().getMessage()));
            } else {
              String b = (String) ar.result().body();
              DeploymentDescriptor pmd = Json.decodeValue(b, DeploymentDescriptor.class);
              fut.handle(new Success<>(pmd));
            }
          });
    });
  }

  void removeAndUndeploy(String srvcId, String instId,
                         Handler<ExtendedAsyncResult<Void>> fut) {

    logger.info("removeAndUndeploy: srvcId {} instId {}", srvcId, instId);
    deployments.get(srvcId, instId, res -> {
      if (res.failed()) {
        logger.warn("deployment.get failed");
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        List<DeploymentDescriptor> ddList = new LinkedList<>();
        ddList.add(res.result());
        removeAndUndeploy(ddList, fut);
      }
    });
  }

  void removeAndUndeploy(String srvcId,
                         Handler<ExtendedAsyncResult<Void>> fut) {

    logger.info("removeAndUndeploy: srvcId {}", srvcId);
    deployments.get(srvcId, res -> {
      if (res.failed()) {
        logger.warn("deployment.get failed");
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        removeAndUndeploy(res.result(), fut);
      }
    });
  }

  void removeAndUndeploy(Handler<ExtendedAsyncResult<Void>> fut) {
    logger.info("removeAndUndeploy all");
    this.get(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        removeAndUndeploy(res.result(), fut);
      }
    });
  }

  private void removeAndUndeploy(List<DeploymentDescriptor> ddList,
                                 Handler<ExtendedAsyncResult<Void>> fut) {

    CompList<List<Void>> futures = new CompList<>(ErrorType.INTERNAL);
    for (DeploymentDescriptor dd : ddList) {
      Promise<Void> promise = Promise.promise();
      logger.info("removeAndUndeploy {} {}", dd.getSrvcId(), dd.getInstId());
      callUndeploy(dd, res -> {
        if (res.failed()) {
          promise.handle(res);
          return;
        }
        deploymentStore.delete(dd.getInstId()).onComplete(x -> promise.handle(x.mapEmpty()));
      });
      futures.add(promise);
    }
    futures.all(fut);
  }

  private void callUndeploy(DeploymentDescriptor md,
                            Handler<ExtendedAsyncResult<Void>> fut) {

    logger.info("callUndeploy srvcId={} instId={} node={}",
        md.getSrvcId(), md.getInstId(), md.getNodeId());
    final String nodeId = md.getNodeId();
    if (nodeId == null) {
      logger.info("callUndeploy remove");
      remove(md.getSrvcId(), md.getInstId(), fut);
      return;
    }
    logger.info("callUndeploy calling..");
    getNode(nodeId).onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      String reqdata = md.getInstId();
      vertx.eventBus().request(res.result().getUrl() + "/undeploy", reqdata,
          deliveryOptions, ar -> {
            if (ar.failed()) {
              fut.handle(new Failure<>(ErrorType.USER, ar.cause().getMessage()));
            } else {
              fut.handle(new Success<>());
            }
          });
    });
  }

  void remove(String srvcId, String instId,
              Handler<ExtendedAsyncResult<Void>> fut) {

    deployments.remove(srvcId, instId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
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

  void autoDeploy(ModuleDescriptor md,
                  Handler<ExtendedAsyncResult<Void>> fut) {

    logger.info("autoDeploy {}", md.getId());
    // internal Okapi modules is not part of discovery so ignore it
    if (md.getId().startsWith(XOkapiHeaders.OKAPI_MODULE)) {
      fut.handle(new Success<>());
      return;
    }
    nodes.getKeys(res1 -> {
      if (res1.failed()) {
        fut.handle(new Failure<>(res1.getType(), res1.cause()));
      } else {
        Collection<String> allNodes = res1.result();
        deployments.get(md.getId(), res -> {
          if (res.succeeded()) {
            autoDeploy2(md, allNodes, res.result(), fut);
          } else if (res.getType() == ErrorType.NOT_FOUND) {
            autoDeploy2(md, allNodes, new LinkedList<>(), fut);
          } else {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          }
        });
      }
    });
  }

  private void autoDeploy2(ModuleDescriptor md,
                           Collection<String> allNodes, List<DeploymentDescriptor> ddList,
                           Handler<ExtendedAsyncResult<Void>> fut) {

    LaunchDescriptor modLaunchDesc = md.getLaunchDescriptor();
    CompList<List<Void>> futures = new CompList<>(ErrorType.USER);
    // deploy on all nodes for now
    for (String node : allNodes) {
      // check if we have deploy on node
      logger.info("autoDeploy {} consider {}", md.getId(), node);
      DeploymentDescriptor foundDd = null;
      for (DeploymentDescriptor dd : ddList) {
        if (dd.getNodeId() == null || node.equals(dd.getNodeId())) {
          foundDd = dd;
        }
      }
      if (foundDd == null) {
        logger.info("autoDeploy {} must deploy on node {}", md.getId(), node);
        DeploymentDescriptor dd = new DeploymentDescriptor();
        dd.setDescriptor(modLaunchDesc);
        dd.setSrvcId(md.getId());
        dd.setNodeId(node);
        Promise<DeploymentDescriptor> promise = Promise.promise();
        addAndDeploy(dd, promise::handle);
        futures.add(promise);
      } else {
        logger.info("autoDeploy {} already deployed on {}", md.getId(), node);
      }
    }
    futures.all(fut);
  }

  void autoUndeploy(ModuleDescriptor md,
                    Handler<ExtendedAsyncResult<Void>> fut) {

    logger.info("autoUndeploy {}", md.getId());
    if (md.getId().startsWith(XOkapiHeaders.OKAPI_MODULE)) {
      fut.handle(new Success<>());
      return;
    }
    deployments.get(md.getId(), res -> {
      if (res.succeeded()) {
        List<DeploymentDescriptor> ddList = res.result();
        CompList<List<Void>> futures = new CompList<>(ErrorType.USER);
        for (DeploymentDescriptor dd : ddList) {
          if (dd.getNodeId() != null) {
            Promise<Void> promise = Promise.promise();
            callUndeploy(dd, promise::handle);
            futures.add(promise);
          }
        }
        futures.all(fut);
      } else {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      }
    });
  }

  void getNonEmpty(String srvcId,
                   Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {

    deployments.get(srvcId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      List<DeploymentDescriptor> result = res.result();
      nodes.getAll(nodeRes -> {
        if (nodeRes.failed()) {
          fut.handle(new Failure<>(nodeRes.getType(), nodeRes.cause()));
          return;
        }
        Collection<NodeDescriptor> nodesCollection = nodeRes.result().values();
        Iterator<DeploymentDescriptor> it = result.iterator();
        while (it.hasNext()) {
          DeploymentDescriptor md = it.next();
          if (!isAlive(md, nodesCollection)) {
            it.remove();
          }
        }
        fut.handle(new Success<>(result));
      });
    });
  }

  void get(String srvcId, Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    getNonEmpty(srvcId, res -> {
      if (res.failed() && res.getType() == ErrorType.NOT_FOUND) {
        fut.handle(new Success<>(new LinkedList<>()));
      } else {
        fut.handle(res);
      }
    });
  }

  /**
   * Get all known DeploymentDescriptors (all services on all nodes).
   */
  public void get(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    deployments.getKeys(resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
      } else {
        Collection<String> keys = resGet.result();
        List<DeploymentDescriptor> all = new LinkedList<>();
        CompList<List<DeploymentDescriptor>> futures = new CompList<>(ErrorType.INTERNAL);
        for (String s : keys) {
          Promise<List<DeploymentDescriptor>> promise = Promise.promise();
          this.get(s, res -> {
            if (res.succeeded()) {
              all.addAll(res.result());
            }
            promise.handle(res);
          });
          futures.add(promise);
        }
        futures.all(all, fut);
      }
    });
  }

  void get(String srvcId, String instId, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {

    deployments.get(srvcId, instId, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
        return;
      }
      DeploymentDescriptor md = resGet.result();
      nodes.getAll(nodeRes -> {
        if (nodeRes.failed()) {
          fut.handle(new Failure<>(nodeRes.getType(), nodeRes.cause()));
          return;
        }
        Collection<NodeDescriptor> nodesCollection = nodeRes.result().values();
        // check that the node is alive, but only on non-url instances
        if (!isAlive(md, nodesCollection)) {
          fut.handle(new Failure<>(ErrorType.NOT_FOUND, messages.getMessage("10805")));
          return;
        }
        fut.handle(new Success<>(md));
      });
    });
  }

  private void healthList(List<DeploymentDescriptor> list,
                          Handler<ExtendedAsyncResult<List<HealthDescriptor>>> fut) {

    List<HealthDescriptor> all = new LinkedList<>();
    CompList<List<HealthDescriptor>> futures = new CompList<>(ErrorType.INTERNAL);
    for (DeploymentDescriptor md : list) {
      Promise<HealthDescriptor> promise = Promise.promise();
      health(md, res -> {
        if (res.succeeded()) {
          all.add(res.result());
        }
        promise.handle(res);
      });
      futures.add(promise);
    }
    futures.all(all, fut);
  }

  void health(DeploymentDescriptor md,
                      Handler<ExtendedAsyncResult<HealthDescriptor>> fut) {

    HealthDescriptor hd = new HealthDescriptor();
    String url = md.getUrl();
    hd.setInstId(md.getInstId());
    hd.setSrvcId(md.getSrvcId());
    if (url == null || url.length() == 0) {
      hd.setHealthMessage("Unknown");
      hd.setHealthStatus(false);
      fut.handle(new Success<>(hd));
      return;
    }
    httpClient.get(new RequestOptions().setAbsoluteURI(url), res1 -> {
      if (res1.failed()) {
        hd.setHealthMessage("Fail: " + res1.cause().getMessage());
        hd.setHealthStatus(false);
        fut.handle(new Success<>(hd));
        return;
      }
      HttpClientResponse response = res1.result();
      response.endHandler(res -> {
        hd.setHealthMessage("OK");
        hd.setHealthStatus(true);
        fut.handle(new Success<>(hd));
      });
      response.exceptionHandler(res -> {
        hd.setHealthMessage("Fail: " + res.getMessage());
        hd.setHealthStatus(false);
        fut.handle(new Success<>(hd));
      });
    });
  }

  void health(Handler<ExtendedAsyncResult<List<HealthDescriptor>>> fut) {
    DiscoveryManager.this.get(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        healthList(res.result(), fut);
      }
    });
  }

  void health(String srvcId, String instId, Handler<ExtendedAsyncResult<HealthDescriptor>> fut) {
    DiscoveryManager.this.get(srvcId, instId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        health(res.result(), fut);
      }
    });
  }

  void health(String srvcId, Handler<ExtendedAsyncResult<List<HealthDescriptor>>> fut) {
    getNonEmpty(srvcId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        healthList(res.result(), fut);
      }
    });
  }

  void addNode(NodeDescriptor nd, Handler<ExtendedAsyncResult<Void>> fut) {
    if (clusterManager != null) {
      nd.setNodeId(clusterManager.getNodeId());
    }
    nodes.put(nd.getNodeId(), nd, fut);
  }

  /**
   * Translate node url or node name to its id. If not found, returns the id itself.
   *
   * @param nodeId node ID or URL
   * @return future with id
   */
  private Future<String> nodeUrl(String nodeId) {
    return getNodes().compose(result -> {
      for (NodeDescriptor nd : result) {
        if (nodeId.compareTo(nd.getUrl()) == 0) {
          return Future.succeededFuture(nd.getNodeId());
        }
        String nm = nd.getNodeName();
        if (nm != null && nodeId.compareTo(nm) == 0) {
          return Future.succeededFuture(nd.getNodeId());
        }
      }
      return Future.succeededFuture(nodeId); // try with the original id
    });
  }

  Future<NodeDescriptor> getNode(String nodeId) {
    return nodeUrl(nodeId).compose(x -> getNode1(x));
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

  void updateNode(String nodeId, NodeDescriptor nd,
                  Handler<ExtendedAsyncResult<NodeDescriptor>> fut) {
    if (clusterManager != null) {
      List<String> n = clusterManager.getNodes();
      if (!n.contains(nodeId)) {
        fut.handle(new Failure<>(ErrorType.NOT_FOUND, messages.getMessage("10806", nodeId)));
        return;
      }
    }
    nodes.get(nodeId, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        NodeDescriptor old = gres.result();
        if (!old.getNodeId().equals(nd.getNodeId()) || !nd.getNodeId().equals(nodeId)) {
          fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10807", nodeId)));
          return;
        }
        if (!old.getUrl().equals(nd.getUrl())) {
          fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10808", nodeId)));
          return;
        }
        nodes.put(nodeId, nd, pres -> {
          if (pres.failed()) {
            fut.handle(new Failure<>(pres.getType(), pres.cause()));
          } else {
            fut.handle(new Success<>(nd));
          }
        });
      }
    });
  }

  Future<List<NodeDescriptor>> getNodes() {
    return nodes.getKeys().compose(keys -> {
      if (clusterManager != null) {
        List<String> n = clusterManager.getNodes();
        keys.retainAll(n);
      }
      List<NodeDescriptor> nodes = new LinkedList<>();
      List<Future> futures = new LinkedList<>();
      for (String nodeId : keys) {
        futures.add(getNode1(nodeId).compose(x -> {
          nodes.add(x);
          return Future.succeededFuture();
        }));
      }
      return CompositeFuture.all(futures).compose(res -> {
        return Future.succeededFuture(nodes);
      });
    });
  }

  @Override
  public void nodeAdded(String nodeID) {
    logger.info("node.add {}", nodeID);
  }

  @Override
  public void nodeLeft(String nodeID) {
    nodes.remove(nodeID, res
        -> logger.info("node.remove {} result={}", nodeID, res.result())
    );
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
