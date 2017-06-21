package org.folio.okapi.discovery;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.HealthDescriptor;
import org.folio.okapi.bean.NodeDescriptor;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.service.ModuleManager;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.LockedTypedMap2;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.OkapiClient;

/**
 * Keeps track of which modules are running where.
 * Uses a shared map to list running modules on the different nodes.
 * Maps a SrvcId to a DeploymentDescriptor. Can also invoke deployment,
 * and record the result in its map.
 */
public class DiscoveryManager implements NodeListener {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  LockedTypedMap2<DeploymentDescriptor> deployments = new LockedTypedMap2<>(DeploymentDescriptor.class);
  LockedTypedMap1<NodeDescriptor> nodes = new LockedTypedMap1<>(NodeDescriptor.class);
  Vertx vertx;
  private ClusterManager clusterManager;
  private ModuleManager moduleManager;

  private final int delay = 10; // ms in recursing for retry of map
  private HttpClient httpClient;

  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    this.vertx = vertx;
    this.httpClient = vertx.createHttpClient();
    deployments.init(vertx, "discoveryList", res1 -> {
      if (res1.failed()) {
        fut.handle(new Failure<>(res1.getType(), res1.cause()));
      } else {
        nodes.init(vertx, "discoveryNodes", res2 -> {
          if (res2.failed()) {
            fut.handle(new Failure<>(res2.getType(), res2.cause()));
          } else {
            fut.handle(new Success<>());
          }
        });
      }
    });
  }

  public void setClusterManager(ClusterManager mgr) {
    this.clusterManager = mgr;
    mgr.nodeListener(this);
  }

  public void setModuleManager(ModuleManager mgr) {
    this.moduleManager = mgr;
  }

  public void add(DeploymentDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    final String srvcId = md.getSrvcId();
    if (srvcId == null) {
      fut.handle(new Failure<>(USER, "Needs srvc"));
      return;
    }
    final String instId = md.getInstId();
    if (instId == null) {
      fut.handle(new Failure<>(USER, "Needs instId"));
      return;
    }
    deployments.add(srvcId, instId, md, fut);
  }

  private void getNodeIdFromUrl(DeploymentDescriptor dd, Handler<ExtendedAsyncResult<String>> fut) {
    final String url = dd.getUrl();
    if (dd.getNodeId() == null && url != null) {
      this.getNodes(res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          List<NodeDescriptor> result = res.result();
          Iterator<NodeDescriptor> iterator = result.iterator();
          while (iterator.hasNext()) {
            NodeDescriptor next = iterator.next();
            if (next.getUrl().compareTo(url) == 0) {
              fut.handle(new Success<>(next.getNodeId()));
              return;
            }
          }
          fut.handle(new Failure<>(NOT_FOUND, url));
        }
      });
    } else {
      fut.handle(new Success<>(null));
    }
  }

  /**
   * Adds a service to the discovery, and optionally deploys it too.
   *
   *   1: We have LaunchDescriptor and NodeId: Deploy on that node.
   *   2: NodeId, but no LaunchDescriptor: Fetch the module, use its LaunchDescriptor, and deploy.
   *   3: No nodeId: Do not deploy at all, just record the existence (URL and instId) of the module.
   */
  public void addAndDeploy(DeploymentDescriptor dd,
    Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    logger.info("addAndDeploy: " + Json.encodePrettily(dd));
    final String srvcId = dd.getSrvcId();
    if (srvcId == null) {
      fut.handle(new Failure<>(USER, "Needs srvcId"));
      return;
    }
    LaunchDescriptor launchDesc = dd.getDescriptor();
    final String nodeId = dd.getNodeId();
    if (launchDesc == null && nodeId == null) { // 3: externally deployed
      final String instId = dd.getInstId();
      if (instId == null) {
        fut.handle(new Failure<>(USER, "Needs instId"));
        return;
      }
      deployments.add(srvcId, instId, dd, res -> { // just add it
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          fut.handle(new Success<>(dd));
        }
      });
    } else if (nodeId == null) {
      fut.handle(new Failure<>(USER, "missing nodeId"));
    } else {
      if (launchDesc == null) { // 2: get from module
        logger.debug("addAndDeploy: case 2 for " + dd.getSrvcId());
        if ( moduleManager == null) {
          fut.handle(new Failure<>(INTERNAL, "no module manager (should not happen)"));
          return;
        }
        String modId = dd.getSrvcId();
        if (modId == null || modId.isEmpty()) {
          fut.handle(new Failure<>(USER, "Needs srvcId"));
          return;
        }
        moduleManager.get(modId, gres -> {
          if (gres.failed()) {
            if (gres.getType() == NOT_FOUND) {
              fut.handle(new Failure<>(NOT_FOUND, "Module " + modId + " not found"));
            } else {
              fut.handle(new Failure<>(gres.getType(), gres.cause()));
            }
            return;
          }
          ModuleDescriptor md = gres.result();
          LaunchDescriptor modLaunchDesc = md.getLaunchDescriptor();
          if (modLaunchDesc == null) {
            fut.handle(new Failure<>(USER, "Module " + modId + " has no launchDescriptor"));
            return;
          }
          dd.setDescriptor(modLaunchDesc);
          launchit(nodeId, dd, fut);
          return;
        });
      } else { // Have a launchdesc already in dd
        logger.debug("addAndDeploy: case 1: We have a ld: " + Json.encode(dd));
        launchit(nodeId, dd, fut);
      }
    }
  }
  /**
   * Helper to actually launch (deploy) a module on a node.
   */
  private void launchit(String nodeId, DeploymentDescriptor dd,
    Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    logger.debug("launchit starting for " + Json.encode(dd));
    getNode(nodeId, noderes -> {
      if (noderes.failed()) {
        fut.handle(new Failure<>(noderes.getType(), noderes.cause()));
      } else {
        OkapiClient ok = new OkapiClient(noderes.result().getUrl(), vertx, null);
        String reqdata = Json.encode(dd);
        ok.post("/_/deployment/modules", reqdata, okres -> {
          if (okres.failed()) {
            fut.handle(new Failure<>(okres.getType(), okres.cause().getMessage()));
          } else {
            DeploymentDescriptor pmd = Json.decodeValue(okres.result(),
              DeploymentDescriptor.class);
            fut.handle(new Success<>(pmd));
          }
        });
      }
    });
  }

  public void removeAndUndeploy(String srvcId, String instId, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.info("removeAndUndeploy: srvcId " + srvcId + " instId " + instId);
    deployments.get(srvcId, instId, res -> {
      if (res.failed()) {
        logger.warn("deployment.get failed");
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        DeploymentDescriptor md = res.result();
        if (md.getDescriptor() == null) {
          remove(srvcId, instId, fut);
        } else {
          final String nodeId = md.getNodeId();
          getNode(nodeId, res1 -> {
            if (res1.failed()) {
              fut.handle(new Failure<>(res1.getType(), res1.cause()));
            } else {
              OkapiClient ok = new OkapiClient(res1.result().getUrl(), vertx, null);
              ok.delete("/_/deployment/modules/" + instId, okres -> {
                if ( okres.failed()) {
                  logger.warn("Dm: Failure: " + okres.getType() + " " + okres.cause().getMessage() );
                  fut.handle(new Failure<>(okres.getType(),okres.cause().getMessage()));
                } else {
                    fut.handle(new Success<>());
                }
              });
            }
          });
        }
      }
    });
  }

  public void remove(String srvcId, String instId, Handler<ExtendedAsyncResult<Void>> fut) {
    deployments.remove(srvcId, instId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  void get(String srvcId, String instId, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    deployments.get(srvcId, instId, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
      } else {
        DeploymentDescriptor md = resGet.result();
        String url = md.getUrl();
        if (url == null) {
          url = "";
        }
        // check that the node is alive, but only on non-url instances
        if (clusterManager != null && url.isEmpty()) {
          if (!clusterManager.getNodes().contains(md.getNodeId())) {
            fut.handle(new Failure<>(NOT_FOUND, "gone"));
            return;
          }
        }
        fut.handle(new Success<>(md));
      }
    });
  }

  /**
   * Get the list for one srvcId. May return an empty list.
   */
  public void get(String srvcId, Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    deployments.get(srvcId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        List<DeploymentDescriptor> result = res.result();
        Iterator<DeploymentDescriptor> it = result.iterator();
        while (it.hasNext()) {
          DeploymentDescriptor md = it.next();
          String url = md.getUrl();
          if (url == null) {
            url = "";
          }
          // remove instances that are on nodes that are not up
          // but not those that have an explicit URL
          if (clusterManager != null && url.isEmpty()) {
            List<String> n = clusterManager.getNodes();
            if (!clusterManager.getNodes().contains(md.getNodeId())) {
              it.remove();
            }
          }
        }
        fut.handle(new Success<>(result));
      }
    });
  }

  /**
   * Get all known DeploymentDescriptors (all services on all nodes).
   */
  public void get(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    deployments.getKeys(resGet -> {
      if (resGet.failed()) {
        logger.debug("DiscoveryManager:get all: " + resGet.getType().name());
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
      } else {
        Collection<String> keys = resGet.result();
        List<DeploymentDescriptor> all = new LinkedList<>();
        if (keys == null || keys.isEmpty()) {
          fut.handle(new Success<>(all));
        } else {
          Iterator<String> it = keys.iterator();
          getAll_r(it, all, fut);
        }
      }
    });
  }

  void getAll_r(Iterator<String> it, List<DeploymentDescriptor> all,
          Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(all));
    } else {
      String srvcId = it.next();
      this.get(srvcId, resGet -> {
        if (resGet.failed()) {
          fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
        } else {
          List<DeploymentDescriptor> dpl = resGet.result();
          all.addAll(dpl);
          getAll_r(it, all, fut);
        }
      });
    }
  }
  private void health(DeploymentDescriptor md, Handler<ExtendedAsyncResult<HealthDescriptor>> fut) {
    HealthDescriptor hd = new HealthDescriptor();
    String url = md.getUrl();
    hd.setInstId(md.getInstId());
    hd.setSrvcId(md.getSrvcId());
    if (url == null || url.length() == 0) {
      hd.setHealthMessage("Unknown");
      hd.setHealthStatus(false);
      fut.handle(new Success<>(hd));
    } else {
      HttpClientRequest req = httpClient.getAbs(url, res -> {
        res.endHandler(res1 -> {
          hd.setHealthMessage("OK");
          hd.setHealthStatus(true);
          fut.handle(new Success<>(hd));
        });
        res.exceptionHandler(res1 -> {
          hd.setHealthMessage("Fail: " + res1.getMessage());
          hd.setHealthStatus(false);
          fut.handle(new Success<>(hd));
        });
      });
      req.exceptionHandler(res -> {
        hd.setHealthMessage("Fail: " + res.getMessage());
        hd.setHealthStatus(false);
        fut.handle(new Success<>(hd));
      });
      req.end();
    }
  }

  private void healthR(Iterator<DeploymentDescriptor> it, List<HealthDescriptor> all,
          Handler<ExtendedAsyncResult<List<HealthDescriptor>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(all));
    } else {
      DeploymentDescriptor md = it.next();
      health(md, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          all.add(res.result());
          healthR(it, all, fut);
        }
      });
    }
  }

  public void health(Handler<ExtendedAsyncResult<List<HealthDescriptor>>> fut) {
    get(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        Iterator<DeploymentDescriptor> it = res.result().iterator();
        List<HealthDescriptor> all = new ArrayList<>();
        healthR(it, all, fut);
      }
    });
  }

  public void health(String srvcId, String instId, Handler<ExtendedAsyncResult<HealthDescriptor>> fut) {
    get(srvcId, instId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        health(res.result(), fut);
      }
    });
  }

  public void health(String srvcId, Handler<ExtendedAsyncResult<List<HealthDescriptor>>> fut) {
    get(srvcId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        Iterator<DeploymentDescriptor> it = res.result().iterator();
        List<HealthDescriptor> all = new ArrayList<>();
        healthR(it, all, fut);
      }
    });
  }

  public void addNode(NodeDescriptor nd, Handler<ExtendedAsyncResult<Void>> fut) {
    if (clusterManager != null) {
      nd.setNodeId(clusterManager.getNodeID());
    }
    nodes.put(nd.getNodeId(), nd, fut);
  }

  private void removeNode(NodeDescriptor nd, Handler<ExtendedAsyncResult<Boolean>> fut) {
    nodes.remove(nd.getNodeId(), fut);
  }

  void getNodes_r(Iterator<String> it, List<NodeDescriptor> all,
          Handler<ExtendedAsyncResult<List<NodeDescriptor>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(all));
    } else {
      String srvcId = it.next();
      getNode(srvcId, resGet -> {
        if (resGet.failed()) {
          fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
        } else {
          NodeDescriptor dpl = resGet.result();
          all.add(dpl);
          getNodes_r(it, all, fut);
        }
      });
    }
  }

  private void nodeUrl(String nodeId, Handler<ExtendedAsyncResult<String>> fut) {
    if (nodeId.startsWith("http://")) {
      getNodes(res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          List<NodeDescriptor> result = res.result();
          Iterator<NodeDescriptor> iterator = result.iterator();
          while (iterator.hasNext()) {
            NodeDescriptor next = iterator.next();
            if (nodeId.compareTo(next.getUrl()) == 0) {
              fut.handle(new Success<>(next.getNodeId()));
              return;
            }
          }
          fut.handle(new Failure<>(NOT_FOUND, nodeId));
        }
      });
    } else {
      fut.handle(new Success<>(nodeId));
    }
  }

  public void getNode(String nodeId, Handler<ExtendedAsyncResult<NodeDescriptor>> fut) {
    nodeUrl(nodeId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        getNode1(res.result(), fut);
      }
    });
  }

  private void getNode1(String nodeId, Handler<ExtendedAsyncResult<NodeDescriptor>> fut) {
    if (clusterManager != null) {
      List<String> n = clusterManager.getNodes();
      if (!n.contains(nodeId)) {
        fut.handle(new Failure<>(NOT_FOUND, nodeId));
        return;
      }
    }
    nodes.get(nodeId, fut);
  }

  public void getNodes(Handler<ExtendedAsyncResult<List<NodeDescriptor>>> fut) {
    nodes.getKeys(resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
      } else {
        Collection<String> keys = resGet.result();
        if (clusterManager != null) {
          List<String> n = clusterManager.getNodes();
          keys.retainAll(n);
        }
        List<NodeDescriptor> all = new LinkedList<>();
        if (keys == null || keys.isEmpty()) {
          fut.handle(new Success<>(all));
        } else {
          getNodes_r(keys.iterator(), all, fut);
        }
      }
    });
  }

  @Override
  public void nodeAdded(String nodeID) {
  }

  @Override
  public void nodeLeft(String nodeID) {
    nodes.remove(nodeID, res -> {
      logger.info("node.remove " + nodeID + " result=" + res.result());
    });
  }
}
