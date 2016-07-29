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
package okapi.discovery;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
import okapi.bean.DeploymentDescriptor;
import okapi.bean.HealthDescriptor;
import okapi.bean.NodeDescriptor;
import okapi.bean.LaunchDescriptor;
import okapi.bean.ModuleDescriptor;
import okapi.service.ModuleManager;
import static okapi.common.ErrorType.*;
import okapi.common.ExtendedAsyncResult;
import okapi.common.Failure;
import okapi.util.LockedTypedMap;
import okapi.common.Success;

/**
 * Keeps track of which modules are running where.
 * Uses a shared map to list running modules on the different nodes.
 * Maps a SrvcId to a DeploymentDescpriptor. Can also invoke deployment
 * and record the result in its map.
 */
public class DiscoveryManager implements NodeListener {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  LockedTypedMap<DeploymentDescriptor> deployments = new LockedTypedMap<>(DeploymentDescriptor.class);
  LockedTypedMap<NodeDescriptor> nodes = new LockedTypedMap<>(NodeDescriptor.class);
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

  /**
   * Adds a service to the discovery, and optionally deploys it too.
   * Three cases: (TODO - this is not how it is implemented yet!)
   *
   *   1: We have launchDescriptor and NodeId: Deploy on that node.
   *   2: NodeId, but no launchDescriptor: Fetch the module, use its launchdesc, and deploy.
   *   3: No nodeId: Do not deploy at all, just record the existence (URL and instId) of the module.
   */
  public void addAndDeploy(DeploymentDescriptor dd, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    logger.info("addAndDeploy: " + Json.encodePrettily(dd));
    final String srvcId = dd.getSrvcId();
    if (srvcId == null) {
      fut.handle(new Failure<>(USER, "Needs srvcId"));
      return;
    }
    LaunchDescriptor launchDesc = dd.getDescriptor();
    final String nodeId = dd.getNodeId();
    if (launchDesc == null && nodeId == null) { // 3:already deployed
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
      if ( launchDesc == null ) { // 2: get module
        if ( moduleManager == null) {
          fut.handle(new Failure<>(INTERNAL, "no module manager (should not happen)"));
          return;
        }
        String modId = dd.getSrvcId();
        if (modId == null || modId.isEmpty()) {
          fut.handle(new Failure<>(USER, "Needs srvcId"));
          return;
        }
        ModuleDescriptor md = moduleManager.get(modId);
        if (md == null) {
          fut.handle(new Failure<>(NOT_FOUND, "Module " + modId + " not found"));
          return;
        }
        launchDesc = md.getLaunchDescriptor();
        if (launchDesc == null) {
          fut.handle(new Failure<>(USER, "Module " + modId + " has no launchDescriptor"));
          return;
        }
        dd.setDescriptor(launchDesc);
      }
      getNode(nodeId, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          String url = res.result().getUrl() + "/_/deployment/modules";
          HttpClientRequest req = httpClient.postAbs(url, res2 -> {
            final Buffer buf = Buffer.buffer();
            res2.handler(b -> {
              buf.appendBuffer(b);
            });
            res2.endHandler(e -> {
              if (res2.statusCode() == 201) {
                DeploymentDescriptor pmd = Json.decodeValue(buf.toString(),
                        DeploymentDescriptor.class);
                fut.handle(new Success<>(pmd));
              } else if (res2.statusCode() == 404) {
                fut.handle(new Failure<>(NOT_FOUND, res2.statusMessage()));
              } else if (res2.statusCode() == 400) {
                fut.handle(new Failure<>(USER, res2.statusMessage()));
              } else {
                fut.handle(new Failure<>(INTERNAL, res2.statusMessage()));
              }
            });
          });
          req.exceptionHandler(x -> {
            fut.handle(new Failure<>(INTERNAL, x.getMessage()));
          });
          req.end(Json.encode(dd));
        }
      });
    }
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
              String url = res1.result().getUrl() + "/_/deployment/modules/" + instId;
              HttpClientRequest req = httpClient.deleteAbs(url, res2 -> {
                res2.endHandler(x -> {
                  if (res2.statusCode() == 204) {
                    fut.handle(new Success<>());
                  } else if (res2.statusCode() == 404) {
                    fut.handle(new Failure<>(NOT_FOUND, url));
                  } else {
                    fut.handle(new Failure<>(INTERNAL, url));
                  }
                });
              });
              req.exceptionHandler(x -> {
                fut.handle(new Failure<>(INTERNAL, x.getMessage()));
              });
              req.end();
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
        if (clusterManager != null) {
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
   * Get the list for one srvcId. May return an empty list
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
          if (clusterManager != null) {
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
        logger.warn("DiscoveryManager:get all: " + resGet.getType().name());
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
    nodes.put(nd.getNodeId(), "a", nd, fut);
  }

  private void removeNode(NodeDescriptor nd, Handler<ExtendedAsyncResult<Boolean>> fut) {
    nodes.remove(nd.getNodeId(), "a", fut);
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

  public void getNode(String nodeId, Handler<ExtendedAsyncResult<NodeDescriptor>> fut) {
    if (clusterManager != null) {
      List<String> n = clusterManager.getNodes();
      if (!n.contains(nodeId)) {
        fut.handle(new Failure<>(NOT_FOUND, nodeId));
        return;
      }
    }
    nodes.get(nodeId, "a", fut);
  }

  public void getNodes(Handler<ExtendedAsyncResult<List<NodeDescriptor>>> fut) {
    nodes.getKeys(resGet -> {
      if (resGet.failed()) {
        logger.warn("DiscoveryManager:get all: " + resGet.getType().name());
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
    nodes.remove(nodeID, "a", res -> {
      logger.info("node.remove " + nodeID + " result=" + res.result());
    });
  }
}
