package org.folio.okapi.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.OkapiLogger;

public final class DepResolution {

  private static final Logger logger = OkapiLogger.get();
  private static final Messages messages = Messages.getInstance();

  private DepResolution() {
    throw new UnsupportedOperationException("DepResolution");
  }

  /**
   * Test required dependencies for a set of modules.
   * @param available existing set of modules to check
   * @return empty string if all OK; error message otherwise
   */
  public static String checkAvailable(Map<String, ModuleDescriptor> available) {
    Collection<ModuleDescriptor> testList = available.values();
    return checkAvailable(testList, testList, false);
  }

  /**
   * Test required dependencies for a set of modules against an existing set.
   * @param available existing set of modules
   * @param testList modules whose dependencies we want to check
   * @param removeIfMissingDep remove from testList if dependency check fails
   * @return empty string if all OK; error message otherwise
   */
  public static String checkAvailable(Collection<ModuleDescriptor> available,
      Collection<ModuleDescriptor> testList, boolean removeIfMissingDep) {

    Map<String, List<InterfaceDescriptor>> ints = getProvidedInterfaces(available);
    List<String> list = new LinkedList<>();

    Iterator<ModuleDescriptor> iterator = testList.iterator();
    while (iterator.hasNext()) {
      ModuleDescriptor md = iterator.next();
      List<String> res = checkDependenciesInts(md, available, ints);
      if (!res.isEmpty()) {
        if (removeIfMissingDep) {
          available.remove(md);
          ints = getProvidedInterfaces(available);
          iterator.remove();
          iterator = testList.iterator();
        } else {
          list.addAll(res);
        }
      }
    }
    if (list.isEmpty()) {
      return "";
    } else {
      return String.join("\n", list);
    }
  }

  /**
   * Test interfaces for a set of enabled modules.
   * @param modsEnabled enabled modules
   * @return list of errors ; empty list if not error(s)
   */
  public static List<String> checkEnabled(Map<String, ModuleDescriptor> modsEnabled) {
    return interfaceCheck(modsEnabled, modsEnabled, Collections.emptyList(), false,
        new HashSet<>());
  }

  /**
   * Install modules with dependency checking only.
   * @param modsAvailable available modules
   * @param modsEnabled enabled modules (for some tenant)
   * @param tml install list with actions
   * @param reinstall whether to re-install
   */
  public static void install(Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
      boolean reinstall) {
    installMaxIterations(modsAvailable, modsEnabled, tml, reinstall, 100);
  }

  /**
   * Return top-N set of modules - in order of module ID.
   * @param limit max number for each module (Top-N)
   * @param mdl modules to consider (will be modified!)
   */
  public static void getLatestProducts(int limit, List<ModuleDescriptor> mdl) {
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
  }

  static boolean moduleDepProvided(List<ModuleDescriptor> modules, ModuleDescriptor md) {
    List<InterfaceDescriptor> requiresOptionalList = md.getRequiresOptionalList();
    for (InterfaceDescriptor req : requiresOptionalList) {
      boolean found = false;
      for (ModuleDescriptor md1 : modules) {
        InterfaceDescriptor[] providesList = md1.getProvidesList();
        for (InterfaceDescriptor prov : providesList) {
          if (prov.isCompatible(req)) {
            found = true;
          }
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  static void topoSort(List<ModuleDescriptor> modules) {
    List<ModuleDescriptor> result = new LinkedList<>();

    boolean more = true;
    while (more) {
      more = false;
      Iterator<ModuleDescriptor> iterator = modules.iterator();
      while (iterator.hasNext()) {
        ModuleDescriptor md = iterator.next();
        if (moduleDepProvided(result, md)) {
          result.add(md);
          iterator.remove();
          more = true;
        }
      }
    }
    // add remaining result to modules (such as modules with optional interfaces requirements
    modules.addAll(0, result);
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

  static class ModuleInterface {
    final ModuleDescriptor moduleDescriptor;
    final InterfaceDescriptor interfaceDescriptor;

    ModuleInterface(ModuleDescriptor md, InterfaceDescriptor id) {
      this.moduleDescriptor = md;
      this.interfaceDescriptor = id;
    }
  }


  private static void sortTenantModules(List<TenantModuleDescriptor> tml,
      Map<String, ModuleDescriptor> modsAvailable, Map<String, ModuleDescriptor> modsEnabled) {

    Set<String> added = new HashSet<>();
    // make a list of all modules involved.. also those removed.
    LinkedList<ModuleDescriptor> sortedList = new LinkedList<>();
    for (TenantModuleDescriptor tm: tml) {
      if (tm.getAction() == TenantModuleDescriptor.Action.enable
          || tm.getAction() == TenantModuleDescriptor.Action.uptodate
          || tm.getAction() == TenantModuleDescriptor.Action.disable) {
        sortedList.add(modsAvailable.get(tm.getId()));
        added.add(tm.getId());
      }
    }
    for (ModuleDescriptor md : modsEnabled.values()) {
      if (!added.contains(md.getId())) {
        sortedList.add(md);
        added.add(md.getId());
      }
    }
    topoSort(sortedList);
    // we now have a list where things mentioned in the install comes first.. Thus, if
    // for cases where there are different orders satisfying dependencies, the order in the
    // install is honored and will be listed first.
    logger.info("Topo sort result {}", () ->
        sortedList.stream()
        .map(ModuleDescriptor::getId)
            .collect(Collectors.joining(", ")));

    logger.info("Input install list {}", () ->
        tml.stream()
            .map(TenantModuleDescriptor::getId)
            .collect(Collectors.joining(", ")));

    List<TenantModuleDescriptor> tml2 = new ArrayList<>();

    // go through disabled modules
    Iterator<ModuleDescriptor> moduleIterator = sortedList.descendingIterator();
    while (moduleIterator.hasNext()) {
      ModuleDescriptor md = moduleIterator.next();
      Iterator<TenantModuleDescriptor> iterator = tml.iterator();
      while (iterator.hasNext()) {
        TenantModuleDescriptor tm = iterator.next();
        if (tm.getAction() == TenantModuleDescriptor.Action.disable) {
          String id = tm.getId();
          if (id.equals(md.getId())) {
            tml2.add(tm);
            iterator.remove();
          }
        }
      }
    }

    // go through enabled/updated modules
    moduleIterator = sortedList.iterator();
    while (moduleIterator.hasNext()) {
      ModuleDescriptor md = moduleIterator.next();
      Iterator<TenantModuleDescriptor> iterator = tml.iterator();
      while (iterator.hasNext()) {
        TenantModuleDescriptor tm = iterator.next();
        if (tm.getAction() == TenantModuleDescriptor.Action.enable
            || tm.getAction() == TenantModuleDescriptor.Action.uptodate) {
          String id = tm.getId();
          if (id.equals(md.getId())) {
            tml2.add(tm);
            iterator.remove();
          }
        }
      }
    }
    tml2.addAll(tml);
    // result in tml2.. transfer to tml
    tml.clear();
    tml.addAll(tml2);
  }

  private static void addTenantModule(List<TenantModuleDescriptor> tml, String id, String from,
      TenantModuleDescriptor.Action action) {
    if (from != null && action == TenantModuleDescriptor.Action.enable && id != from) {
      if (!new ModuleId(id).getProduct().equals(new ModuleId(from).getProduct())) {
        TenantModuleDescriptor tm = new TenantModuleDescriptor();
        tm.setAction(TenantModuleDescriptor.Action.disable);
        tm.setId(from);
        tml.add(tm);
        from = null;
      }
    }
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setId(id);
    tm.setFrom(from);
    tm.setAction(action);
    tml.add(tm);
  }

  static List<String> interfaceCheck(Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml, boolean fix,
      Set<String> stickyModules) {

    List<String> errors = new LinkedList<>();
    Map<String,List<ModuleInterface>> providedInterfaces = new HashMap<>();
    Map<String,List<ModuleInterface>> requiredInterfaces = new HashMap<>();
    Map<String,List<ModuleInterface>> requiredOptInterfaces = new HashMap<>();
    for (Map.Entry<String,ModuleDescriptor> entry: modsEnabled.entrySet()) {
      ModuleDescriptor md = entry.getValue();
      for (InterfaceDescriptor descriptor: md.getProvidesList()) {
        if (descriptor.isRegularHandler()) {
          ModuleInterface moduleInterface = new ModuleInterface(md, descriptor);
          providedInterfaces.computeIfAbsent(descriptor.getId(), k -> new ArrayList<>())
              .add(moduleInterface);
        }
      }
      for (InterfaceDescriptor interfaceDescriptor: md.getRequiresList()) {
        ModuleInterface moduleInterface = new ModuleInterface(md, interfaceDescriptor);
        requiredInterfaces.computeIfAbsent(interfaceDescriptor.getId(), k -> new ArrayList<>())
            .add(moduleInterface);
      }
      for (InterfaceDescriptor interfaceDescriptor: md.getRequiresOptionalList()) {
        ModuleInterface moduleInterface = new ModuleInterface(md, interfaceDescriptor);
        requiredOptInterfaces.computeIfAbsent(interfaceDescriptor.getId(), k -> new ArrayList<>())
            .add(moduleInterface);
      }
    }
    if (checkMultiple(modsEnabled, tml, fix, errors, providedInterfaces, stickyModules)) {
      return null;
    }
    if (checkRequired(modsAvailable, modsEnabled, tml, fix, errors,
        providedInterfaces, requiredInterfaces, stickyModules)) {
      return null;
    }
    if (checkCompatible(modsAvailable, modsEnabled, tml, fix, errors,
        providedInterfaces, requiredOptInterfaces, stickyModules)) {
      return null;
    }
    return errors;
  }

  private static boolean checkMultiple(Map<String, ModuleDescriptor> modsEnabled,
      List<TenantModuleDescriptor> tml, boolean fix, List<String> errors,
      Map<String, List<ModuleInterface>> providedInterfaces, Set<String> stickyModules) {

    for (Map.Entry<String,List<ModuleInterface>> entry : providedInterfaces.entrySet()) {
      if (entry.getValue().size() > 1) {
        logger.info("Interface {} is defined {} times",
            entry.getKey(), entry.getValue().size());
        if (fix) {
          for (ModuleInterface ent : entry.getValue()) {
            ModuleDescriptor md = ent.moduleDescriptor;
            if (!stickyModules.contains(md.getId())) {
              logger.info("Disable module {}", md.getId());
              modsEnabled.remove(md.getId());
              addTenantModule(tml, md.getId(), null, TenantModuleDescriptor.Action.disable);
              return true;
            }
          }
        }
        String modules = entry.getValue().stream()
            .map(x -> x.moduleDescriptor.getId())
            .collect(Collectors.joining(", "));
        errors.add(messages.getMessage("10213", modules, entry.getKey()));
      }
    }
    return false;
  }

  private static boolean checkRequired(Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
      boolean fix, List<String> errors, Map<String, List<ModuleInterface>> providedInterfaces,
      Map<String, List<ModuleInterface>> requiredInterfaces, Set<String> stickyModules) {

    for (Map.Entry<String, List<ModuleInterface>> entry : requiredInterfaces.entrySet()) {
      List<ModuleInterface> providedModuleInterfaces = providedInterfaces.get(entry.getKey());
      if (providedModuleInterfaces == null) {
        logger.info("Interface {} undefined and required", entry.getKey());
        for (ModuleInterface req : entry.getValue()) {
          if (fix) {
            Map<String, ModuleDescriptor> modules =
                checkInterfaceDepAvailable(modsAvailable, req.interfaceDescriptor);
            if (modules.size() > 1) {
              errors.add(messages.getMessage("10210", entry.getKey(), req.moduleDescriptor.getId(),
                  String.join(", ", modules.keySet())));
              return false;
            } else if (!modules.isEmpty()) {
              ModuleDescriptor mdFound = modules.values().iterator().next();
              String id = req.moduleDescriptor.getId();
              if (stickyModules.contains(req.moduleDescriptor.getId())) {
                logger.info("Enable {}", mdFound.getId());
                modsEnabled.put(mdFound.getId(), mdFound);
                addTenantModule(tml, mdFound.getId(), null, TenantModuleDescriptor.Action.enable);
                stickyModules.add(mdFound.getId());
              } else {
                logger.info("Removing {}", id);
                modsEnabled.remove(id);
                addTenantModule(tml, id, null, TenantModuleDescriptor.Action.disable);
              }
              return true;
            }
          }
          errors.add(messages.getMessage("10211", entry.getKey(), req.moduleDescriptor.getId()));
        }
      }
    }
    return false;
  }

  private static boolean checkCompatible(Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
      boolean fix, List<String> errors, Map<String, List<ModuleInterface>> providedInterfaces,
      Map<String, List<ModuleInterface>> requiredOptInterfaces, Set<String> stickyModules) {

    for (Map.Entry<String,List<ModuleInterface>> entry : requiredOptInterfaces.entrySet()) {
      List<ModuleInterface> providedModuleInterfaces = providedInterfaces.get(entry.getKey());
      if (providedModuleInterfaces != null) {
        ModuleInterface prov = providedModuleInterfaces.get(0);
        for (ModuleInterface req : entry.getValue()) {
          if (!prov.interfaceDescriptor.isCompatible(req.interfaceDescriptor)) {
            logger.info("Interface prov={}:{}/{} req={}:{}/{} not compatible",
                prov.interfaceDescriptor.getId(), prov.interfaceDescriptor.getVersion(),
                prov.moduleDescriptor.getId(),
                req.interfaceDescriptor.getId(), req.interfaceDescriptor.getVersion(),
                req.moduleDescriptor.getId());
            if (fix) {
              if (!stickyModules.contains(prov.moduleDescriptor.getId())) {
                // see if we can find a module that provides the required interface
                Map<String, ModuleDescriptor> modules =
                    checkInterfaceDepAvailable(modsAvailable, req.interfaceDescriptor);
                if (modules.size() > 1) {
                  errors.add(messages.getMessage("10210", entry.getKey(),
                      req.moduleDescriptor.getId(),
                      String.join(", ", modules.keySet())));
                  return false;
                } else if (!modules.isEmpty()) {
                  ModuleDescriptor mdFound = modules.values().iterator().next();
                  String from = prov.moduleDescriptor.getId();
                  stickyModules.add(mdFound.getId());
                  logger.info("Adding 1 to={} from={}", mdFound.getId(), from);
                  modsEnabled.remove(from);
                  modsEnabled.put(mdFound.getId(), mdFound);
                  addTenantModule(tml, mdFound.getId(), from, TenantModuleDescriptor.Action.enable);
                  return true;
                }
              }
              if (!stickyModules.contains(req.moduleDescriptor.getId())) {
                // see if we can find a module that require the provided interface
                ModuleDescriptor mdFound = null;
                Set<String> products = new HashSet<>();
                for (Map.Entry<String, ModuleDescriptor> ent : modsAvailable.entrySet()) {
                  ModuleDescriptor md = ent.getValue();
                  for (InterfaceDescriptor interfaceDescriptor : md.getRequiresOptionalList()) {
                    if (prov.interfaceDescriptor.isCompatible(interfaceDescriptor)) {
                      logger.info("Found candidate module {}", md.getId());
                      products.add(md.getProduct());
                      if (mdFound == null || md.compareTo(mdFound) > 0) {
                        mdFound = md;
                      }
                    }
                  }
                }
                if (mdFound != null) {
                  if (products.size() > 1) {
                    errors.add(messages.getMessage("10210", entry.getKey(),
                        req.moduleDescriptor.getId(), String.join(", ", products)));
                    return false;
                  } else {
                    String from = req.moduleDescriptor.getId();
                    stickyModules.add(mdFound.getId());
                    logger.info("Adding 2 to={} from={}", mdFound.getId(), from);
                    modsEnabled.remove(from);
                    modsEnabled.put(mdFound.getId(), mdFound);
                    addTenantModule(tml, mdFound.getId(), from,
                        TenantModuleDescriptor.Action.enable);
                    return true;
                  }
                }
              }
            }
            errors.add(messages.getMessage("10201", req.moduleDescriptor.getId(), entry.getKey(),
                req.interfaceDescriptor.getVersion(),
                prov.interfaceDescriptor.getVersion() + "/" + prov.moduleDescriptor.getId()));
          }
        }
      }
    }
    return false;
  }


  /**
   * Install modules with dependency checking only.
   * @param modsAvailable available modules
   * @param modsEnabled enabled modules (for some tenant)
   * @param tml install list with actions
   * @param reinstall whether to re-install
   * @param maxIterations how many iterations to allow fixup of list.
   */
  static void installMaxIterations(Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
      boolean reinstall, int maxIterations) {

    List<String> errors = new LinkedList<>();
    Set<String> stickyModules = new HashSet<>();
    for (TenantModuleDescriptor tm : tml) {
      String id = tm.getId();
      ModuleId moduleId = new ModuleId(id);
      if (tm.getAction() == TenantModuleDescriptor.Action.enable) {
        if (!moduleId.hasSemVer()) {
          id = moduleId.getLatest(modsAvailable.keySet());
          tm.setId(id);
          stickyModules.add(id);
        } else {
          stickyModules.add(id);
        }
        if (!modsAvailable.containsKey(id)) {
          errors.add(messages.getMessage("10801", id));
        } else {
          if (modsEnabled.containsKey(id) && !reinstall) {
            tm.setAction(TenantModuleDescriptor.Action.uptodate);
          } else {
            String product = new ModuleId(id).getProduct();
            for (String enabledId : modsEnabled.keySet()) {
              String enabledProduct = new ModuleId(enabledId).getProduct();
              if (enabledProduct.equals(product)) {
                if (stickyModules.contains(enabledId) && !id.equals(enabledId)) {
                  errors.add(messages.getMessage("10209", enabledId));
                }
                modsEnabled.remove(enabledId);
                tm.setFrom(enabledId);
                break;
              }
            }
            modsEnabled.put(id, modsAvailable.get(id));
          }
        }
      }
      if (tm.getAction() == TenantModuleDescriptor.Action.disable) {
        if (!moduleId.hasSemVer()) {
          id = moduleId.getLatest(modsEnabled.keySet());
          tm.setId(id);
        }
        if (!modsEnabled.containsKey(id)) {
          errors.add(messages.getMessage("10801", id));
        } else {
          modsEnabled.remove(id);
        }
      }
    }
    if (maxIterations == 0) {
      errors = interfaceCheck(modsAvailable, modsEnabled, tml, false, stickyModules);
    } else {
      for (int i = 0; i < maxIterations && (errors == null || errors.isEmpty()); i++) {
        errors = interfaceCheck(modsAvailable, modsEnabled, tml, true, stickyModules);
      }
    }
    if (errors == null) {
      throw new OkapiError(ErrorType.INTERNAL,
          "Dependency resolution not completing in " + maxIterations + " iterations");
    } else if (!errors.isEmpty()) {
      throw new OkapiError(ErrorType.USER, String.join(". ", errors));
    }
    sortTenantModules(tml, modsAvailable, modsEnabled);
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

}
