package org.folio.okapi.util;

import java.util.ArrayList;
import java.util.Arrays;
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
   *
   * @param available          existing set of modules
   * @param testList           modules whose dependencies we want to check
   * @param removeIfMissingDep remove from testList if dependency check fails
   * @return empty string if all OK; error message otherwise
   */
  public static String checkAvailable(
      Collection<ModuleDescriptor> available,
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
   *
   * @param modsEnabled enabled modules
   * @return list of errors ; empty list if not error(s)
   */
  public static List<String> checkEnabled(Map<String, ModuleDescriptor> modsEnabled) {
    return checkEnabledModules(modsEnabled, modsEnabled, Collections.emptyList(), false,
        new HashSet<>());
  }

  /**
   * Install modules with dependency checking only.
   *
   * @param modsAvailable available modules
   * @param modsEnabled   enabled modules (for some tenant)
   * @param tml           install list with actions
   * @param reinstall     whether to re-install
   */
  public static void install(
      Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
      boolean reinstall) {
    installMaxIterations(modsAvailable, modsEnabled, tml, reinstall, 100);
  }

  /**
   * Sort mdl in descending order and remove all but the top-N for each product.
   *
   * @param limit max number for each module (Top-N)
   * @param mdl   modules to consider (will be modified!)
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

  /**
   * Check if md's required interfaces are provided by set of modules.
   *
   * @param modules     the modules to check against
   * @param md          module to check.
   * @return            true if interface requirements are met; false otherwise
   */
  public static boolean moduleDepProvided(
      Collection<ModuleDescriptor> modules, ModuleDescriptor md) {

    interfaceDescriptor:
    for (InterfaceDescriptor req : md.getRequiresList()) {
      for (ModuleDescriptor md1 : modules) {
        InterfaceDescriptor[] providesList = md1.getProvidesList();
        for (InterfaceDescriptor prov : providesList) {
          if (prov.isRegularHandler() && prov.isCompatible(req)) {
            continue interfaceDescriptor;
          }
        }
      }
      return false;
    }
    return true;
  }

  /**
   * Check if any of md's provided interfaces are used by any of the modules.
   * <p>A interface is used if it's a required or optional interface dependency.</p>
   *
   * @param modules     the modules to check against
   * @param md          module to check
   * @return            true if modules do not use any of the provided interfaces; false otherwise
   */
  public static boolean moduleDepRequired(Collection<ModuleDescriptor> modules,
      ModuleDescriptor md) {

    for (InterfaceDescriptor prov : md.getProvidesList()) {
      if (!prov.isRegularHandler()) {
        continue;
      }
      for (ModuleDescriptor md1 : modules) {
        List<InterfaceDescriptor> requiresList = md1.getRequiresOptionalList();
        for (InterfaceDescriptor req : requiresList) {
          if (prov.isCompatible(req)) {
            return false;
          }
        }
      }
    }
    return true;
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
   * @param md   module to check
   * @param req  required dependency
   * @param ints the list to provided interface as returned by getProvidedInterfaces
   * @return null if ok, or error message
   */
  private static String checkOneDependency(
      ModuleDescriptor md, InterfaceDescriptor req,
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

  private static void addTenantModule(
      List<TenantModuleDescriptor> tml, String id, String from,
      TenantModuleDescriptor.Action action) {
    if (action == TenantModuleDescriptor.Action.enable && from != null
        && !new ModuleId(id).getProduct().equals(new ModuleId(from).getProduct())) {
      // upgrading between two different products.. so this upgrade is turned into
      // a disable, then en enable
      TenantModuleDescriptor tm = new TenantModuleDescriptor();
      tm.setAction(TenantModuleDescriptor.Action.disable);
      tm.setId(from);
      tml.add(tm);
      from = null;
    }
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setId(id);
    tm.setFrom(from);
    tm.setAction(action);
    tml.add(tm);
  }

  /**
   * Check that modules are consistent WRT interface dependencies.
   *
   * <p>This method should be called until non-null errors are returned.
   *
   * <p>There could be situations where dependencies could not be resolved
   * even in cases of repeated calls.
   *
   * @param modsAvailable all known modules
   * @param modsEnabled modules enabled for a tenant that is checked
   * @param tml tenant modules list as given by install
   * @param fix whether to modify modules to fix dependencies
   * @param stickyModules modules that are not removed (because they are explicitly listed)
   * @return list of errors and empty list for no errors; null if modules are fixed (call again)
   */
  static List<String> checkEnabledModules(
      Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml, boolean fix,
      Set<String> stickyModules) {

    List<String> errors = new LinkedList<>();
    Map<String, List<ModuleInterface>> providedInterfaces = new HashMap<>();
    Map<String, List<ModuleInterface>> requiredInterfaces = new HashMap<>();
    Map<String, List<ModuleInterface>> requiredOptInterfaces = new HashMap<>();
    for (Map.Entry<String, ModuleDescriptor> entry : modsEnabled.entrySet()) {
      ModuleDescriptor md = entry.getValue();
      for (InterfaceDescriptor descriptor : md.getProvidesList()) {
        if (descriptor.isRegularHandler()) {
          ModuleInterface moduleInterface = new ModuleInterface(md, descriptor);
          providedInterfaces.computeIfAbsent(descriptor.getId(), k -> new ArrayList<>())
              .add(moduleInterface);
        }
      }
      for (InterfaceDescriptor interfaceDescriptor : md.getRequiresList()) {
        ModuleInterface moduleInterface = new ModuleInterface(md, interfaceDescriptor);
        requiredInterfaces.computeIfAbsent(interfaceDescriptor.getId(), k -> new ArrayList<>())
            .add(moduleInterface);
      }
      for (InterfaceDescriptor interfaceDescriptor : md.getRequiresOptionalList()) {
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

  /**
   * Check for enabled modules, that an interface is only provided once
   *
   * <p>Optionally, attempt to fix the situation by removing modules to ensure that
   * (eventually) only one module provides an interface.
   *
   * <p>For each entry in providedInterfaces with more than one ModuleInterface add an error
   * message to errors.
   *
   * <p>If fix is true and it is not listed in stickyModules also disable it by adding an
   * disable entry to tml and by removing it from modsEnabled.
   *
   * <p>If a module gets disabled the method stops and returns true without checking the
   * remaining interfaces.
   *
   * <p>If all interfaces have been checked but no module has been disabled false is returned.
   *
   * @param modsEnabled moddules enabled for a tenant
   * @param tml tenant module list (install)
   * @param fix whether to disable modules when multiple interfaces are provided
   * @param errors errors list (empty if no errors)
   * @param providedInterfaces provided interfaces for enabled modules
   * @param stickyModules modules that are never removed/enabled
   * @return true if modsEnabled was altered (call again), false if modsEnabled was unchanged
   */
  private static boolean checkMultiple(
      Map<String, ModuleDescriptor> modsEnabled,
      List<TenantModuleDescriptor> tml, boolean fix, List<String> errors,
      Map<String, List<ModuleInterface>> providedInterfaces, Set<String> stickyModules) {

    for (Map.Entry<String, List<ModuleInterface>> entry : providedInterfaces.entrySet()) {
      if (entry.getValue().size() <= 1) {
        continue;
      }
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
    return false;
  }

  /**
   * Check for enabled modules, that an interfaces required are also provided.
   * @param modsAvailable all modules known
   * @param modsEnabled modules enabled for a tenant
   * @param tml tenant module list (install)
   * @param fix whether to enable/disable modules when interfaces are not found.
   * @param errors errors list (empty if no errors)
   * @param providedInterfaces provided interfaces for modsEnabled
   * @param requiredInterfaces required interfaces for modsEnabled
   * @param stickyModules modules that are never removed/enabled
   * @return true if modsEnabled was altered (call again), false if modsEnabled was unchanged.
   */
  private static boolean checkRequired(
      Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
      boolean fix, List<String> errors, Map<String, List<ModuleInterface>> providedInterfaces,
      Map<String, List<ModuleInterface>> requiredInterfaces, Set<String> stickyModules) {

    for (Map.Entry<String, List<ModuleInterface>> entry : requiredInterfaces.entrySet()) {
      List<ModuleInterface> providedModuleInterfaces = providedInterfaces.get(entry.getKey());
      if (providedModuleInterfaces != null) {
        continue;
      }
      logger.info("Interface {} undefined and required", entry.getKey());
      for (ModuleInterface req : entry.getValue()) {
        if (fix) {
          Map<String, ModuleDescriptor> modules =
              findModulesForRequiredInterface(modsAvailable, req.interfaceDescriptor);
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
    return false;
  }

  /**
   * Check for enabled modules, that an interfaces provided and required are compatible.
   * @param modsAvailable all modules known
   * @param modsEnabled modules enabled for a tenant
   * @param tml tenant module list (install)
   * @param fix whether to enable/disable modules when interfaces are incompatible.
   * @param errors errors list (empty if no errors)
   * @param providedInterfaces provided interfaces for modsEnabled
   * @param requiredOptInterfaces required/optional interfaces for modsEnabled
   * @param stickyModules modules that are never removed/enabled
   * @return true if modsEnabled was altered (call again), false if modsEnabled was unchanged.
   */
  private static boolean checkCompatible(
      Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
      boolean fix, List<String> errors, Map<String, List<ModuleInterface>> providedInterfaces,
      Map<String, List<ModuleInterface>> requiredOptInterfaces, Set<String> stickyModules) {

    for (Map.Entry<String, List<ModuleInterface>> entry : requiredOptInterfaces.entrySet()) {
      List<ModuleInterface> providedModuleInterfaces = providedInterfaces.get(entry.getKey());
      if (providedModuleInterfaces == null) {
        continue;
      }
      ModuleInterface prov = providedModuleInterfaces.get(0);
      for (ModuleInterface req : entry.getValue()) {
        if (prov.interfaceDescriptor.isCompatible(req.interfaceDescriptor)) {
          continue;
        }
        logger.info("Interface prov={}:{}/{} req={}:{}/{} not compatible",
            prov.interfaceDescriptor.getId(), prov.interfaceDescriptor.getVersion(),
            prov.moduleDescriptor.getId(),
            req.interfaceDescriptor.getId(), req.interfaceDescriptor.getVersion(),
            req.moduleDescriptor.getId());
        if (fix) {
          if (!stickyModules.contains(prov.moduleDescriptor.getId())) {
            // see if we can find a module that provides the required interface
            Map<String, ModuleDescriptor> modules =
                findModulesForRequiredInterface(modsAvailable, req.interfaceDescriptor);
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
            Map<String, ModuleDescriptor> modules =
                findModuleWithProvidedInterface(modsAvailable, prov.interfaceDescriptor);
            for (String product : modules.keySet()) {
              if (product.equals(req.moduleDescriptor.getProduct())) {
                String from = req.moduleDescriptor.getId();
                ModuleDescriptor mdFound = modules.get(product);
                stickyModules.add(mdFound.getId());
                logger.info("Adding 2 to={} from={}", mdFound.getId(), from);
                modsEnabled.remove(from);
                modsEnabled.put(mdFound.getId(), mdFound);
                addTenantModule(tml, mdFound.getId(), from,
                    TenantModuleDescriptor.Action.enable);
                return true;
              }
            }
            modsEnabled.remove(req.moduleDescriptor.getId());
            addTenantModule(tml, req.moduleDescriptor.getId(), null,
                TenantModuleDescriptor.Action.disable);
            return true;
          }
        }
        errors.add(messages.getMessage("10201", req.moduleDescriptor.getId(), entry.getKey(),
            req.interfaceDescriptor.getVersion(),
            prov.interfaceDescriptor.getVersion() + "/" + prov.moduleDescriptor.getId()));
      }
    }
    return false;
  }

  /**
   * Install modules with dependency checking only.
   *
   * @param modsAvailable available modules
   * @param modsEnabled   enabled modules (for some tenant)
   * @param tml           install list with actions
   * @param reinstall     whether to re-install
   * @param maxIterations how many fixup iterations
   */
  static void installMaxIterations(
      Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
      boolean reinstall, int maxIterations) {

    final Collection<ModuleDescriptor> enabledModules = new LinkedList<>(modsEnabled.values());

    List<String> errors = new LinkedList<>();
    Set<String> stickyModules = new HashSet<>();
    for (TenantModuleDescriptor tm : tml) {
      String id = tm.getId();
      ModuleId moduleId = new ModuleId(id);
      if (tm.getAction() == TenantModuleDescriptor.Action.enable) {
        if (!moduleId.hasSemVer()) {
          id = moduleId.getLatest(modsAvailable.keySet());
          tm.setId(id);
        }
        stickyModules.add(id);
        if (!modsAvailable.containsKey(id)) {
          errors.add(messages.getMessage("10801", id));
        } else if (modsEnabled.containsKey(id) && !reinstall) {
          tm.setAction(TenantModuleDescriptor.Action.uptodate);
        } else {
          // see if module is already enabled in which case we must turn it into an upgrade
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
    if (!errors.isEmpty()) {
      throw new OkapiError(ErrorType.USER, String.join(". ", errors));
    }
    if (maxIterations == 0) {
      errors = checkEnabledModules(modsAvailable, modsEnabled, tml, false, stickyModules);
    } else {
      int i = 0;
      do {
        errors = checkEnabledModules(modsAvailable, modsEnabled, tml, true, stickyModules);
        i++;
      } while (errors == null && i < maxIterations);
      logger.info("Dependency resolution done in {} iterations", i);
      if (errors == null) {
        throw new OkapiError(ErrorType.INTERNAL,
            "Dependency resolution not completing in " + maxIterations + " iterations");
      }
    }
    if (!errors.isEmpty()) {
      throw new OkapiError(ErrorType.USER, String.join(". ", errors));
    }
    sortTenantModules(tml, modsAvailable, enabledModules);

  }

  /**
   * Sort the modules honoring enable/disable actions.
   * <p>This is topological sort where nodes represent modules and arcs represent
   * interface dependencies.
   * </p>
   * @see <a href="https://en.wikipedia.org/wiki/Topological_sorting">Topological sorting</a>
   * @param tml the module list with actions and the resulting sorted list afterwards.
   * @param modsAvailable all known modules
   * @param modules the existing list of modules and current list as we go on
   * @throws OkapiError if dependencies can not be satisfied - including circular dependencies
   */
  static void sortTenantModules(List<TenantModuleDescriptor> tml,
      Map<String, ModuleDescriptor> modsAvailable, Collection<ModuleDescriptor> modules) {

    logger.info("sortTenantModules with list {}", () ->
        tml.stream().map(TenantModuleDescriptor::getId).collect(Collectors.joining(", ")));
    List<TenantModuleDescriptor> result = new ArrayList<>();
    Iterator<TenantModuleDescriptor> iterator = tml.iterator();
    while (iterator.hasNext()) {
      TenantModuleDescriptor tm = iterator.next();
      ModuleDescriptor md = modsAvailable.get(tm.getId());
      if (tm.getAction().equals(TenantModuleDescriptor.Action.disable)) {
        logger.debug("See if module {} can be removed from existing list of modules {}",
            md.getId(), modules.stream().map(ModuleDescriptor::getId)
                .collect(Collectors.joining(", ")));
        if (DepResolution.moduleDepRequired(modules, md)) {
          logger.debug("yes: removing {}", md.getId());
          iterator.remove();
          iterator = tml.iterator();
          result.add(tm);
          modules.remove(md);
        }
      } else if (tm.getAction().equals(TenantModuleDescriptor.Action.enable)) {
        logger.debug("See if module {} can be added to existing list of modules {}",
            md.getId(), modules.stream().map(ModuleDescriptor::getId)
                .collect(Collectors.joining(", ")));
        if (DepResolution.moduleDepProvided(modules, md)) {
          logger.debug("yes: adding {}", md.getId());
          iterator.remove();
          iterator = tml.iterator();
          result.add(tm);
          modules.add(md);
          String moduleFrom = tm.getFrom();
          if (moduleFrom != null) {
            logger.debug("yes: removing from {}", moduleFrom);
            modules.remove(modsAvailable.get(moduleFrom));
          }
        }
      } else {
        iterator.remove();
        iterator = tml.iterator();
        result.add(tm);
      }
    }
    if (!tml.isEmpty()) {
      // it would be good to analyze this further with the interfaces that are problematic
      throw new OkapiError(ErrorType.USER, "Some modules cannot be topological sorted: "
          + tml.stream().map(TenantModuleDescriptor::getId).collect(Collectors.joining(", ")));
    }
    tml.addAll(result);
  }

  /**
   * Find modules that satisfies interface dependency.
   * @param modsAvailable all modules known
   * @param testInterface interface to check. Either a required interface or a provided interface
   * @param provide true: testInterface is a provided interface;
   *                false: testInterface is a required interface
   * @return newest modules that meets the requirements; empty if no modules are found
   */
  private static Map<String, ModuleDescriptor> findModulesForInterface(
      Map<String, ModuleDescriptor> modsAvailable, InterfaceDescriptor testInterface,
      boolean provide) {

    Set<String> replaceProducts = new HashSet<>();
    Map<String, ModuleDescriptor> productMd = new HashMap<>();
    for (Map.Entry<String, ModuleDescriptor> entry : modsAvailable.entrySet()) {
      ModuleDescriptor md = entry.getValue();
      String product = md.getProduct();
      List<InterfaceDescriptor> list = provide
          ? md.getRequiresOptionalList() : Arrays.asList(md.getProvidesList());
      for (InterfaceDescriptor pi : list) {
        if (pi.isRegularHandler() && (provide
            ? testInterface.isCompatible(pi) : pi.isCompatible(testInterface))) {
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
   * Find modules that provide a required interface.
   * @param modsAvailable all modules known
   * @param req required interface to check
   * @return newest modules that meet the requirements; empty if no modules are found
   */
  static Map<String, ModuleDescriptor> findModulesForRequiredInterface(
      Map<String, ModuleDescriptor> modsAvailable, InterfaceDescriptor req) {

    return findModulesForInterface(modsAvailable, req, false);
  }

  /**
   * Find modules that require a provided interface.
   * @param modsAvailable all modules known
   * @param prov provided interface to check
   * @return newest modules that meets the requirements; empty if no modules are found.
   */
  static Map<String, ModuleDescriptor> findModuleWithProvidedInterface(
      Map<String, ModuleDescriptor> modsAvailable, InterfaceDescriptor prov) {

    return findModulesForInterface(modsAvailable, prov, true);
  }
}
