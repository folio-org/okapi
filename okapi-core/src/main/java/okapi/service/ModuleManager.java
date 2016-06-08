/*
 * Copyright (C) 2015 Index Data
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
package okapi.service;

import io.vertx.core.Handler;
import okapi.bean.ModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.Set;
import okapi.bean.ModuleInterface;
import okapi.bean.RoutingEntry;
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

  /**
   * Check one dependency.
   * @param md module to check
   * @param req required dependency
   * @param modlist the list to check against
   * @return "" if ok, or error message
   */
  private String checkOneDependency(ModuleDescriptor md, ModuleInterface req,
       LinkedHashMap<String, ModuleDescriptor> modlist) {
    ModuleInterface seenversion = null;
    for (String runningmodule : modlist.keySet()) {
      ModuleDescriptor rm = modlist.get(runningmodule);
      ModuleInterface[] provides = rm.getProvides();
      if (provides != null) {
        for (ModuleInterface pi : provides) {
          logger.debug("Checking dependency of " + md.getId() + ": "
                  + req.getId() + " " + req.getVersion()
                  + " against " + pi.getId() + " " + pi.getVersion());
          if (req.getId().equals(pi.getId())) {
            if (seenversion == null || pi.compare(req) > 0) {
              seenversion = pi;
            }
            if (pi.isCompatible(req)) {
              logger.debug("Dependency OK");
              return "";  // ok
            }
          }
        }
      }
    }
    if (seenversion == null) {
      logger.debug("Can not create module '" + md.getId() + "'"
              + ", missing dependency " + req.getId() + ": " + req.getVersion());
      //fut.handle(new Failure<>(USER, "Can not create module '" + md.getId() + "'. "
      return "Missing dependency: " + md.getId()
        + " requires " + req.getId() + ": " + req.getVersion();
    } else {
      logger.debug("Can not create module '" + md.getId() + "'"
              + "Insufficient version for " + req.getId() + ". "
              + "Need " + req.getVersion() + ". have " + seenversion.getVersion());
      return "Insufficient version for " + req.getId() + ". "
        + "Need " + req.getVersion() + ". have " + seenversion.getVersion();
    }
  }

  /**
   * Check that the dependencies are satisfied.
   *
   * @param md Module to be checked
   * @return "" if no problems, or an erro rmessage
   */
  private String checkDependencies(ModuleDescriptor md,
        LinkedHashMap<String, ModuleDescriptor> modlist) {
    logger.debug("Checking dependencies of " + md.getId());
    ModuleInterface[] requires = md.getRequires();
    if (requires != null) {
      for (ModuleInterface req : requires) {
        String res = checkOneDependency(md, req, modlist);
        if ( !res.isEmpty())
          return res;
      }
    }
    return "";  // ok
  }

  /**
   * Check that all dependencies are satisfied.
   * Usually called with a copy of the modules list, after making some change.
   * @param modlist list to check
   * @return true if no problems
   */
  private String checkAllDependencies(LinkedHashMap<String, ModuleDescriptor> modlist){
    for ( ModuleDescriptor md : modlist.values() ) {
      String res = checkDependencies(md, modlist);
      if ( !res.isEmpty())
        return res;
    }
    return "";
  }

  public void create(ModuleDescriptor md, Handler<ExtendedAsyncResult<String>> fut) {
    final String id = md.getId();
    logger.debug("Creating module " + id);
    if (modules.containsKey(id)) {
      fut.handle(new Failure<>(USER, "create: module " + id + " exists already"));
      return;
    }
    LinkedHashMap<String, ModuleDescriptor> tempList = new LinkedHashMap<>(modules);
    tempList.put(id, md);

    String res = checkAllDependencies(tempList);
    if ( ! res.isEmpty()) {
      fut.handle(new Failure<>(USER, "create: module " + id + ": " + res));
      return;
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
    logger.debug("Deleting module " + id);

    if (!modules.containsKey(id)) {
      fut.handle(new Failure<>(NOT_FOUND, "delete: module does not exist"));
      return;
    }

    LinkedHashMap<String, ModuleDescriptor> tempList = new LinkedHashMap<>(modules);
    tempList.remove(id);
    logger.debug("delete: templist: " + tempList.keySet());
    String res = checkAllDependencies(tempList);
    if ( ! res.isEmpty()) {
      fut.handle(new Failure<>(USER, "delete: module " + id + ": " + res));
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
