/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.discovery;

import io.vertx.core.Handler;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import okapi.bean.DeploymentDescriptor;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

public class DiscoveryManager {
  LinkedHashMap<String, List<DeploymentDescriptor>> list = new LinkedHashMap<>();

  void add(DeploymentDescriptor md, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    final String id = md.getId();
    if (!list.containsKey(id)) {
      list.put(id, new LinkedList<>());
    }
    list.get(id).add(md);
    fut.handle(new Success<>(md));
  }

  void remove(String id, String nodeId, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!list.containsKey(id)) {
      fut.handle(new Failure(NOT_FOUND, "id"));
      return;
    }
    Iterator<DeploymentDescriptor> it = list.get(id).iterator();
    while (it.hasNext()) {
       DeploymentDescriptor md = it.next();
       if (md.getNodeId().equals(nodeId)) {
         fut.handle(new Success());
         return;
       }
    }
    fut.handle(new Failure(NOT_FOUND, "nodeId"));
  }

  void get(String id, String nodeId, Handler<ExtendedAsyncResult<DeploymentDescriptor>> fut) {
    if (!list.containsKey(id)) {
      fut.handle(new Failure(NOT_FOUND, "id"));
      return;
    }
    Iterator<DeploymentDescriptor> it = list.get(id).iterator();
    while (it.hasNext()) {
       DeploymentDescriptor md = it.next();
       if (md.getNodeId().equals(nodeId)) {
         fut.handle(new Success(md));
         return;
       }
    }
    fut.handle(new Failure(NOT_FOUND, "nodeId"));
  }

  void get(String id, Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    if (!list.containsKey(id)) {
      fut.handle(new Failure(NOT_FOUND, "id"));
      return;
    }
    fut.handle(new Success<>(list.get(id)));
  }

  void get(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    List<DeploymentDescriptor> ml = new LinkedList<>();
    for (String id : list.keySet()) {
      ml.addAll(list.get(id));
    }
    fut.handle(new Success<>(ml));
  }
}
