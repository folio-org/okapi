package org.folio.okapi.service;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.okapi.bean.ModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor.Action;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.util.CompList;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.Messages;

/**
 * Manages a list of modules known to Okapi's "/_/proxy". Maintains consistency
 * checks on module versions, etc. Stores them in the database too, if we have
 * one.
 */
public class ModuleManager {

  private final Logger logger = OkapiLogger.get();
  private TenantManager tenantManager = null;
  private String mapName = "modules";
  private LockedTypedMap1<ModuleDescriptor> modules
    = new LockedTypedMap1<>(ModuleDescriptor.class);
  private ModuleStore moduleStore;
  private Messages messages = Messages.getInstance();

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

  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    modules.init(vertx, mapName, ires -> {
      if (ires.failed()) {
        fut.handle(new Failure<>(ires.getType(), ires.cause()));
      } else {
        loadModules(fut);
      }
    });
  }

  /**
   * Load the modules from the database, if not already loaded.
   *
   * @param fut
   */
  private void loadModules(Handler<ExtendedAsyncResult<Void>> fut) {
    if (moduleStore == null) {
      fut.handle(new Success<>());
    } else {
      modules.size(kres -> {
        if (kres.failed()) {
          fut.handle(new Failure<>(INTERNAL, kres.cause()));
        } else if (kres.result() > 0) {
          logger.debug("Not loading modules, looks like someone already did");
          fut.handle(new Success<>());
        } else {
          moduleStore.getAll(mres -> {
            if (mres.failed()) {
              fut.handle(new Failure<>(mres.getType(), mres.cause()));
            } else {
              CompList<Void> futures = new CompList<>(INTERNAL);
              for (ModuleDescriptor md : mres.result()) {
                Future<Void> f = Future.future();
                modules.add(md.getId(), md, f::handle);
                futures.add(f);
              }
              futures.all(fut);
            }
          });
        }
      });
    }
  }

  /**
   * Check one dependency.
   *
   * @param md module to check
   * @param req required dependency
   * @param modlist the list to check against
   * @return "" if ok, or error message
   */
  private String checkOneDependency(ModuleDescriptor md, InterfaceDescriptor req,
    Map<String, ModuleDescriptor> modlist) {
    InterfaceDescriptor seenversion = null;
    for (Map.Entry<String, ModuleDescriptor> entry : modlist.entrySet()) {
      ModuleDescriptor rm = entry.getValue();
      for (InterfaceDescriptor pi : rm.getProvidesList()) {
        logger.debug("Checking dependency of " + md.getId() + ": "
          + req.getId() + " " + req.getVersion()
          + " against " + pi.getId() + " " + pi.getVersion());
        if (req.getId().equals(pi.getId())) {
          seenversion = pi;
          if (pi.isCompatible(req)) {
            logger.debug("Dependency OK");
            return "";  // ok
          }
        }
      }
    }
    if (seenversion == null) {
      return messages.getMessage("10200", md.getId(), req.getId(), req.getVersion());

    } else {
      return messages.getMessage("10201", md.getId(), req.getId(), req.getVersion(), seenversion.getVersion());
    }
  }

  /**
   * Check that the dependencies are satisfied.
   *
   * @param md Module to be checked
   * @return "" if no problems, or an error message
   *
   * This could be done like we do conflicts, by building a map and checking
   * against that...
   */
  private String checkDependencies(ModuleDescriptor md,
    Map<String, ModuleDescriptor> modlist) {
    logger.debug("Checking dependencies of " + md.getId());
    for (InterfaceDescriptor req : md.getRequiresList()) {
      String res = checkOneDependency(md, req, modlist);
      if (!res.isEmpty()) {
        return res;
      }
    }
    return "";  // ok
  }

  private TenantModuleDescriptor getNextTM(Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml) {

    Iterator<TenantModuleDescriptor> it = tml.iterator();
    TenantModuleDescriptor tm = null;
    while (it.hasNext()) {
      tm = it.next();
      Action action = tm.getAction();
      String id = tm.getId();
      logger.info("getNextTM: loop id=" + id + " action=" + action.name());
      if (action == Action.enable && !modsEnabled.containsKey(id)) {
        logger.info("getNextMT: return tm for action=enable");
        return tm;
      }
      if (action == Action.disable && modsEnabled.containsKey(id)) {
        logger.info("getNextTM: return tm for action=disable");
        return tm;
      }
      if (action == Action.conflict) {
        logger.info("getNextTM: return null on conflict");
        return null;
      }
    }
    logger.info("getNextTM done null");
    return null;
  }

  public void installSimulate(Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    for (TenantModuleDescriptor tm : tml) {
      String id = tm.getId();
      ModuleId moduleId = new ModuleId(id);
      if (!moduleId.hasSemVer()) {
        id = moduleId.getLatest(modsAvailable.keySet());
        tm.setId(id);
      }
      if (tm.getAction() == Action.enable) {
        if (!modsAvailable.containsKey(id)) {
          fut.handle(new Failure<>(NOT_FOUND, id));
          return;
        }
        if (modsEnabled.containsKey(id)) {
          tm.setAction(Action.uptodate);
        }
      }
      if (tm.getAction() == Action.disable && !modsEnabled.containsKey(id)) {
        fut.handle(new Failure<>(NOT_FOUND, id));
        return;
      }
    }
    final int lim = tml.size();
    for (int i = 0; i <= lim; i++) {
      logger.info("outer loop i=" + i + " tml.size=" + tml.size());
      TenantModuleDescriptor tm = getNextTM(modsEnabled, tml);
      if (tm == null) {
        break;
      }
      if (tmAction(tm, modsAvailable, modsEnabled, tml, fut)) {
        return;
      }
    }
    String s = checkAllDependencies(modsEnabled);
    if (!s.isEmpty()) {
      logger.warn("installModules.checkAllDependencies: " + s);
      fut.handle(new Failure<>(USER, s));
      return;
    }

    logger.info("installModules.returning OK");
    fut.handle(new Success<>(Boolean.TRUE));
  }

  private boolean tmAction(TenantModuleDescriptor tm,
    Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    String id = tm.getId();
    Action action = tm.getAction();
    if (null == action) {
      fut.handle(new Failure<>(INTERNAL, messages.getMessage("10404", "null")));
      return true;
    } else {
      switch (action) {
        case enable:
          return tmEnable(id, modsAvailable, modsEnabled, tml, fut);
        case uptodate:
          return false;
        case disable:
          return tmDisable(id, modsAvailable, modsEnabled, tml, fut);
        default:
          fut.handle(new Failure<>(INTERNAL, messages.getMessage("10404", action.name())));
          return true;
      }
    }
  }

  private boolean tmEnable(String id, Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    if (addModuleDependencies(modsAvailable.get(id), modsAvailable,
      modsEnabled, tml) == -1) {
      fut.handle(new Failure<>(USER, "install: can not enable " + id
        + " due to missing dependencies or conflict"));
      return true;
    }
    return false;
  }

  private boolean tmDisable(String id, Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {
    if (removeModuleDependencies(modsAvailable.get(id),
      modsEnabled, tml) == -1) {
      fut.handle(new Failure<>(USER, "install: can not disable " + id
        + " due to missing dependencies or conflict"));
      return true;
    }
    return false;
  }

  private int checkInterfaceDependency(InterfaceDescriptor req,
    Map<String, ModuleDescriptor> modsAvailable, Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml) {

    // check if already enabled
    if (checkInterfaceDepAlreadyEnabled(modsEnabled, req)) {
      return 0;
    }
    // check if mentioned already in other install action
    ModuleDescriptor foundMd = checkInterfaceDepOtherInstall(tml, modsAvailable, req);
    if (foundMd != null) {
      return addModuleDependencies(foundMd, modsAvailable, modsEnabled, tml);
    }
    // see if we can find it in available modules
    foundMd = checkInterfaceDepAvailable(modsAvailable, req);
    if (foundMd != null) {
      return addModuleDependencies(foundMd, modsAvailable, modsEnabled, tml);
    }
    logger.warn("interface req=" + req.getId() + " NOT FOUND");
    return -1;
  }

  private ModuleDescriptor checkInterfaceDepAvailable(Map<String, ModuleDescriptor> modsAvailable,
    InterfaceDescriptor req) {

    ModuleDescriptor foundMd = null;
    for (Map.Entry<String, ModuleDescriptor> entry : modsAvailable.entrySet()) {
      ModuleDescriptor md = entry.getValue();
      for (InterfaceDescriptor pi : md.getProvidesList()) {
        if (pi.isRegularHandler() && pi.isCompatible(req)
          && (foundMd == null || md.compareTo(foundMd) > 0)) {// newest module
          foundMd = md;
        }
      }
    }
    return foundMd;
  }

  private ModuleDescriptor checkInterfaceDepOtherInstall(List<TenantModuleDescriptor> tml,
    Map<String, ModuleDescriptor> modsAvailable, InterfaceDescriptor req) {

    ModuleDescriptor foundMd = null;
    Iterator<TenantModuleDescriptor> it = tml.iterator();
    while (it.hasNext()) {
      TenantModuleDescriptor tm = it.next();
      ModuleDescriptor md = modsAvailable.get(tm.getId());
      if (md != null && tm.getAction() == Action.enable) {
        for (InterfaceDescriptor pi : md.getProvidesList()) {
          if (pi.isRegularHandler() && pi.isCompatible(req)) {
            it.remove();
            logger.info("Dependency OK for existing enable id=" + md.getId());
            foundMd = md;
          }
        }
      }
    }
    return foundMd;
  }

  private boolean checkInterfaceDepAlreadyEnabled(Map<String, ModuleDescriptor> modsEnabled, InterfaceDescriptor req) {
    for (Map.Entry<String, ModuleDescriptor> entry : modsEnabled.entrySet()) {
      ModuleDescriptor md = entry.getValue();
      for (InterfaceDescriptor pi : md.getProvidesList()) {
        if (pi.isRegularHandler() && pi.isCompatible(req)) {
          logger.info("Dependency OK already enabled id=" + md.getId());
          return true;
        }
      }
    }
    return false;
  }

  private int resolveModuleConflicts(ModuleDescriptor md, Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml, List<ModuleDescriptor> fromModule) {

    int v = 0;
    Iterator<String> it = modsEnabled.keySet().iterator();
    while (it.hasNext()) {
      String runningmodule = it.next();
      ModuleDescriptor rm = modsEnabled.get(runningmodule);
      if (md.getProduct().equals(rm.getProduct())) {
        logger.info("resolveModuleConflicts from " + runningmodule);
        it.remove();
        fromModule.add(rm);
        v++;
      } else {
        for (InterfaceDescriptor pi : rm.getProvidesList()) {
          if (pi.isRegularHandler()) {
            String confl = pi.getId();
            for (InterfaceDescriptor mi : md.getProvidesList()) {
              if (mi.getId().equals(confl)
                && mi.isRegularHandler()
                && modsEnabled.containsKey(runningmodule)) {
                logger.info("resolveModuleConflicts remove " + runningmodule);
                TenantModuleDescriptor tm = new TenantModuleDescriptor();
                tm.setAction(Action.disable);
                tm.setId(runningmodule);
                tml.add(tm);
                it.remove();
                v++;
              }
            }
          }
        }
      }
    }
    return v;
  }

  private void addOrReplace(List<TenantModuleDescriptor> tml, ModuleDescriptor md,
    Action action, ModuleDescriptor fm) {

    logger.info("addOrReplace md.id=" + md.getId());
    Iterator<TenantModuleDescriptor> it = tml.iterator();
    boolean found = false;
    while (it.hasNext()) {
      TenantModuleDescriptor tm = it.next();
      if (tm.getAction().equals(action) && tm.getId().equals(md.getId())) {
        it.remove();
      } else if (fm != null && tm.getAction() == Action.enable && tm.getId().equals(fm.getId())) {
        logger.info("resolveConflict .. patch id=" + md.getId());
        tm.setId(md.getId());
        found = true;
      }
    }
    if (found) {
      return;
    }
    TenantModuleDescriptor t = new TenantModuleDescriptor();
    t.setAction(action);
    t.setId(md.getId());
    if (fm != null) {
      t.setFrom(fm.getId());
    }
    tml.add(t);
  }

  private int addModuleDependencies(ModuleDescriptor md,
    Map<String, ModuleDescriptor> modsAvailable, Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml) {

    int sum = 0;
    logger.info("addModuleDependencies " + md.getId());
    for (InterfaceDescriptor req : md.getRequiresList()) {
      int v = checkInterfaceDependency(req, modsAvailable, modsEnabled, tml);
      if (v == -1) {
        return v;
      }
      sum += v;
    }
    List<ModuleDescriptor> fromModule = new LinkedList<>();
    sum += resolveModuleConflicts(md, modsEnabled, tml, fromModule);

    modsEnabled.put(md.getId(), md);
    addOrReplace(tml, md, Action.enable, fromModule.isEmpty() ? null : fromModule.get(0));
    return sum + 1;
  }

  private int removeModuleDependencies(ModuleDescriptor md,
    Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml) {
    int sum = 0;
    logger.info("removeModuleDependencies " + md.getId());

    if (!modsEnabled.containsKey(md.getId())) {
      return 0;
    }
    InterfaceDescriptor[] provides = md.getProvidesList();
    for (InterfaceDescriptor prov : provides) {
      if (prov.isRegularHandler()) {
        Iterator<String> it = modsEnabled.keySet().iterator();
        while (it.hasNext()) {
          String runningmodule = it.next();
          ModuleDescriptor rm = modsEnabled.get(runningmodule);
          InterfaceDescriptor[] requires = rm.getRequiresList();
          for (InterfaceDescriptor ri : requires) {
            if (prov.getId().equals(ri.getId())) {
              sum += removeModuleDependencies(rm, modsEnabled, tml);
              it = modsEnabled.keySet().iterator();
            }
          }
        }
      }
    }
    modsEnabled.remove(md.getId());
    addOrReplace(tml, md, Action.disable, null);
    return sum + 1;
  }

  /**
   * Check that all dependencies are satisfied. Usually called with a copy of
   * the modules list, after making some change.
   *
   * @param modlist list to check
   * @return error message, or "" if all is ok
   */
  public String checkAllDependencies(Map<String, ModuleDescriptor> modlist) {
    for (ModuleDescriptor md : modlist.values()) {
      String res = checkDependencies(md, modlist);
      if (!res.isEmpty()) {
        return res;
      }
    }
    return "";
  }

  /**
   * Check a module list for conflicts.
   *
   * @param modlist modules to be checked
   * @return error message listing conflicts, or "" if no problems
   */
  public String checkAllConflicts(Map<String, ModuleDescriptor> modlist) {
    Map<String, String> provs = new HashMap<>(); // interface name to module name
    StringBuilder conflicts = new StringBuilder();
    for (ModuleDescriptor md : modlist.values()) {
      InterfaceDescriptor[] provides = md.getProvidesList();
      for (InterfaceDescriptor mi : provides) {
        if (mi.isRegularHandler()) {
          String confl = provs.get(mi.getId());
          if (confl == null || confl.isEmpty()) {
            provs.put(mi.getId(), md.getId());
          } else {
            String msg = messages.getMessage("10202", mi.getId(), md.getId(), confl);
            conflicts.append(msg);
          }
        }
      }
    }
    logger.debug("checkAllConflicts: " + conflicts.toString());
    return conflicts.toString();
  }

  /**
   * Create a module.
   *
   * @param md
   * @param fut
   */
  public void create(ModuleDescriptor md, boolean check, Handler<ExtendedAsyncResult<Void>> fut) {
    List<ModuleDescriptor> l = new LinkedList<>();
    l.add(md);
    createList(l, check, fut);
  }

  /**
   * Create a whole list of modules.
   *
   * @param list
   * @param fut
   */
  public void createList(List<ModuleDescriptor> list, boolean check, Handler<ExtendedAsyncResult<Void>> fut) {
    modules.getAll(ares -> {
      if (ares.failed()) {
        fut.handle(new Failure<>(ares.getType(), ares.cause()));
        return;
      }
      LinkedHashMap<String, ModuleDescriptor> tempList = ares.result();
      LinkedList<ModuleDescriptor> nList = new LinkedList<>();
      for (ModuleDescriptor md : list) {
        final String id = md.getId();
        if (tempList.containsKey(id)) {
          ModuleDescriptor exMd = tempList.get(id);

          String exJson = Json.encodePrettily(exMd);
          String json = Json.encodePrettily(md);
          if (!json.equals(exJson)) {
            fut.handle(new Failure<>(USER, messages.getMessage("10203", id)));
            return;
          }
        } else {
          tempList.put(id, md);
          nList.add(md);
        }
      }
      if (check) {
        String res = checkAllDependencies(tempList);
        if (!res.isEmpty()) {
          fut.handle(new Failure<>(USER, res));
          return;
        }
      }
      createList2(nList, fut);
    });
  }

  private void createList2(List<ModuleDescriptor> list, Handler<ExtendedAsyncResult<Void>> fut) {
    CompList<Void> futures = new CompList<>(INTERNAL);
    for (ModuleDescriptor md : list) {
      Future<Void> f = Future.future();
      createList3(md, f::handle);
      futures.add(f);
    }
    futures.all(fut);
  }

  private void createList3(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    String id = md.getId();
    if (moduleStore == null) {
      modules.add(id, md, ares -> {
        if (ares.failed()) {
          fut.handle(new Failure<>(ares.getType(), ares.cause()));
          return;
        }
        fut.handle(new Success<>());
      });
    } else {
      moduleStore.insert(md, ires -> {
        if (ires.failed()) {
          fut.handle(new Failure<>(ires.getType(), ires.cause()));
          return;
        }
        modules.add(id, md, ares -> {
          if (ares.failed()) {
            fut.handle(new Failure<>(ares.getType(), ares.cause()));
            return;
          }
          fut.handle(new Success<>());
        });
      });
    }
  }

  /**
   * Update a module.
   *
   * @param md
   * @param fut
   */
  public void update(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = md.getId();
    modules.getAll(ares -> {
      if (ares.failed()) {
        fut.handle(new Failure<>(ares.getType(), ares.cause()));
        return;
      }
      LinkedHashMap<String, ModuleDescriptor> tempList = ares.result();
      tempList.put(id, md);
      String res = checkAllDependencies(tempList);
      if (!res.isEmpty()) {
        fut.handle(new Failure<>(USER, messages.getMessage("10204", id, res)));
        return;
      }
      tenantManager.getModuleUser(id, gres -> {
        if (gres.failed()) {
          if (gres.getType() == ANY) {
            String ten = gres.cause().getMessage();
            fut.handle(new Failure<>(USER, messages.getMessage("10205", id, ten)));
          } else { // any other error
            fut.handle(new Failure<>(gres.getType(), gres.cause()));
          }
          return;
        }
        // all ok, we can update it
        if (moduleStore == null) { // no db, just upd shared memory
          modules.put(id, md, fut);
        } else {
          moduleStore.update(md, ures -> { // store in db first,
            if (ures.failed()) {
              fut.handle(new Failure<>(ures.getType(), ures.cause()));
            } else {
              modules.put(id, md, fut);
            }
          });
        }
      }); // getModuleUser
    }); // get
  }

  /**
   * Delete a module.
   *
   * @param id
   * @param fut
   */
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    modules.getAll(ares -> {
      if (ares.failed()) {
        fut.handle(new Failure<>(ares.getType(), ares.cause()));
        return;
      }
      if (deleteCheckDep(id, fut, ares.result())) {
        return;
      }
      tenantManager.getModuleUser(id, ures -> {
        if (ures.failed()) {
          if (ures.getType() == ANY) {
            String ten = ures.cause().getMessage();
            fut.handle(new Failure<>(USER, messages.getMessage("10209", id, ten)));
            fut.handle(new Failure<>(USER, messages.getMessage("10206", id, ten)));
          } else {
            fut.handle(new Failure<>(ures.getType(), ures.cause()));
          }
        } else if (moduleStore == null) {
          deleteInternal(id, fut);
        } else {
          moduleStore.delete(id, dres -> {
            if (dres.failed()) {
              fut.handle(new Failure<>(dres.getType(), dres.cause()));
            } else {
              deleteInternal(id, fut);
            }
          });
        }
      });
    });
  }

  private boolean deleteCheckDep(String id, Handler<ExtendedAsyncResult<Void>> fut,
    LinkedHashMap<String, ModuleDescriptor> mods) {

    if (!mods.containsKey(id)) {
      fut.handle(new Failure<>(NOT_FOUND, messages.getMessage("10207")));
      return true;
    }
    mods.remove(id);
    String res = checkAllDependencies(mods);
    if (!res.isEmpty()) {
      fut.handle(new Failure<>(USER, messages.getMessage("10208", id, res)));
      return true;
    } else {
      return false;
    }
  }

  private void deleteInternal(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    modules.remove(id, rres -> {
      if (rres.failed()) {
        fut.handle(new Failure<>(rres.getType(), rres.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  /**
   * Get a module.
   *
   * @param id to get. If null, returns a null.
   * @param fut
   */
  public void get(String id, Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    if (id != null) {
      modules.get(id, fut);
    } else {
      fut.handle(new Success<>(null));
    }
  }

  public void getLatest(String id, Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    ModuleId moduleId = id != null ? new ModuleId(id) : null;
    if (moduleId == null || moduleId.hasSemVer()) {
      get(id, fut);
    } else {
      modules.getKeys(res2 -> {
        if (res2.failed()) {
          fut.handle(new Failure<>(res2.getType(), res2.cause()));
        } else {
          String latest = moduleId.getLatest(res2.result());
          get(latest, fut);
        }
      });
    }
  }

  public void getModulesWithFilter(ModuleId filter, boolean preRelease,
    Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    modules.getAll(kres -> {
      if (kres.failed()) {
        fut.handle(new Failure<>(kres.getType(), kres.cause()));
      } else {
        List<ModuleDescriptor> mdl = new LinkedList<>();
        for (ModuleDescriptor md : kres.result().values()) {
          String id = md.getId();
          ModuleId idThis = new ModuleId(id);
          if ((filter == null || idThis.hasPrefix(filter))
            && (preRelease || !idThis.hasPreRelease())) {
            mdl.add(md);
          }
        }
        fut.handle(new Success<>(mdl));
      }
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
    CompList<List<ModuleDescriptor>> futures = new CompList<>(INTERNAL);
    for (String id : ten.getEnabled().keySet()) {
      Future<ModuleDescriptor> f = Future.future();
      modules.get(id, res -> {
        if (res.succeeded()) {
          mdl.add(res.result());
        }
        f.handle(res);
      });
      futures.add(f);
    }
    futures.all(mdl, fut);
  }
}
