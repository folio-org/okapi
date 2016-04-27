/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.discovery;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import okapi.bean.DeploymentDescriptor;
import okapi.util.AsyncLocalmap;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

public class DiscoveryManager {

  static class DeploymentList {
    @JsonProperty
    List<DeploymentDescriptor> mdlist = new ArrayList<>();
  }

  LinkedHashMap<String, List<DeploymentDescriptor>> oldlist = new LinkedHashMap<>();
  AsyncMap<String, String> list = null;

  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    if (vertx.isClustered()) {
      SharedData shared = vertx.sharedData();
      shared.<String, String>getClusterWideMap("discoveryList", res -> {
        if (res.succeeded()) {
          this.list = res.result();
          fut.handle(new Success<>());
        } else {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        }
      });
    } else {
      this.list = new AsyncLocalmap<>(vertx);
      fut.handle(new Success<>());
    }
  }

  void add(DeploymentDescriptor md, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    final String id = md.getId();
    DeploymentList dpl = new DeploymentList();
    list.get(id, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if (val != null) {
          dpl.mdlist.addAll(Json.decodeValue(val, DeploymentList.class).mdlist);
        }
        Iterator<DeploymentDescriptor> it = dpl.mdlist.iterator();
        while (it.hasNext()) {
          DeploymentDescriptor mdi = it.next();
          if (mdi.getNodeId().equals(md.getNodeId())) {
            fut.handle(new Failure<>(USER, "Duplicate node " +md.getNodeId()));
            return;
          }
        }
        dpl.mdlist.add(md);
        String newVal = Json.encodePrettily(dpl);
        list.put(id, newVal, resPut -> {
          if (resPut.succeeded()) {
            fut.handle(new Success<>(md));
          } else {
            fut.handle(new Failure<>(INTERNAL, resPut.cause()));
          }
        });
      }
    });
  }

  void remove(String id, String nodeId, Handler<ExtendedAsyncResult<Void>> fut) {
    list.get(id, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if ( val == null ) {
          fut.handle(new Failure<>(NOT_FOUND, id));
          return;
        }
        DeploymentList dpl = Json.decodeValue(val, DeploymentList.class );
        Iterator<DeploymentDescriptor> it = dpl.mdlist.iterator();
        while (it.hasNext()) {
          DeploymentDescriptor md = it.next();
          if (md.getNodeId().equals(nodeId)) {
            it.remove();
            if (dpl.mdlist.isEmpty()) {
             list.remove(id, resDel->{
                if (resDel.succeeded()) {
                  fut.handle(new Success<>());
                } else {
                  fut.handle(new Failure<>(INTERNAL, resDel.cause()));
                }

             });
            } else {
              String newVal = Json.encodePrettily(dpl);
              list.put(id, newVal, resPut -> {
                if (resPut.succeeded()) {
                  fut.handle(new Success<>());
                } else {
                  fut.handle(new Failure<>(INTERNAL, resPut.cause()));
                }
              });
            }
            return;
          }
        }
        fut.handle(new Failure<>(NOT_FOUND, nodeId));
      }
    });
  }

  void get(String id, String nodeId, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    list.get(id, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(NOT_FOUND, id));
      } else {
        String val = resGet.result();
        DeploymentList dpl = Json.decodeValue(val, DeploymentList.class );
        Iterator<DeploymentDescriptor> it = dpl.mdlist.iterator();
        while (it.hasNext()) {
          DeploymentDescriptor md = it.next();
          if (md.getNodeId().equals(nodeId)) {
            fut.handle(new Success<>(md));
            return;
          }
        }
        fut.handle(new Failure<>(NOT_FOUND, nodeId));
      }
    });
  }

  void get(String id, Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    list.get(id, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if ( val == null ) {
          fut.handle(new Failure<>(NOT_FOUND, id));
        } else {
          DeploymentList dpl = Json.decodeValue(val, DeploymentList.class );
          fut.handle(new Success<>(dpl.mdlist));
        }
      }
    });
  }

  void get(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    fut.handle(new Failure<>(INTERNAL, "get not implemented"));
  }
}
