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
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import okapi.bean.DeploymentDescriptor;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.LockedStringMap;
import okapi.util.Success;

public class DiscoveryManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  LockedStringMap list = new LockedStringMap();
  Vertx vertx;

  private final int delay = 10; // ms in recursing for retry of map
  private EventBus eb;
  private final String eventBusName = "okapi.conf.discovery";

  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    this.vertx = vertx;
    list.init(vertx, "discoveryList", fut);
    this.eb = vertx.eventBus();
    eb.consumer(eventBusName + ".deploy", message -> {
      final String s = (String) message.body();
      final DeploymentDescriptor md = Json.decodeValue(s,
              DeploymentDescriptor.class);
      add(md, res -> {
        if (res.failed()) {
          message.fail(0, res.cause().getMessage());
        }
      });
    });
    eb.consumer(eventBusName + ".undeploy", message -> {
      final String s = (String) message.body();
      final DeploymentDescriptor md = Json.decodeValue(s,
              DeploymentDescriptor.class);
      remove(md.getSrvcId(), md.getInstId(), res -> {
        if (res.failed()) {
          message.fail(0, res.cause().getMessage());
        }
      });
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
    String jsonVal = Json.encodePrettily(md);
    //logger.debug("Disc:add " + srvcId + "/" + instId + ": " + jsonVal);

    list.add(srvcId, instId, jsonVal, fut);
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
        String val = resGet.result();
        //logger.debug("Disc:get " + srvcId + "/" + instId + ": " + val);
        DeploymentDescriptor md = Json.decodeValue(val, DeploymentDescriptor.class);
        fut.handle(new Success<>(md));
      }
    });
  }

  /**
   * Get the list for one srvcId.
   * May return an empty list
   */
  public void get(String srvcId, Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    list.get(srvcId, resGet -> {
      if (resGet.failed()) { 
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
      } else {
        List<DeploymentDescriptor> dpl = new ArrayList<>();
        Collection<String> val = resGet.result();
        Iterator<String> it = val.iterator();
        while (it.hasNext()) {
          String t = it.next();
          //logger.debug("Disc:get " + srvcId + ":" + t);
          DeploymentDescriptor md = Json.decodeValue(t, DeploymentDescriptor.class);
          dpl.add(md);
        }
        fut.handle(new Success<>(dpl));
      }
    });
  }

  /**
   * Get all known DeploymentDescriptors (all services on all nodes).
   */
  public void get(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    list.getKeys(resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
      } else {
        Collection<String> keys = resGet.result();
        if (keys == null || keys.isEmpty()) {
          List<DeploymentDescriptor> empty = new ArrayList<>();
          fut.handle(new Success<>(empty));
        } else {
          Iterator<String> it = keys.iterator();
          List<DeploymentDescriptor> all = new ArrayList<>();
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

}
