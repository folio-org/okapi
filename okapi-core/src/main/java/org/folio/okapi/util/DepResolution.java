package org.folio.okapi.util;

import io.vertx.core.Handler;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;

public class DepResolution {

  private static Logger logger = OkapiLogger.get();
  private static Messages messages = Messages.getInstance();

  private DepResolution() {
    throw new IllegalAccessError("DepResolution");
  }

  /**
   * Check one dependency.
   *
   * @param md module to check
   * @param req required dependency
   * @param pInts the list to provided interface as returned by getProvidedInterfaces
   * @return null if ok, or error message
   */
  private static String checkOneDependency(ModuleDescriptor md, InterfaceDescriptor req,
    Map<String, List<InterfaceDescriptor>> pInts, Collection<ModuleDescriptor> modList) {

    Map<String, InterfaceDescriptor> seenVersions = new HashMap<>();
    List<InterfaceDescriptor> pIntsList = pInts.get(req.getId());
    if (pIntsList != null) {
      for (InterfaceDescriptor pi : pIntsList) {
        logger.debug("Checking dependency of " + md.getId() + ": "
          + req.getId() + " " + req.getVersion()
          + " against " + pi.getId() + " " + pi.getVersion());
        if (req.getId().equals(pi.getId())) {
          if (pi.isCompatible(req)) {
            logger.debug("Dependency OK");
            return null;
          }
          seenVersions.put(pi.getVersion(), pi);
        }
      }
    }
    if (seenVersions.isEmpty()) {
      return messages.getMessage("10200", md.getId(), req.getId(), req.getVersion());
    }
    StringBuilder moduses = new StringBuilder();
    String sep = "";
    for (InterfaceDescriptor seenVersion : seenVersions.values()) {
      moduses.append(sep + seenVersion.getVersion());
      sep = " ";
      for (ModuleDescriptor mdi : modList) {
        for (InterfaceDescriptor provi : mdi.getProvidesList()) {
          if (req.getId().equals(provi.getId()) && seenVersion.isCompatible(provi)) {
            moduses.append("/");
            moduses.append(mdi.getId());
          }
        }
      }
    }
    return messages.getMessage("10201", md.getId(), req.getId(),
      req.getVersion(), moduses.toString());
  }

  private static List<String> checkDependenciesInts(ModuleDescriptor md,
    Map<String, ModuleDescriptor> modlist, Map<String, List<InterfaceDescriptor>> pInts) {

    List<String> list = new LinkedList<>(); // error messages (empty=no errors)
    logger.debug("Checking dependencies of " + md.getId());
    for (InterfaceDescriptor req : md.getRequiresList()) {
      String res = checkOneDependency(md, req, pInts, modlist.values());
      if (res != null) {
        list.add(res);
      }
    }
    return list;
  }

  private static Map<String, List<InterfaceDescriptor>> getProvidedInterfaces(Collection<ModuleDescriptor> modList) {
    Map<String, List<InterfaceDescriptor>> pInts = new HashMap<>();
    for (ModuleDescriptor md : modList) {
      for (InterfaceDescriptor req : md.getProvidesList()) {
        final String version = req.getVersion();
        boolean found = false;
        List<InterfaceDescriptor> iList = pInts.get(req.getId());
        if (iList == null) {
          iList = new LinkedList<>();
        } else {
          for (InterfaceDescriptor nInt : iList) {
            String existingVersion = nInt.getVersion();
            if (existingVersion.equals(version)) {
              found = true;
            }
          }
        }
        if (!found) {
          iList.add(req);
          pInts.put(req.getId(), iList);
        }
      }
    }
    return pInts;
  }

  public static String checkDependencies(Map<String, ModuleDescriptor> available, Collection<ModuleDescriptor> testList) {
    Map<String, List<InterfaceDescriptor>> pInts = getProvidedInterfaces(available.values());

    List<String> list = new LinkedList<>();
    for (ModuleDescriptor md : testList) {
      List<String> res = checkDependenciesInts(md, available, pInts);
      list.addAll(res);
    }
    if (list.isEmpty()) {
      return "";
    } else {
      return String.join(". ", list);
    }
  }

  public static String checkAllDependencies(Map<String, ModuleDescriptor> available) {
    Collection<ModuleDescriptor> testList = available.values();
    return checkDependencies(available, testList);
  }

  /**
   * Check a module list for conflicts.
   *
   * @param modlist modules to be checked
   * @return error message listing conflicts, or "" if no problems
   */
  public static String checkAllConflicts(Map<String, ModuleDescriptor> modlist) {
    Map<String, String> provs = new HashMap<>(); // interface name to module name
    List<String> conflicts = new LinkedList<>();
    for (ModuleDescriptor md : modlist.values()) {
      InterfaceDescriptor[] provides = md.getProvidesList();
      for (InterfaceDescriptor mi : provides) {
        if (mi.isRegularHandler()) {
          String confl = provs.get(mi.getId());
          if (confl == null || confl.isEmpty()) {
            provs.put(mi.getId(), md.getId());
          } else {
            String msg = messages.getMessage("10202", mi.getId(), md.getId(), confl);
            conflicts.add(msg);
          }
        }
      }
    }
    return String.join(". ", conflicts);
  }

  private static TenantModuleDescriptor getNextTM(Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml) {

    Iterator<TenantModuleDescriptor> it = tml.iterator();
    TenantModuleDescriptor tm = null;
    while (it.hasNext()) {
      tm = it.next();
      TenantModuleDescriptor.Action action = tm.getAction();
      String id = tm.getId();
      logger.info("getNextTM: loop id=" + id + " action=" + action.name());
      if (action == TenantModuleDescriptor.Action.enable && !modsEnabled.containsKey(id)) {
        logger.info("getNextMT: return tm for action=enable");
        return tm;
      }
      if (action == TenantModuleDescriptor.Action.disable && modsEnabled.containsKey(id)) {
        logger.info("getNextTM: return tm for action=disable");
        return tm;
      }
    }
    logger.info("getNextTM done null");
    return null;
  }

  public static void installSimulate(Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    List<String> errors = new LinkedList<>();
    for (TenantModuleDescriptor tm : tml) {
      String id = tm.getId();
      ModuleId moduleId = new ModuleId(id);
      if (tm.getAction() == TenantModuleDescriptor.Action.enable) {
        if (!moduleId.hasSemVer()) {
          id = moduleId.getLatest(modsAvailable.keySet());
          tm.setId(id);
        }
        if (!modsAvailable.containsKey(id)) {
          errors.add(messages.getMessage("10801", id));
        }
        if (modsEnabled.containsKey(id)) {
          tm.setAction(TenantModuleDescriptor.Action.uptodate);
        }
      }
      if (tm.getAction() == TenantModuleDescriptor.Action.disable) {
        if (!moduleId.hasSemVer()) {
          id = moduleId.getLatest(modsEnabled.keySet());
          tm.setId(id);
        }
        if (!modsEnabled.containsKey(id)) {
          errors.add(messages.getMessage("10801", id));
        }
      }
    }
    if (!errors.isEmpty()) {
      fut.handle(new Failure<>(USER, String.join(". ", errors)));
      return;
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
    String s = DepResolution.checkAllDependencies(modsEnabled);
    if (!s.isEmpty()) {
      logger.warn("installModules.checkAllDependencies: " + s);
      fut.handle(new Failure<>(USER, s));
      return;
    }

    logger.info("installModules.returning OK");
    fut.handle(new Success<>(Boolean.TRUE));
  }

  private static boolean tmAction(TenantModuleDescriptor tm,
    Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    String id = tm.getId();
    TenantModuleDescriptor.Action action = tm.getAction();
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

  private static boolean tmEnable(String id, Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    List<String> ret = addModuleDependencies(modsAvailable.get(id), modsAvailable,
      modsEnabled, tml);
    if (ret.isEmpty()) {
      return false;
    }
    fut.handle(new Failure<>(USER, "enable " + id + " failed: " + String.join(". ", ret)));
    return true;
  }

  private static boolean tmDisable(String id, Map<String, ModuleDescriptor> modsAvailable,
    Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
    Handler<ExtendedAsyncResult<Boolean>> fut) {
    List<String> ret = removeModuleDependencies(modsAvailable.get(id),
      modsEnabled, tml);
    if (ret.isEmpty()) {
      return false;
    }
    fut.handle(new Failure<>(USER, "disable " + id + " failed: " + String.join(". ", ret)));
    return true;
  }

  private static List<String> checkInterfaceDependency(ModuleDescriptor md, InterfaceDescriptor req,
    Map<String, ModuleDescriptor> modsAvailable, Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml) {

    List<String> ret = new LinkedList<>();
    // check if already enabled
    if (checkInterfaceDepAlreadyEnabled(modsEnabled, req)) {
      return ret;
    }
    // check if mentioned already in other install action
    ModuleDescriptor foundMd = checkInterfaceDepOtherInstall(tml, modsAvailable, req);
    if (foundMd != null) {
      return addModuleDependencies(foundMd, modsAvailable, modsEnabled, tml);
    }
    Map<String,ModuleDescriptor> productMd = checkInterfaceDepAvailable(modsAvailable, req);
    if (productMd.isEmpty()) {
      String s = "interface " + req.getId() + " required by module " + md.getId() + " not found";
      ret.add(s);
      return ret;
    } else if (productMd.size() == 1) {
      Set<String> s = productMd.keySet();
      String k = s.iterator().next();
      foundMd = productMd.get(k);
    } else {
      String s = "interface " + req.getId() + " required by module " + md.getId() + " is provided by multiple products: "
        + String.join(", ", productMd.keySet());
      ret.add(s);
      return ret;
    }
    return addModuleDependencies(foundMd, modsAvailable, modsEnabled, tml);
  }

  private static Map<String, ModuleDescriptor> checkInterfaceDepAvailable(Map<String, ModuleDescriptor> modsAvailable,
    InterfaceDescriptor req) {

    Set<String> replaceProducts = new HashSet<>();
    Map<String, ModuleDescriptor> productMd = new HashMap<>();
    for (Map.Entry<String, ModuleDescriptor> entry : modsAvailable.entrySet()) {
      ModuleDescriptor md = entry.getValue();
      String product = md.getProduct();
      for (InterfaceDescriptor pi : md.getProvidesList()) {
        if (pi.isRegularHandler() && pi.isCompatible(req)) {
          if (md.getReplaces() != null) {
            Collections.addAll(replaceProducts, md.getReplaces());
          }
          if (productMd.containsKey(product)) {
            ModuleDescriptor fMd = productMd.get(product);
            if (md.compareTo(fMd) > 0) {
              productMd.put(product, md);
            }
          } else {
            productMd.put(product, md);
          }
        }
      }
    }
    for (String replaceProduct : replaceProducts) {
      productMd.remove(replaceProduct);
    }
    return productMd;
  }

  private static ModuleDescriptor checkInterfaceDepOtherInstall(List<TenantModuleDescriptor> tml,
    Map<String, ModuleDescriptor> modsAvailable, InterfaceDescriptor req) {

    ModuleDescriptor foundMd = null;
    Iterator<TenantModuleDescriptor> it = tml.iterator();
    while (it.hasNext()) {
      TenantModuleDescriptor tm = it.next();
      ModuleDescriptor md = modsAvailable.get(tm.getId());
      if (md != null && tm.getAction() == TenantModuleDescriptor.Action.enable) {
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

  private static boolean checkInterfaceDepAlreadyEnabled(Map<String, ModuleDescriptor> modsEnabled, InterfaceDescriptor req) {
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

  private static int resolveModuleConflicts(ModuleDescriptor md, Map<String, ModuleDescriptor> modsEnabled,
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
                tm.setAction(TenantModuleDescriptor.Action.disable);
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

  private static void addOrReplace(List<TenantModuleDescriptor> tml, ModuleDescriptor md,
    TenantModuleDescriptor.Action action, ModuleDescriptor fm) {

    logger.info("addOrReplace md.id=" + md.getId());
    Iterator<TenantModuleDescriptor> it = tml.iterator();
    boolean found = false;
    while (it.hasNext()) {
      TenantModuleDescriptor tm = it.next();
      if (tm.getAction().equals(action) && tm.getId().equals(md.getId())) {
        it.remove();
      } else if (fm != null && tm.getAction() == TenantModuleDescriptor.Action.enable && tm.getId().equals(fm.getId())) {
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

  private static List<String> addModuleDependencies(ModuleDescriptor md,
    Map<String, ModuleDescriptor> modsAvailable, Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml) {

    List<String> ret = new LinkedList<>();
    logger.info("addModuleDependencies " + md.getId());
    for (InterfaceDescriptor req : md.getRequiresList()) {
      ret.addAll(checkInterfaceDependency(md, req, modsAvailable, modsEnabled, tml));
    }
    if (!ret.isEmpty()) {
      return ret;
    }
    List<ModuleDescriptor> fromModule = new LinkedList<>();
    resolveModuleConflicts(md, modsEnabled, tml, fromModule);

    modsEnabled.put(md.getId(), md);
    addOrReplace(tml, md, TenantModuleDescriptor.Action.enable, fromModule.isEmpty() ? null : fromModule.get(0));
    return ret;
  }

  private static List<String> removeModuleDependencies(ModuleDescriptor md,
    Map<String, ModuleDescriptor> modsEnabled,
    List<TenantModuleDescriptor> tml) {
    logger.info("removeModuleDependencies " + md.getId());

    List<String> ret = new LinkedList<>();
    if (modsEnabled.containsKey(md.getId())) {
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
                ret.addAll(removeModuleDependencies(rm, modsEnabled, tml));
                it = modsEnabled.keySet().iterator();
              }
            }
          }
        }
      }
      if (!ret.isEmpty()) {
        return ret;
      }
      modsEnabled.remove(md.getId());
      addOrReplace(tml, md, TenantModuleDescriptor.Action.disable, null);
    }
    return ret;
  }

  public static List<ModuleDescriptor> getLatestProducts(int limit, List<ModuleDescriptor> mdl) {

    Collections.sort(mdl, Collections.reverseOrder());
    Iterator<ModuleDescriptor> it = mdl.listIterator();
    String product = "";
    int no = 0;
    while (it.hasNext()) {
      ModuleDescriptor md = it.next();
      if (!product.equals(md.getProduct())) {
        product = md.getProduct();
        no = 0;
      } else if (no >= limit) {
        it.remove();
      }
      no++;
    }
    return mdl;
  }
}
