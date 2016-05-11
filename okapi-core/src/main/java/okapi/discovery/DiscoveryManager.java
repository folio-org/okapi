/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.discovery;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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

  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    this.vertx = vertx;
    list.init(vertx, "discoveryList", fut);
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

    list.add(srvcId, instId, jsonVal, fut );
    // TODO - Add the key too
  }

  void remove(String srvcId, String instId, Handler<ExtendedAsyncResult<Void>> fut) {
    list.remove(srvcId, instId, res->{
      if (res.failed())
        fut.handle(new Failure<>(res.getType(),res.cause()));
      else {
        if ( res.result()) { // deleted the last one
          // TODO - Remove the key
        }
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
   * Get the list for one srvid.
   */
  public void get(String srvcId, Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    list.get(srvcId, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
      } else {
        List<DeploymentDescriptor> dpl = new ArrayList<>();
        Collection<String> val = resGet.result();
        Iterator<String> it = val.iterator();
        while(it.hasNext()) {
          String t = it.next();
          //logger.debug("Disc:get " + srvcId + ":" + t);
          DeploymentDescriptor md = Json.decodeValue(t, DeploymentDescriptor.class);
          dpl.add(md);
        }
        fut.handle(new Success<>(dpl));
        }
    });
  }

  void get(Handler<ExtendedAsyncResult<List<DeploymentDescriptor>>> fut) {
    fut.handle(new Failure<>(INTERNAL, "get not implemented"));
  }
}
