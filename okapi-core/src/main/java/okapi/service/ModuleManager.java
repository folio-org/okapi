/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import okapi.bean.ModuleDescriptor;
import okapi.bean.ModuleInstance;
import okapi.bean.Modules;
import okapi.bean.Ports;
import okapi.bean.ProcessModuleHandle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import okapi.bean.HealthModule;
import okapi.bean.ModuleInterface;
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

  public ModuleManager(Vertx vertx, Modules modules, Ports ports) {
    this.vertx = vertx;
    this.ports = ports;
    this.modules = modules;
  }

  private void spawn(ModuleDescriptor md, Handler<ExtendedAsyncResult<ModuleInstance>> fut) {
    int use_port = -1;
    String url = md.getUrl();
    if (url == null) {
      use_port = ports.get();
      if (use_port == -1) {
        fut.handle(new Failure<>(INTERNAL, "all ports in use"));
        return;
      }
      url = "http://localhost:" + use_port;
    }
    if (md.getDescriptor() != null) {
      ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, md.getDescriptor(),
              ports, use_port);
      ModuleInstance mi = new ModuleInstance(md, pmh, url, use_port);
      pmh.start(future -> {
        if (future.succeeded()) {
          fut.handle(new Success<>(mi));
        } else {
          ports.free(mi.getPort());
          fut.handle(new Failure<>(INTERNAL, future.cause()));
        }
      });
    } else {
      ModuleInstance mi = new ModuleInstance(md, null, url, use_port);
      fut.handle(new Success<>(mi));
    } 
  }


  private boolean checkOneDependency(ModuleDescriptor md, ModuleInterface req,
      Handler<ExtendedAsyncResult<String>> fut){
    ModuleInterface seenversion = null;
    for ( String runningmodule : modules.list() ) {
      ModuleInstance rm = modules.get(runningmodule);
      ModuleInterface[] provides = rm.getModuleDescriptor().getProvides();
      if ( provides != null ) {
        for ( ModuleInterface pi: provides ) {
          logger.debug("Checking dependency of " + md.getId() + ": "
              + req.getId() + " " + req.getVersion()
              + " against " + pi.getId() + " " + pi.getVersion() );
          if ( req.getId().equals(pi.getId())) {
            if ( seenversion == null || pi.compare(req) > 0)
              seenversion = pi;
            if ( pi.isCompatible(req))
              return true;
          }
        }
      }
    }
    if (  seenversion == null ) {
      logger.debug("Can not create module '" + md.getId() + "'"
        +", missing dependency " + req.getId() + ": " + req.getVersion() );
      fut.handle(new Failure<>(USER, "Can not create module '" + md.getId() + "'. "
        + "Missing dependency: " +  req.getId() + ": " + req.getVersion()));
    } else {
      logger.debug("Can not create module '" + md.getId() + "'"
        + "Insufficient version for " + req.getId() + ". "
        + "Need " + req.getVersion() + ". have " + seenversion.getVersion() );
      fut.handle(new Failure<>(USER, "Can not create module '" + md.getId() + "'"
        + "Insufficient version for " + req.getId() + ". "
        + "Need " + req.getVersion() + ". have " + seenversion.getVersion()));
    }
    return false;
  }

  /**
   * Check that the dependencies are satisfied.
   * @param md Module to be created
   * @param fut to be called in case of failure
   * @return true if no problems
   */
  private boolean checkDependencies(ModuleDescriptor md,
        Handler<ExtendedAsyncResult<String>> fut) {
    ModuleInterface[] requires = md.getRequires();
    if (requires != null) {
      for (ModuleInterface req : requires) {
        if ( ! checkOneDependency(md, req, fut)) {
          return false;
        }
      }
    }
    return true;
  }

  public void create(ModuleDescriptor md, Handler<ExtendedAsyncResult<String>> fut) {
    final String id = md.getId();
    if (modules.get(id) != null) {
      fut.handle(new Failure<>(USER, "create: module already exist"));
      return;
    }
    if ( !checkDependencies(md, fut))
      return; // The fail has already been fired into fut.
    
    spawn(md, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), "module " + id + ":" + res.cause().getMessage()));
      } else {
        ModuleInstance mi = res.result();
        modules.put(id, mi);
        fut.handle(new Success<>(id));
      }
    });
  }

  public void update(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = md.getId();
    ModuleInstance prev_m = modules.get(id);
    if (prev_m == null) {
      fut.handle(new Failure<>(USER, "update: module does not exist"));
      return;
    }
    spawn(md, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        modules.put(id, res.result());
        delete(prev_m, dres -> {
          if (dres.failed()) {
            fut.handle(new Failure(dres.getType(), "Update: " + dres.cause().getMessage()));
          } else {
            fut.handle(new Success<>());
          }
        });
      }
    });
  }

  public void healthR(Iterator<String> it, List<HealthModule> ml, Handler<AsyncResult<List<HealthModule>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(ml));
    } else {
      String id = it.next();
      HealthModule hm = new HealthModule();
      hm.setId(id);
      ml.add(hm);

      ModuleInstance m = modules.get(id);
      if (m == null) {
        hm.setStatus("Not Found");
        healthR(it, ml, fut);
      } else {
        HttpClient c = vertx.createHttpClient();
        HttpClientRequest c_req = c.getAbs(m.getUrl(), res -> {
          res.endHandler(x -> {
            hm.setStatus("OK");
            healthR(it, ml, fut);
          });
          res.exceptionHandler(x -> {
            hm.setStatus("FAIL");
            healthR(it, ml, fut);
          });
        });
        c_req.exceptionHandler(x -> {
          hm.setStatus("FAIL");
          healthR(it, ml, fut);
        });
        c_req.end();
      }
    }
  }

  public void health(Handler<AsyncResult<List<HealthModule>>> fut) {
    Iterator<String> it = modules.list().iterator();
    List<HealthModule> ml = new ArrayList<>();
    healthR(it, ml, fut);
  }

  public void health(String id, Handler<AsyncResult<List<HealthModule>>> fut) {
    Set<String> list = new HashSet<String>();
    list.add(id);
    List<HealthModule> ml = new ArrayList<>();
    healthR(list.iterator(), ml, fut);
  }

  private void delete(ModuleInstance m, Handler<ExtendedAsyncResult<Void>> fut) {
    ProcessModuleHandle pmh = m.getProcessModuleHandle();
    if (pmh == null) {
      logger.debug("Not running, just deleting " + m.getModuleDescriptor().getId());
      fut.handle(new Success<>());
    } else {
      logger.debug("About to stop " + m.getModuleDescriptor().getId());
      pmh.stop(future -> {
        if (future.succeeded()) {
          logger.debug("Did stop " + m.getModuleDescriptor().getId());
          fut.handle(new Success<>());
        } else {
          fut.handle(new Failure<>(INTERNAL, future.cause()));
          logger.warn("FAILED to stop " + m.getModuleDescriptor().getId());
        }
      });
    }
  }

  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    ModuleInstance m = modules.get(id);
    if (m == null) {
      fut.handle(new Failure<>(NOT_FOUND, "Can not delete " + id + ". Not found"));
    } else {
      delete(m, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          modules.remove(id);
          fut.handle(new Success<>());
        }
      });
    }
  }

  public void deleteAll(Handler<ExtendedAsyncResult<Void>> fut) {
    Set<String> list = modules.list();
    if (list.isEmpty()) {
      fut.handle(new Success<Void>());
    } else {
      String id = list.iterator().next();
      ModuleInstance mi = modules.get(id);

      delete(mi, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(INTERNAL, "Failed to stop module " + id + ":" + res.cause().getMessage()));
        } else {
          modules.remove(id);
          deleteAll(fut);
        }
      });
    }
  }

} // class
