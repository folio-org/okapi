package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.ModuleStore;
import org.folio.okapi.util.DepResolution;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.OkapiError;

/**
 * Manages a list of modules known to Okapi's "/_/proxy". Maintains consistency
 * checks on module versions, etc. Stores them in the database too, if we have
 * one.
 */
public class ModuleManager {

  private final Logger logger = OkapiLogger.get();
  private static final String MAP_NAME = "modules";
  private final LockedTypedMap1<ModuleDescriptor> modules
      = new LockedTypedMap1<>(ModuleDescriptor.class);
  private final ModuleStore moduleStore;
  private final Messages messages = Messages.getInstance();
  private final boolean local;

  public ModuleManager(ModuleStore moduleStore, boolean local) {
    this.moduleStore = moduleStore;
    this.local = local;
  }

  /**
   * Initialize module manager.
   * @param vertx Vert.x handle
   * @return future result
   */
  public Future<Void> init(Vertx vertx) {
    return modules.init(vertx, MAP_NAME, local)
        .compose(x -> loadModules());
  }

  /**
   * Load the modules from the database, if not already loaded.
   * @return future result
   */
  private Future<Void> loadModules() {
    if (moduleStore == null) {
      return Future.succeededFuture();
    }
    return modules.size().compose(kres -> {
      if (kres > 0) {
        logger.debug("Not loading modules, looks like someone already did");
        return Future.succeededFuture();
      }
      return moduleStore.getAll().compose(res -> {
        List<Future<Void>> futures = new LinkedList<>();
        for (ModuleDescriptor md : res) {
          futures.add(modules.add(md.getId(), md));
        }
        return GenericCompositeFuture.all(futures).mapEmpty();
      });
    });
  }

  /**
   * Create a list of modules.
   *
   * @param list list of modules
   * @param check whether to check dependencies
   * @param preRelease whether to allow pre-releasee
   * @param npmSnapshot whether to allow npm-snapshot
   * @param removeIfMissingDep skip modules where dependency check fails
   * @return future
   */
  public Future<Void> createList(List<ModuleDescriptor> list, boolean check, boolean preRelease,
                                 boolean npmSnapshot, boolean removeIfMissingDep) {
    return getModulesWithFilter(preRelease, npmSnapshot, null).compose(ares -> {
      Map<String, ModuleDescriptor> tempList = new HashMap<>();
      for (ModuleDescriptor md : ares) {
        tempList.put(md.getId(), md);
      }
      LinkedList<ModuleDescriptor> newList = new LinkedList<>();
      for (ModuleDescriptor md : list) {
        final String id = md.getId();
        if (tempList.containsKey(id)) {
          ModuleDescriptor exMd = tempList.get(id);
          String exJson = Json.encodePrettily(exMd);
          String json = Json.encodePrettily(md);
          if (!json.equals(exJson)) {
            return Future.failedFuture(new OkapiError(ErrorType.USER,
                messages.getMessage("10203", id)));
          }
        } else {
          tempList.put(id, md);
          newList.add(md);
        }
      }
      if (check) {
        String res = DepResolution.checkDependencies(tempList.values(), newList,
            removeIfMissingDep);
        if (!res.isEmpty()) {
          return Future.failedFuture(new OkapiError(ErrorType.USER, res));
        }
      }
      return createList2(newList);
    });
  }

  private Future<Void> createList2(List<ModuleDescriptor> list) {
    List<Future<Void>> futures = new LinkedList<>();
    for (ModuleDescriptor md : list) {
      if (moduleStore != null) {
        futures.add(moduleStore.insert(md));
      }
      futures.add(modules.add(md.getId(), md));
    }
    return GenericCompositeFuture.all(futures).mapEmpty();
  }

  /**
   * Delete a module.
   *
   * @param id module ID
   * @return future
   */
  public Future<Void> delete(String id) {
    return modules.getAll()
        .compose(ares -> deleteCheckDep(id, ares))
        .compose(res -> {
          if (moduleStore == null) {
            return Future.succeededFuture();
          } else {
            return moduleStore.delete(id).mapEmpty();
          }
        })
        .compose(res -> deleteInternal(id).mapEmpty());
  }

  private Future<Void> deleteCheckDep(String id, LinkedHashMap<String, ModuleDescriptor> mods) {
    if (!mods.containsKey(id)) {
      return Future.failedFuture(
          new OkapiError(ErrorType.NOT_FOUND, messages.getMessage("10207", id)));
    }
    mods.remove(id);
    String res = DepResolution.checkAllDependencies(mods);
    if (!res.isEmpty()) {
      return Future.failedFuture(new OkapiError(ErrorType.USER,
          messages.getMessage("10208", id, res)));
    }
    return Future.succeededFuture();
  }

  private Future<Void> deleteInternal(String id) {
    return modules.remove(id).mapEmpty();
  }

  /**
   * Get a module descriptor from ID, if not found the Future will fail with
   * an {@link OkapiError} with {@link ErrorType#NOT_FOUND}.
   *
   * @param id module ID to get.
   * @return fut future with resulting Module Descriptor
   */
  public Future<ModuleDescriptor> get(String id) {
    return modules.getNotFound(id);
  }

  Future<ModuleDescriptor> getLatest(String id) {
    ModuleId moduleId = new ModuleId(id);
    if (moduleId.hasSemVer()) {
      return get(id);
    }
    return modules.getKeys().compose(res -> {
      String latest = moduleId.getLatest(res);
      return get(latest);
    });
  }

  Future<List<ModuleDescriptor>> getModulesWithFilter(boolean preRelease, boolean npmSnapshot,
                                                      List<String> skipModules) {

    Set<String> skipIds = new TreeSet<>();
    if (skipModules != null) {
      skipIds.addAll(skipModules);
    }
    return modules.getAll().compose(kres -> {
      List<ModuleDescriptor> mdl = new LinkedList<>();
      for (ModuleDescriptor md : kres.values()) {
        String id = md.getId();
        ModuleId idThis = new ModuleId(id);
        if ((npmSnapshot || !idThis.hasNpmSnapshot())
            && (preRelease || !idThis.hasPreRelease())
            && !skipIds.contains(id)) {
          mdl.add(md);
        }
      }
      return Future.succeededFuture(mdl);
    });
  }
}
