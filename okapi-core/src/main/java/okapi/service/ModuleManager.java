/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import io.vertx.core.Handler;
import okapi.bean.ModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.Set;
import okapi.bean.ModuleInterface;
import okapi.util.DropwizardHelper;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

public class ModuleManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  final private Vertx vertx;

  LinkedHashMap<String, ModuleDescriptor> modules = new LinkedHashMap<>();


  public ModuleManager(Vertx vertx) {
    String metricKey = "modules.count";
    DropwizardHelper.registerGauge(metricKey, () -> modules.size());
    this.vertx = vertx;
  }

  private boolean checkOneDependency(ModuleDescriptor md, ModuleInterface req,
      Handler<ExtendedAsyncResult<String>> fut){
    ModuleInterface seenversion = null;
    for (String runningmodule : modules.keySet()) {
      ModuleDescriptor rm = modules.get(runningmodule);
      ModuleInterface[] provides = rm.getProvides();
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
    if (modules.containsKey(id)) {
      fut.handle(new Failure<>(USER, "create: module already exist"));
      return;
    }
    if (!checkDependencies(md, fut)) {
      return; // The fail has already been fired into fut.
    }
    modules.put(id, md);
    fut.handle(new Success<>(id));
  }

  public void update(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = md.getId();
    if (!modules.containsKey(id)) {
      fut.handle(new Failure<>(NOT_FOUND, "update: module does not exist"));
      return;
    }
    modules.replace(id, md);
    fut.handle(new Success<>());
  }

  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!modules.containsKey(id)) {
      fut.handle(new Failure<>(NOT_FOUND, "delete: module does not exist"));
      return;
    }
    modules.remove(id);
    fut.handle(new Success<>());
  }

  public void deleteAll(Handler<ExtendedAsyncResult<Void>> fut) {
    modules.clear();
    fut.handle(new Success<>());
  }

  public ModuleDescriptor get(String name) {
    return modules.getOrDefault(name, null);
  }

  public Set<String> list() {
    return modules.keySet();
  }


} // class
