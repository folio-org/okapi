package org.folio.okapi.util;

import io.vertx.core.Future;
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
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.OkapiLogger;

public class DepResolution {

  private static final Logger logger = OkapiLogger.get();
  private static final Messages messages = Messages.getInstance();

  private DepResolution() {
    throw new IllegalAccessError("DepResolution");
  }

  private static Map<String, InterfaceDescriptor> checkPresenceDependency(
      ModuleDescriptor md, InterfaceDescriptor req,
      Map<String, List<InterfaceDescriptor>> ints) {

    Map<String, InterfaceDescriptor> seenVersions = new HashMap<>();
    List<InterfaceDescriptor> intsList = ints.get(req.getId());
    if (intsList != null) {
      for (InterfaceDescriptor pi : intsList) {
        logger.debug("Checking dependency of {}: {} {} against {} {}",
            md.getId(), req.getId(), req.getVersion(),
            pi.getId(), pi.getVersion());
        if (req.getId().equals(pi.getId())) {
          if (pi.isCompatible(req)) {
            logger.debug("Dependency OK");
            return null;
          }
          seenVersions.put(pi.getVersion(), pi);
        }
      }
    }
    return seenVersions;
  }

  /**
   * Check one dependency.
   *
   * @param md module to check
   * @param req required dependency
   * @param ints the list to provided interface as returned by getProvidedInterfaces
   * @return null if ok, or error message
   */
  private static String checkOneDependency(ModuleDescriptor md, InterfaceDescriptor req,
                                           Map<String, List<InterfaceDescriptor>> ints,
                                           Collection<ModuleDescriptor> modList,
                                           boolean optional) {

    Map<String, InterfaceDescriptor> seenVersions = checkPresenceDependency(md, req, ints);
    if (seenVersions == null) { // found and compatible?
      return null;
    }
    if (seenVersions.isEmpty()) { // nothing found?
      if (optional) {
        return null;
      }
      return messages.getMessage("10200", md.getId(), req.getId(), req.getVersion());
    }
    // found but incompatible
    StringBuilder moduses = new StringBuilder();
    String sep = "";
    for (InterfaceDescriptor seenVersion : seenVersions.values()) {
      moduses.append(sep).append(seenVersion.getVersion());
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

  private static List<String> checkDependenciesInts(
      ModuleDescriptor md, Collection<ModuleDescriptor> modlist,
      Map<String, List<InterfaceDescriptor>> ints) {

    List<String> list = new LinkedList<>(); // error messages (empty=no errors)
    logger.debug("Checking dependencies of {}", md.getId());
    for (InterfaceDescriptor req : md.getRequiresList()) {
      String res = checkOneDependency(md, req, ints, modlist, false);
      if (res != null) {
        list.add(res);
      }
    }
    for (InterfaceDescriptor req : md.getOptionalList()) {
      String res = checkOneDependency(md, req, ints, modlist, true);
      if (res != null) {
        list.add(res);
      }
    }
    return list;
  }

  private static Map<String, List<InterfaceDescriptor>> getProvidedInterfaces(
      Collection<ModuleDescriptor> modList) {

    Map<String, List<InterfaceDescriptor>> ints = new HashMap<>();
    for (ModuleDescriptor md : modList) {
      for (InterfaceDescriptor req : md.getProvidesList()) {
        final String version = req.getVersion();
        boolean found = false;
        List<InterfaceDescriptor> intsList = ints.get(req.getId());
        if (intsList == null) {
          intsList = new LinkedList<>();
        } else {
          for (InterfaceDescriptor id : intsList) {
            String existingVersion = id.getVersion();
            if (existingVersion.equals(version)) {
              found = true;
              break;
            }
          }
        }
        if (!found) {
          intsList.add(req);
          ints.put(req.getId(), intsList);
        }
      }
    }
    return ints;
  }

  /**
   * Test required dependencies for a set of modules against an existing set.
   * @param available existing set of modules
   * @param testList modules whose dependencies we want to check
   * @return empty string if all OK; error message otherwise
   */
  public static String checkDependencies(Collection<ModuleDescriptor> available,
                                         Collection<ModuleDescriptor> testList) {
    Map<String, List<InterfaceDescriptor>> ints = getProvidedInterfaces(available);
    List<String> list = new LinkedList<>();
    for (ModuleDescriptor md : testList) {
      List<String> res = checkDependenciesInts(md, available, ints);
      list.addAll(res);
    }
    if (list.isEmpty()) {
      return "";
    } else {
      return String.join(". ", list);
    }
  }

  /**
   * Test required dependencies for a set of modules.
   * @param available existing set of modules to check
   * @return empty string if all OK; error message otherwise
   */
  public static String checkAllDependencies(Map<String, ModuleDescriptor> available) {
    Collection<ModuleDescriptor> testList = available.values();
    return checkDependencies(testList, testList);
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
          if (confl == null) {
            provs.put(mi.getId(), md.getId());
          } else {
            String msg = messages.getMessage("10202", mi.getId(), md.getId(), confl);
            conflicts.add(msg);
          }
        }
      }
    }
    return String.join(" ", conflicts);
  }

  private static TenantModuleDescriptor getNextTM(Map<String, ModuleDescriptor> modsEnabled,
                                                  List<TenantModuleDescriptor> tml) {

    Iterator<TenantModuleDescriptor> it = tml.iterator();
    TenantModuleDescriptor tm;
    while (it.hasNext()) {
      tm = it.next();
      TenantModuleDescriptor.Action action = tm.getAction();
      String id = tm.getId();
      if (logger.isInfoEnabled()) {
        logger.info("getNextTM: loop id {} action {}", id, action.name());
      }
      if (action == TenantModuleDescriptor.Action.enable && !modsEnabled.containsKey(id)) {
        logger.info("getNextMT: return tm for action enable");
        return tm;
      }
      if (action == TenantModuleDescriptor.Action.disable && modsEnabled.containsKey(id)) {
        logger.info("getNextTM: return tm for action disable");
        return tm;
      }
    }
    logger.info("getNextTM done null");
    return null;
  }

  /**
   * Install modules with dependency checking only.
   * @param modsAvailable available modules
   * @param modsEnabled enabled modules (for some tenant)
   * @param tml install list with actions
   * @return future
   */
  public static Future<Void> installSimulate(Map<String, ModuleDescriptor> modsAvailable,
                                             Map<String, ModuleDescriptor> modsEnabled,
                                             List<TenantModuleDescriptor> tml) {
    List<String> errors = new LinkedList<>();
    for (TenantModuleDescriptor tm : tml) {
      String id = tm.getId();
      ModuleId moduleId = new ModuleId(id);
      if (tm.getAction() == TenantModuleDescriptor.Action.enable) {
        if (!moduleId.hasSemVer()) {
          id = moduleId.getLatest(modsAvailable.keySet());
          tm.setId(id);
        } else {
          tm.setFixed(Boolean.TRUE);
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
      return Future.failedFuture(new OkapiError(ErrorType.USER,
          String.join(". ", errors)));
    }
    final int lim = tml.size();
    Future<Void> future = Future.succeededFuture();
    for (int i = 0; i <= lim; i++) {
      logger.info("outer loop i {} tml.size {}", i, tml.size());
      TenantModuleDescriptor tm = getNextTM(modsEnabled, tml);
      if (tm == null) {
        break;
      }
      future = future.compose(x -> tmAction(tm, modsAvailable, modsEnabled, tml));
    }
    return future.compose(x -> {
      upgradeLeafs(modsAvailable, modsEnabled, tml);
      String s = DepResolution.checkAllDependencies(modsEnabled);
      if (!s.isEmpty()) {
        logger.warn("installModules.checkAllDependencies: {}", s);
        return Future.failedFuture(new OkapiError(ErrorType.USER, s));
      }
      logger.info("installModules.returning OK");
      return Future.succeededFuture();
    });
  }

  private static Future<Void> tmAction(TenantModuleDescriptor tm,
                                       Map<String, ModuleDescriptor> modsAvailable,
                                       Map<String, ModuleDescriptor> modsEnabled,
                                       List<TenantModuleDescriptor> tml) {
    String id = tm.getId();
    TenantModuleDescriptor.Action action = tm.getAction();
    if (null == action) {
      return Future.failedFuture(new OkapiError(ErrorType.INTERNAL,
          messages.getMessage("10404", "null")));
    }
    switch (action) {
      case enable:
        return tmEnable(id, modsAvailable, modsEnabled, tml);
      case disable:
        return tmDisable(id, modsAvailable, modsEnabled, tml);
      default:
        return Future.failedFuture(new OkapiError(ErrorType.INTERNAL,
            messages.getMessage("10404", action.name())));
    }

  }

  private static Future<Void> tmEnable(
      String id, Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml) {

    List<String> ret = addModuleDependencies(modsAvailable.get(id), modsAvailable,
        modsEnabled, tml);
    if (ret.isEmpty()) {
      return Future.succeededFuture();
    }
    return Future.failedFuture(new OkapiError(ErrorType.USER, String.join(". ", ret)));
  }

  private static Future<Void> tmDisable(
      String id, Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml) {

    removeModuleDependencies(modsAvailable.get(id), modsEnabled, tml);
    return Future.succeededFuture();
  }

  private static List<String> checkInterfaceDependency(ModuleDescriptor md, InterfaceDescriptor req,
                                                       Map<String, ModuleDescriptor> modsAvailable,
                                                       Map<String, ModuleDescriptor> modsEnabled,
                                                       List<TenantModuleDescriptor> tml) {
    List<String> ret = new LinkedList<>();
    // check if mentioned already in other install action
    ModuleDescriptor foundMd = checkInterfaceDepOtherInstall(tml, modsAvailable, req);
    if (foundMd != null) {
      return addModuleDependencies(foundMd, modsAvailable, modsEnabled, tml);
    }
    Map<String, ModuleDescriptor> productMd = checkInterfaceDepAvailable(modsAvailable, req);
    if (productMd.isEmpty()) {
      ret.add(messages.getMessage("10211", req.getId(), md.getId()));
      return ret;
    } else if (productMd.size() == 1) {
      Set<String> s = productMd.keySet();
      String k = s.iterator().next();
      foundMd = productMd.get(k);
    } else {
      ret.add(messages.getMessage("10210", req.getId(), md.getId(),
          String.join(", ", productMd.keySet())));
      return ret;
    }
    return addModuleDependencies(foundMd, modsAvailable, modsEnabled, tml);
  }

  private static Map<String, ModuleDescriptor> checkInterfaceDepAvailable(
      Map<String, ModuleDescriptor> modsAvailable, InterfaceDescriptor req) {
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
            ModuleDescriptor md2 = productMd.get(product);
            if (md.compareTo(md2) > 0) {
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

  private static ModuleDescriptor checkInterfaceDepOtherInstall(
      List<TenantModuleDescriptor> tml, Map<String, ModuleDescriptor> modsAvailable,
      InterfaceDescriptor req) {
    ModuleDescriptor foundMd = null;
    Iterator<TenantModuleDescriptor> it = tml.iterator();
    while (it.hasNext()) {
      TenantModuleDescriptor tm = it.next();
      ModuleDescriptor md = modsAvailable.get(tm.getId());
      if (md != null && tm.getAction() == TenantModuleDescriptor.Action.enable) {
        for (InterfaceDescriptor pi : md.getProvidesList()) {
          if (pi.isRegularHandler() && pi.isCompatible(req)) {
            it.remove();
            logger.info("Dependency OK for existing enable id {}", md.getId());
            foundMd = md;
          }
        }
      }
    }
    return foundMd;
  }

  /**
   * Check whether interface is provided for a set of enabled modules.
   * @param modsEnabled set of enabled modules
   * @param req the interface to look for
   * @return null: interface not found, false: incompatible, true: compatible
   */
  private static Boolean checkInterfaceDepAlreadyEnabled(
      Map<String, ModuleDescriptor> modsEnabled, InterfaceDescriptor req) {

    Boolean exist = null;
    for (ModuleDescriptor md : modsEnabled.values()) {
      for (InterfaceDescriptor pi : md.getProvidesList()) {
        if (pi.isRegularHandler() && pi.getId().equals(req.getId())) {
          if (pi.isCompatible(req)) {
            logger.info("Dependency OK already enabled id {}", md.getId());
            return true;
          }
          exist = false;
        }
      }
    }
    return exist;
  }

  private static List<String> resolveModuleConflicts(
      ModuleDescriptor md, Map<String, ModuleDescriptor> modsEnabled,
      List<TenantModuleDescriptor> tml, List<ModuleDescriptor> fromModule) {
    Iterator<String> it = modsEnabled.keySet().iterator();
    List<String> errors = new LinkedList<>();
    while (it.hasNext()) {
      String runningModule = it.next();
      ModuleDescriptor rm = modsEnabled.get(runningModule);
      if (md.getProduct().equals(rm.getProduct())) {
        logger.info("resolveModuleConflicts from {}", runningModule);
        for (TenantModuleDescriptor tm : tml) {
          if (tm.getId().equals(rm.getId()) && Boolean.TRUE.equals(tm.getFixed())) {
            errors.add(messages.getMessage("10209", rm.getId()));
            return errors;
          }
        }
        it.remove();
        fromModule.add(rm);
      } else {
        for (InterfaceDescriptor mi : md.getProvidesList()) {
          for (InterfaceDescriptor pi : rm.getProvidesList()) {
            if (pi.isRegularHandler()) {
              String confl = pi.getId();
              if (mi.getId().equals(confl)
                  && mi.isRegularHandler()
                  && modsEnabled.containsKey(runningModule)) {
                logger.info("resolveModuleConflicts remove {}", runningModule);
                TenantModuleDescriptor tm = new TenantModuleDescriptor();
                tm.setAction(TenantModuleDescriptor.Action.disable);
                tm.setId(runningModule);
                tml.add(tm);
                it.remove();
              }
            }
          }
        }
      }
    }
    return errors;
  }

  private static void addOrReplace(List<TenantModuleDescriptor> tml, ModuleDescriptor md,
                                   TenantModuleDescriptor.Action action,
                                   ModuleDescriptor fm) {
    logger.info("addOrReplace from {} to id {}", fm != null ? fm.getId() : "null", md.getId());
    Iterator<TenantModuleDescriptor> it = tml.iterator();
    boolean found = false;
    boolean fixed = false;
    while (it.hasNext()) {
      TenantModuleDescriptor tm = it.next();
      if (tm.getAction().equals(action) && tm.getId().equals(md.getId())) {
        fixed = tm.getFixed();
        it.remove();
      } else if (fm != null && tm.getAction() == TenantModuleDescriptor.Action.enable
          && tm.getId().equals(fm.getId())) {
        logger.info("addOrReplace .. patch id {}", md.getId());
        tm.setId(md.getId());
        found = true;
      }
    }
    if (found) {
      return;
    }
    TenantModuleDescriptor t = new TenantModuleDescriptor();
    t.setAction(action);
    t.setFixed(fixed);
    t.setId(md.getId());
    if (fm != null) {
      t.setFrom(fm.getId());
    }
    tml.add(t);
  }

  private static void upgradeLeafs(
      Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml) {
    while (upgradeLeafs2(modsAvailable, modsEnabled, tml)) {
      // something upgraded.. try again
    }
  }

  private static boolean upgradeLeafs2(
      Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml) {

    for (ModuleDescriptor md : modsEnabled.values()) {
      for (ModuleDescriptor me : modsEnabled.values()) {
        ModuleDescriptor mdTo = null;
        for (InterfaceDescriptor prov : md.getProvidesList()) {
          for (InterfaceDescriptor req : me.getRequiresOptionalList()) {
            if (prov.getId().equals(req.getId()) && !prov.isCompatible(req)) {
              mdTo = lookupAvailableForProvided(modsAvailable, me, prov, mdTo);
            }
          }
        }
        if (mdTo != null) {
          return addModuleDependencies(mdTo, modsAvailable, modsEnabled, tml).isEmpty();
        }
      }
    }
    return false;
  }

  private static ModuleDescriptor lookupAvailableForProvided(
      Map<String, ModuleDescriptor> modsAvailable,
      ModuleDescriptor me, InterfaceDescriptor prov, ModuleDescriptor mdTo) {
    for (ModuleDescriptor ma : modsAvailable.values()) {
      if (!me.getProduct().equals(ma.getProduct())) {
        continue;
      }
      for (InterfaceDescriptor re1 : ma.getRequiresOptionalList()) {
        if (prov.isCompatible(re1) && (mdTo == null || ma.compareTo(mdTo) > 0)) {
          mdTo = ma;
        }
      }
    }
    return mdTo;
  }

  private static List<String> addModuleDependencies(
      ModuleDescriptor md, Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml) {
    List<String> errors = new LinkedList<>();
    logger.info("addModuleDependencies {}", md.getId());
    for (InterfaceDescriptor req : md.getRequiresList()) {
      Boolean exist = checkInterfaceDepAlreadyEnabled(modsEnabled, req);
      if (!Boolean.TRUE.equals(exist)) {
        errors.addAll(checkInterfaceDependency(md, req, modsAvailable, modsEnabled, tml));
      }
    }
    for (InterfaceDescriptor req : md.getOptionalList()) {
      Boolean exist = checkInterfaceDepAlreadyEnabled(modsEnabled, req);
      if (Boolean.FALSE.equals(exist)) {
        errors.addAll(checkInterfaceDependency(md, req, modsAvailable, modsEnabled, tml));
      }
    }
    if (!errors.isEmpty()) {
      return errors;
    }
    List<ModuleDescriptor> fromModule = new LinkedList<>();
    errors = resolveModuleConflicts(md, modsEnabled, tml, fromModule);
    if (!errors.isEmpty()) {
      return errors;
    }
    modsEnabled.put(md.getId(), md);
    addOrReplace(tml, md, TenantModuleDescriptor.Action.enable,
        fromModule.isEmpty() ? null : fromModule.get(0));
    return errors;
  }

  private static void removeModuleDependencies(
      ModuleDescriptor md, Map<String, ModuleDescriptor> modsEnabled,
      List<TenantModuleDescriptor> tml) {

    if (modsEnabled.containsKey(md.getId())) {
      InterfaceDescriptor[] provides = md.getProvidesList();
      for (InterfaceDescriptor prov : provides) {
        if (prov.isRegularHandler()) {
          Iterator<String> it = modsEnabled.keySet().iterator();
          while (it.hasNext()) {
            String runningModule = it.next();
            ModuleDescriptor rm = modsEnabled.get(runningModule);
            InterfaceDescriptor[] requires = rm.getRequiresList();
            for (InterfaceDescriptor ri : requires) {
              if (prov.getId().equals(ri.getId())) {
                removeModuleDependencies(rm, modsEnabled, tml);
                it = modsEnabled.keySet().iterator();
              }
            }
          }
        }
      }
      modsEnabled.remove(md.getId());
      addOrReplace(tml, md, TenantModuleDescriptor.Action.disable, null);
    }
  }

  /**
   * Return top-N set of modules - in order of module ID.
   * @param limit max number for each module (Top-N)
   * @param mdl modules to consider
   * @return list with Top-N set
   */
  public static List<ModuleDescriptor> getLatestProducts(int limit, List<ModuleDescriptor> mdl) {
    mdl.sort(Collections.reverseOrder());
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
