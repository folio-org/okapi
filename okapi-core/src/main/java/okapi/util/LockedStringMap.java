/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okapi.bean.DeploymentDescriptor;
import okapi.discovery.DiscoveryManager;
import static okapi.util.ErrorType.INTERNAL;
import static okapi.util.ErrorType.NOT_FOUND;
import static okapi.util.ErrorType.USER;

/**
 * A shared map with extra features like locking and listing of keys.
 * @author heikki
 */

public class LockedStringMap {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  static class Vlist{
    @JsonProperty
    Map<String,String> mdlist = new LinkedHashMap<>();
  }

  AsyncMap<String, String> list = null;
  Vertx vertx = null;

  private final int delay = 10; // ms in recursing for retry of map

  public void init(Vertx vertx, String mapName, Handler<ExtendedAsyncResult<Void>> fut) {
    this.vertx = vertx;
    AsyncMapFactory.<String, String>create(vertx, mapName, res -> {
      if (res.succeeded()) {
        this.list = res.result();
        fut.handle(new Success<>());
      } else {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  public void get(String k, String k2, Handler<ExtendedAsyncResult<String>> fut) {
    Vlist dpl = new Vlist();
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        logger.warn("Get " + k + "/" + k2 + ":" + val);
        if (val != null) {
          Vlist oldlist = Json.decodeValue(val, Vlist.class);
          dpl.mdlist.putAll(oldlist.mdlist);
          if (dpl.mdlist.containsKey(k2) ) {
            fut.handle(new Success<>(dpl.mdlist.get(k2)));
            return;
          }
        }
        fut.handle(new Failure<>(NOT_FOUND, k + "/" + k2));
        return;
      }
    });
  }

  public void get(String k, Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        logger.warn("Get " + k + ":" + val);
        if (val != null) {
          Vlist oldlist = Json.decodeValue(val, Vlist.class);
          Iterator<String> it = oldlist.mdlist.keySet().iterator();
          fut.handle(new Success<>(oldlist.mdlist.values()));
        } else {
          fut.handle(new Failure<>(NOT_FOUND, k));
        }
      }
    });
  }

  public void add(String k, String k2, String md, Handler<ExtendedAsyncResult<Void>> fut) {
    Vlist dpl = new Vlist();
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if (val != null) {
          Vlist oldlist = Json.decodeValue(val, Vlist.class);
          dpl.mdlist.putAll(oldlist.mdlist);
        }
        if (dpl.mdlist.containsKey(k2) ) {
          fut.handle(new Failure<>(USER, "Duplicate instance " + k2));
          return; // TODO - is this an error at all? Probably yes, should not happen with Discovery
        }
        dpl.mdlist.put(k2,md);
        String newVal = Json.encodePrettily(dpl);
        logger.warn("Add: to " + k + ":" + newVal);
        if (val == null) { // new entry
          list.putIfAbsent(k, newVal, resPut -> {
            if (resPut.succeeded()) {
              if (resPut.result() == null) {
                fut.handle(new Success<>());
              } else { // Someone messed with it, try again
                vertx.setTimer(delay, res->{
                  add(k, k2, md, fut);
                });
              }
            } else {
              fut.handle(new Failure<>(INTERNAL, resPut.cause()));
            }
          });
        } else { // existing entry, put and retry if someone else messed with it
          list.replaceIfPresent(k, val, newVal, resRepl -> {
            if (resRepl.succeeded()) {
              if (resRepl.result()) {
                fut.handle(new Success<>());
              } else {
                vertx.setTimer(delay, res->{
                  add(k, k2, md, fut);
                });
              }
            } else {
              fut.handle(new Failure<>(INTERNAL, resRepl.cause()));
            }
          });
        }
      } // get success
    });
  }

public void remove(String k, String k2, Handler<ExtendedAsyncResult<Boolean>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if (val == null) {
          fut.handle(new Failure<>(NOT_FOUND, k));
          return;
        }
        Vlist dpl = Json.decodeValue(val, Vlist.class);
        if (!dpl.mdlist.containsKey(k2)) {
          fut.handle(new Failure<>(NOT_FOUND, k + "/" + k2));
        } else {
          dpl.mdlist.remove(k2);
          if (dpl.mdlist.isEmpty()) {
            list.removeIfPresent(k, val, resDel -> {
              if (resDel.succeeded()) {
                if (resDel.result()) {
                  fut.handle(new Success<>(true));
                } else {
                  vertx.setTimer(delay, res -> {
                    remove(k, k2, fut);
                  });
                }
              } else {
                fut.handle(new Failure<>(INTERNAL, resDel.cause()));
              }
            });
          } else { // list was not empty, remove value
            String newVal = Json.encodePrettily(dpl);
            list.replaceIfPresent(k, val, newVal, resPut -> {
              if (resPut.succeeded()) {
                if (resPut.result()) {
                  fut.handle(new Success<>(false));
                } else {
                  vertx.setTimer(delay, res -> {
                    remove(k, k2, fut);
                  });
                }
              } else {
                fut.handle(new Failure<>(INTERNAL, resPut.cause()));
              }
            });
          }
        }
      }
    });
  }

}
