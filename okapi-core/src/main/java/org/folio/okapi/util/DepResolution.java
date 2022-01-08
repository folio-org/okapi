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
    if (!modules.isEmpty()) {
      logger.info("Unsorted modules because dependencies failed {}",
          modules.stream().map(ModuleDescriptor::getId).collect(Collectors.joining(", ")));
    }
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

  /**
   * Test required dependencies for a set of modules against an existing set.
   * @param available existing set of modules
   * @param testList modules whose dependencies we want to check
   * @param removeIfMissingDep remove from testList if dependency check fails
   * @return empty string if all OK; error message otherwise
   */
  public static String checkDependencies(Collection<ModuleDescriptor> available,
                                         Collection<ModuleDescriptor> testList,
                                         boolean removeIfMissingDep) {
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
   * Test required dependencies for a set of modules.
   * @param available existing set of modules to check
   * @return empty string if all OK; error message otherwise
   */
  public static String checkAllDependencies(Map<String, ModuleDescriptor> available) {
    Collection<ModuleDescriptor> testList = available.values();
    return checkDependencies(testList, testList, false);
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
    logger.info("Topo sort result {}", () ->
        sortedList.stream()
        .map(ModuleDescriptor::getId)
            .collect(Collectors.joining(", ")));

    logger.info("Input install list {}", () ->
        tml.stream()
            .map(TenantModuleDescriptor::getId)
            .collect(Collectors.joining(", ")));

    List<TenantModuleDescriptor> tml2 = new ArrayList<>();

    Iterator<ModuleDescriptor> moduleIterator = sortedList.descendingIterator();
    // go through disabled modules
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
    tml.clear();
    tml.addAll(tml2);
  }

  private static void addTenantModule(List<TenantModuleDescriptor> tml, String id, String from,
      TenantModuleDescriptor.Action action) {
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setId(id);
    tm.setFrom(from);
    tm.setAction(action);
    tml.add(tm);
  }

  private static List<String> interfaceCheck(Map<String, ModuleDescriptor> modsAvailable,
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
    for (Map.Entry<String,List<ModuleInterface>> entry : providedInterfaces.entrySet()) {
      if (entry.getValue().size() > 1) {
        logger.info("Interface {} is defined {} times",
            entry.getKey(), entry.getValue().size());
        if (fix) {
          for (ModuleInterface ent : entry.getValue()) {
            ModuleDescriptor md = ent.moduleDescriptor;
            for (TenantModuleDescriptor tm : tml) {
              if (tm.getAction().equals(TenantModuleDescriptor.Action.enable)
                  && tm.getId().equals(md.getId()) && tm.getFrom() == null) {
                for (ModuleInterface ent1 : entry.getValue()) {
                  ModuleDescriptor md1 = ent1.moduleDescriptor;
                  if (!md1.getId().equals(md.getId()) && md1.getProduct().equals(md.getProduct())) {
                    tm.setFrom(md.getId());
                    modsEnabled.remove(md.getId());
                    logger.info("Disable by adding from {}", md.getId());
                    return null;
                  }
                }
              }
            }
            boolean mayDisable = true;
            for (TenantModuleDescriptor tm : tml) {
              if (tm.getAction().equals(TenantModuleDescriptor.Action.enable)
                  && tm.getId().equals(md.getId())) {
                mayDisable = false;
              }
            }
            if (mayDisable) {
              logger.info("Disable module {}", md.getId());
              modsEnabled.remove(md.getId());
              addTenantModule(tml, md.getId(), null, TenantModuleDescriptor.Action.disable);
              return null;
            }
          }
        }
        String modules = entry.getValue().stream()
            .map(x -> x.moduleDescriptor.getId())
            .collect(Collectors.joining(", "));
        errors.add(messages.getMessage("10213", modules, entry.getKey()));
      }
    }
    for (Map.Entry<String,List<ModuleInterface>> entry : requiredInterfaces.entrySet()) {
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
              return errors;
            } else if (!modules.isEmpty()) {
              ModuleDescriptor mdFound = modules.values().iterator().next();
              String id = req.moduleDescriptor.getId();
              boolean mayDisable = true;
              for (TenantModuleDescriptor tm : tml) {
                if (tm.getId().equals(req.moduleDescriptor.getId())
                    && tm.getAction().equals(TenantModuleDescriptor.Action.enable)) {
                  mayDisable = false;
                }
              }
              if (mayDisable) {
                logger.info("Removing {}", id);
                modsEnabled.remove(id);
                addTenantModule(tml, id, null, TenantModuleDescriptor.Action.disable);
                return null;
              } else {
                logger.info("Enable {}", mdFound.getId());
                modsEnabled.put(mdFound.getId(), mdFound);
                addTenantModule(tml, mdFound.getId(), null, TenantModuleDescriptor.Action.enable);
                return null;
              }
            }
          }
          errors.add(messages.getMessage("10211", entry.getKey(), req.moduleDescriptor.getId()));
        }
      }
    }
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
                Map<String, ModuleDescriptor> modules =
                    checkInterfaceDepAvailable(modsAvailable, req.interfaceDescriptor);
                if (modules.size() > 1) {
                  errors.add(messages.getMessage("10210", entry.getKey(),
                      req.moduleDescriptor.getId(),
                      String.join(", ", modules.keySet())));
                  return errors;
                } else if (!modules.isEmpty()) {
                  ModuleDescriptor mdFound = modules.values().iterator().next();
                  String from = prov.moduleDescriptor.getId();
                  stickyModules.add(mdFound.getId());
                  logger.info("Adding to={} from={}", mdFound.getId(), from);
                  modsEnabled.remove(from);
                  modsEnabled.put(mdFound.getId(), mdFound);
                  addTenantModule(tml, mdFound.getId(), from, TenantModuleDescriptor.Action.enable);
                  return null;
                }
              }
              if (!stickyModules.contains(req.moduleDescriptor.getId())) {
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
                    return errors;
                  }
                  String from = req.moduleDescriptor.getId();
                  logger.info("Adding to={} from={}", mdFound.getId(), from);
                  modsEnabled.remove(from);
                  modsEnabled.put(mdFound.getId(), mdFound);
                  addTenantModule(tml, mdFound.getId(), from, TenantModuleDescriptor.Action.enable);
                  return null;
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
    return errors;
  }

  /**
   * Install modules with dependency checking only.
   * @param modsAvailable available modules
   * @param modsEnabled enabled modules (for some tenant)
   * @param tml install list with actions
   * @param reinstall whether to re-install
   */
  public static void installSimulate(Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled,
      List<TenantModuleDescriptor> tml,
      boolean reinstall) {
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
    for (int i = 0; i < 10 && (errors == null || errors.isEmpty()); i++) {
      errors = interfaceCheck(modsAvailable, modsEnabled, tml, true, stickyModules);
    }
    if (errors == null) {
      throw new OkapiError(ErrorType.INTERNAL,
          "resolve not completing in 10 iterations");
    }
    if (!errors.isEmpty()) {
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
}
