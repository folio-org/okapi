/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import io.vertx.core.Handler;
import okapi.bean.ModuleDescriptor;
import okapi.bean.ModuleInstance;
import okapi.bean.Modules;
import okapi.bean.Ports;
import okapi.bean.ProcessModuleHandle;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Set;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

/**
 * Manages the running modules, and the ports they listen on
 *
 */
public class ModuleManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final Modules modules;
  private final Ports ports;
  final private Vertx vertx;

  public ModuleManager(Vertx vertx, Modules modules, int port_start, int port_end) {
    this.vertx = vertx;
    this.ports = new Ports(port_start, port_end);
    this.modules = modules;
  }

  public void create(ModuleDescriptor md, Handler<ExtendedAsyncResult<String>> fut) {
    final String id = md.getId();
    String url;
    final int use_port = ports.get();
    int spawn_port = -1;
    ModuleInstance m = modules.get(id);
    if (m != null) {
      fut.handle(new Failure<>(USER, "Already deployed"));
      return;
    }
    if (md.getUrl() == null) {
      if (use_port == -1) {
        fut.handle(new Failure<>(USER, "module " + id
                + " can not be deployed: all ports in use"));
      }
      spawn_port = use_port;
      url = "http://localhost:" + use_port;
    } else {
      ports.free(use_port);
      url = md.getUrl();
    }
    if (md.getDescriptor() != null) {
      // enable it now so that activation for 2nd one will fail
      ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, md.getDescriptor(),
              spawn_port);
      modules.put(id, new ModuleInstance(md, pmh, url));

      pmh.start(future -> {
        if (future.succeeded()) {
          fut.handle(new Success<>(id));
        } else {
          modules.remove(md.getId());
          ports.free(use_port);
          fut.handle(new Failure<>(INTERNAL, future.cause()));
        }
      });
    } else {
      modules.put(id, new ModuleInstance(md, null, url));
      fut.handle(new Success<>(id));
    }
  }

  /**
   * Simplistic implementation of updating a module: Deletes it and inserts.
   *
   */
  public void update(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = md.getId();
    this.delete(id, dres -> {
      if (dres.failed()) {
        logger.warn("Update: Delete failed: " + dres.cause());
        fut.handle(new Failure<>(dres.getType(), dres.cause()));
      } else {
        this.create(md, cres -> {
          if (cres.failed()) {
            logger.warn("Update: create failed: " + dres.cause());
            fut.handle(new Failure<>(dres.getType(), dres.cause()));
          } else {
            fut.handle(new Success<>());
          }
        });
      }
    });
  }

  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    ModuleInstance m = modules.get(id);
    if (m == null) {
      fut.handle(new Failure<>(NOT_FOUND, "Can not delete " + id + ". Not found"));
    } else {
      ProcessModuleHandle pmh = m.getProcessModuleHandle();
      if (pmh == null) {
        logger.debug("Not running, just deleting " + m.getModuleDescriptor().getId());
        modules.remove(id); // nothing running, just remove it from our list
        fut.handle(new Success<>());
      } else {
        logger.debug("About to stop " + m.getModuleDescriptor().getId());
        pmh.stop(future -> {
          if (future.succeeded()) {
            logger.debug("Did stop " + m.getModuleDescriptor().getId());
            modules.remove(id);
            ports.free(pmh.getPort());
            fut.handle(new Success<>());
          } else {
            fut.handle(new Failure<>(INTERNAL, future.cause()));
            logger.warn("FAILED to stop " + m.getModuleDescriptor().getId());
            // TODO - What to do in case it was already dead? Probably safe to ignore
            // TODO - What to do in case stopping failed, and it still runs. That is bad!
          }
        });
      }
    }
  }

  public void deleteAll(Handler<ExtendedAsyncResult<Void>> fut) {
    Set<String> list = modules.list();
    if (list.isEmpty()) {
      fut.handle(new Success<Void>());
    } else {
      String id = list.iterator().next();
      ModuleInstance mi = modules.get(id);
      ProcessModuleHandle pmh = mi.getProcessModuleHandle();
      if (pmh == null) {
        modules.remove(id);
        logger.debug("Deleted module " + id);
        deleteAll(fut);
      } else {
        pmh.stop(res -> {
          if (res.succeeded()) {
            ports.free(pmh.getPort());
          } else {
            logger.warn("Failed to stop module " + id + ":" + res.cause().getMessage());
            fut.handle(new Failure<>(INTERNAL, "Failed to stop module " + id + ":" + res.cause().getMessage()));
            // TODO - What to do in this case? Declare the whole node dead?
          }
          modules.remove(id); // remove in any case
          logger.debug("Stopped and deleted module " + id);
          deleteAll(fut);
        });
      }
    }
  }

} // class
