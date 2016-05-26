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
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import okapi.bean.DeploymentDescriptor;
import okapi.bean.HealthDescriptor;
import okapi.bean.NodeDescriptor;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.LockedStringMap;
import okapi.util.LockedTypedMap;
import okapi.util.Success;
import static okapi.util.OkapiEvents.*;

public class DiscoveryManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  LockedTypedMap<DeploymentDescriptor> list = new LockedTypedMap(DeploymentDescriptor.class);
  LockedStringMap nodes = new LockedStringMap();
  Vertx vertx;

  private final int delay = 10; // ms in recursing for retry of map
  private EventBus eb;
  private HttpClient httpClient;

  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    this.vertx = vertx;
    this.httpClient = vertx.createHttpClient();
    this.eb = vertx.eventBus();
    eb.consumer(DEPLOYMENT_DEPLOY.toString(), message -> {
      final String s = (String) message.body();
      final DeploymentDescriptor md = Json.decodeValue(s,
              DeploymentDescriptor.class);
      add(md, res -> {
        if (res.failed()) {
          message.fail(0, res.cause().getMessage());
        }
      });
    });
    eb.consumer(DEPLOYMENT_UNDEPLOY.toString(), message -> {
      final String s = (String) message.body();
      final DeploymentDescriptor md = Json.decodeValue(s,
              DeploymentDescriptor.class);
      remove(md.getSrvcId(), md.getInstId(), res -> {
        if (res.failed()) {
          message.fail(0, res.cause().getMessage());
        }
      });
    });
    eb.consumer(DEPLOYMENT_NODE_START.toString(), message -> {
      final String host = (String) message.body();
      nodes.add(host, "x", "x", res -> {
        if (res.failed()) {
          message.fail(0, res.cause().getMessage());
        } else {
          message.reply("OK");
        }
      });
    });
    eb.consumer(DEPLOYMENT_NODE_STOP.toString(), message -> {
      final String host = (String) message.body();
      nodes.remove(host, "x", res -> {
        if (res.failed()) {
          message.fail(0, res.cause().getMessage());
        }
      });
    });
    list.init(vertx, "discoveryList", res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        nodes.init(vertx, "discoveryNodes", fut);
      }
    });
  }

  void add(DeploymentDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
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
    list.add(srvcId, instId, md, fut);
  }

  void remove(String srvcId, String instId, Handler<ExtendedAsyncResult<Void>> fut) {
    list.remove(srvcId, instId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  void get(String srvcId, String instId, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    list.get(srvcId, instId, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
      } else {
        fut.handle(new Success<>(resGet.result()));
      }
    });
  }

  /**
   * Get the list for one srvcId. May return an empty list
   */
  public void get(String srvcId, Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    list.get(srvcId, fut);
  }

  /**
   * Get all known DeploymentDescriptors (all services on all nodes).
   */
  public void get(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    list.getKeys(resGet -> {
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
      fut.handle(new Success(hd));
    } else {
      HttpClientRequest req = httpClient.getAbs(url, res -> {
        res.endHandler(res1 -> {
          hd.setHealthMessage("OK");
          hd.setHealthStatus(true);
          fut.handle(new Success(hd));
        });
        res.exceptionHandler(res1 -> {
          hd.setHealthMessage("Fail: " + res1.getMessage());
          hd.setHealthStatus(false);
          fut.handle(new Success(hd));
        });
      });
      req.exceptionHandler(res -> {
        hd.setHealthMessage("Fail: " + res.getMessage());
        hd.setHealthStatus(false);
        fut.handle(new Success(hd));
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
}
