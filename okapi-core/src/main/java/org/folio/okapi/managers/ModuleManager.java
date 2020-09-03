package org.folio.okapi.managers;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
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
  private TenantManager tenantManager = null;
  private String mapName = "modules";
  private static final String EVENT_NAME = "moduleUpdate";
  private final LockedTypedMap1<ModuleDescriptor> modules
      = new LockedTypedMap1<>(ModuleDescriptor.class);
  private final Map<String,ModuleDescriptor> enabledModulesCache = new HashMap<>();
  private final ModuleStore moduleStore;
  private Vertx vertx;
  private final Messages messages = Messages.getInstance();
  // tenants with new permission module (_tenantPermissions version 1.1 or later)
  private Set<String> expandedPermModuleTenants = ConcurrentHashMap.newKeySet();

  public ModuleManager(ModuleStore moduleStore) {
    this.moduleStore = moduleStore;
  }

  /**
   * Force the map to be local. Even in cluster mode, will use a local memory
   * map. This way, the node will not share tenants with the cluster, and can
   * not proxy requests for anyone but the superTenant, to the InternalModule.
   * Which is just enough to act in the deployment mode.
   */
  public void forceLocalMap() {
    mapName = null;
  }

  public void setTenantManager(TenantManager tenantManager) {
    this.tenantManager = tenantManager;
  }

  /**
   * Initialize module manager.
   * @param vertx Vert.x handle
   * @return future result
   */
  public Future<Void> init(Vertx vertx) {
    this.vertx = vertx;
    consumeModulesUpdated();
    return modules.init(vertx, mapName)
        .compose(x -> loadModules());
  }

  private void consumeModulesUpdated() {
    EventBus eb = vertx.eventBus();
    eb.consumer(EVENT_NAME, res -> {
      String moduleId = (String) res.body();
      enabledModulesCache.remove(moduleId);
    });
  }

  private void invalidateCacheEntry(String id) {
    vertx.eventBus().publish(EVENT_NAME, id);
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
        List<Future> futures = new LinkedList<>();
        for (ModuleDescriptor md : res) {
          futures.add(modules.add(md.getId(), md));
        }
        return CompositeFuture.all(futures).mapEmpty();
      });
    });
  }

  Future<Void> enableAndDisableCheck(Tenant tenant, ModuleDescriptor modFrom,
                                     ModuleDescriptor modTo) {

    Promise<Void> promise = Promise.promise();
    getEnabledModules(tenant, gres -> {
      if (gres.failed()) {
        promise.fail(gres.cause());
        return;
      }
      List<ModuleDescriptor> modlist = gres.result();
      HashMap<String, ModuleDescriptor> mods = new HashMap<>(modlist.size());
      for (ModuleDescriptor md : modlist) {
        mods.put(md.getId(), md);
      }
      if (modTo == null) {
        String deps = DepResolution.checkAllDependencies(mods);
        if (!deps.isEmpty()) {
          promise.complete(); // failures even before we remove a module
          return;
        }
      }
      if (modFrom != null) {
        mods.remove(modFrom.getId());
      }
      if (modTo != null) {
        ModuleDescriptor already = mods.get(modTo.getId());
        if (already != null) {
          promise.fail("Module " + modTo.getId() + " already provided");
          return;
        }
        mods.put(modTo.getId(), modTo);
      }
      String conflicts = DepResolution.checkAllConflicts(mods);
      String deps = DepResolution.checkAllDependencies(mods);
      if (!conflicts.isEmpty() || !deps.isEmpty()) {
        promise.fail(conflicts + " " + deps);
        return;
      }
      promise.complete();
    });
    return promise.future();
  }

  /**
   * Create a module.
   *
   * @param md module descriptor
   * @param check whether to check dependencies
   * @param preRelease whether to allow pre-release
   * @param npmSnapshot whether to allow npm snapshot
   * @param fut future
   */
  public void create(ModuleDescriptor md, boolean check, boolean preRelease,
                     boolean npmSnapshot, Handler<ExtendedAsyncResult<Void>> fut) {
    List<ModuleDescriptor> l = new LinkedList<>();
    l.add(md);
    createList(l, check, preRelease, npmSnapshot, fut);
  }

  /**
   * Create a list of modules.
   *
   * @param list list of modules
   * @param check whether to check dependencies
   * @param preRelease whether to allow pre-releasee
   * @param npmSnapshot whether to allow npm-snapshot
   * @param fut future
   */
  public void createList(List<ModuleDescriptor> list, boolean check, boolean preRelease,
                         boolean npmSnapshot, Handler<ExtendedAsyncResult<Void>> fut) {
    getModulesWithFilter(preRelease, npmSnapshot, null).onComplete(ares -> {
      if (ares.failed()) {
        fut.handle(new Failure<>(OkapiError.getType(ares.cause()), ares.cause()));
        return;
      }
      Map<String, ModuleDescriptor> tempList = new HashMap<>();
      for (ModuleDescriptor md : ares.result()) {
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
            fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10203", id)));
            return;
          }
        } else {
          tempList.put(id, md);
          newList.add(md);
        }
      }
      if (check) {
        String res = DepResolution.checkDependencies(tempList.values(), newList);
        if (!res.isEmpty()) {
          fut.handle(new Failure<>(ErrorType.USER, res));
          return;
        }
      }
      createList2(newList).onComplete(res1 -> {
        if (res1.failed()) {
          fut.handle(new Failure<>(ErrorType.USER, res1.cause()));
        } else {
          fut.handle(new Success<>());
        }
      });
    });
  }

  private Future<Void> createList2(List<ModuleDescriptor> list) {
    List<Future> futures = new LinkedList<>();
    for (ModuleDescriptor md : list) {
      if (moduleStore != null) {
        futures.add(moduleStore.insert(md));
      }
      futures.add(modules.add(md.getId(), md));
    }
    return CompositeFuture.all(futures).mapEmpty();
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
        .compose(check -> tenantManager.getModuleUser(id))
        .compose(tenants -> {
          if (!tenants.isEmpty()) {
            return Future.failedFuture(new OkapiError(ErrorType.USER,
                messages.getMessage("10206", id, tenants.get(0))));
          }
          return Future.succeededFuture();
        })
        .compose(res2 -> {
          if (moduleStore == null) {
            return Future.succeededFuture(Boolean.TRUE);
          } else {
            return moduleStore.delete(id);
          }
        })
        .compose(res3 -> {
          if (Boolean.FALSE.equals(res3)) {
            return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, id));
          }
          return deleteInternal(id).mapEmpty();
        });
  }

  private Future<Void> deleteCheckDep(String id, LinkedHashMap<String, ModuleDescriptor> mods) {
    if (!mods.containsKey(id)) {
      return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, messages.getMessage("10207")));
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
    invalidateCacheEntry(id);
    return modules.remove(id).mapEmpty();
  }

  /**
   * Get a module descriptor from ID.
   *
   * @param id to get. If null, returns a null.
   * @returns fut future with resulting Module Descriptor
   */
  public Future<ModuleDescriptor> get(String id) {
    if (id == null) {
      return Future.succeededFuture(null);
    }
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

  /**
   * Get all modules that are enabled for the given tenant.
   *
   * @param ten tenant to check for
   * @param fut callback with a list of ModuleDescriptors (may be empty list)
   */
  public void getEnabledModules(Tenant ten,
                                Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    List<ModuleDescriptor> mdl = new LinkedList<>();
    List<Future> futures = new LinkedList<>();
    for (String id : ten.getEnabled().keySet()) {
      if (enabledModulesCache.containsKey(id)) {
        ModuleDescriptor md = enabledModulesCache.get(id);
        mdl.add(md);
        updateExpandedPermModuleTenants(ten.getId(), md);
      } else {
        futures.add(modules.get(id).compose(md -> {
          enabledModulesCache.put(id, md);
          mdl.add(md);
          updateExpandedPermModuleTenants(ten.getId(), md);
          return Future.succeededFuture();
        }));
      }
    }
    CompositeFuture.all(futures).onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
      } else {
        fut.handle(new Success<>(mdl));
      }
    });
  }

  private void updateExpandedPermModuleTenants(String tenant, ModuleDescriptor md) {
    InterfaceDescriptor id = md.getSystemInterface("_tenantPermissions");
    if (id == null) {
      return;
    }
    if (id.getVersion().equals("1.0")) {
      expandedPermModuleTenants.remove(tenant);
    } else {
      expandedPermModuleTenants.add(tenant);
    }
  }

  public Set<String> getExpandedPermModuleTenants() {
    return expandedPermModuleTenants;
  }

}
