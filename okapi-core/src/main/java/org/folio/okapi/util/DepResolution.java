package org.folio.okapi.util;

import io.vertx.core.logging.Logger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;

public class DepResolution {
  private static Logger logger = OkapiLogger.get();
  private static Messages messages = Messages.getInstance();
  
    /**
   * Check one dependency.
   *
   * @param md module to check
   * @param req required dependency
   * @param modlist the list to check against
   * @return null if ok, or error message
   */
  private static String checkOneDependency(ModuleDescriptor md, InterfaceDescriptor req,
    Map<String, ModuleDescriptor> modlist) {
    InterfaceDescriptor seenversion = null;
    for (Map.Entry<String, ModuleDescriptor> entry : modlist.entrySet()) {
      ModuleDescriptor rm = entry.getValue();
      for (InterfaceDescriptor pi : rm.getProvidesList()) {
        logger.debug("Checking dependency of " + md.getId() + ": "
          + req.getId() + " " + req.getVersion()
          + " against " + pi.getId() + " " + pi.getVersion());
        if (req.getId().equals(pi.getId())) {
          if (pi.isCompatible(req)) {
            logger.debug("Dependency OK");
            return null;  // ok
          }
          seenversion = pi;
        }
      }
    }
    if (seenversion == null) {
      return messages.getMessage("10200", md.getId(), req.getId(), req.getVersion());
    } else {
      StringBuilder modUses = new StringBuilder();
      for (Map.Entry<String, ModuleDescriptor> entry : modlist.entrySet()) {
        ModuleDescriptor rm = entry.getValue();
        for (InterfaceDescriptor ri : rm.getRequiresList()) {
          if (seenversion.getId().equals(ri.getId()) && seenversion.isCompatible(ri)) {
            modUses.append("/");
            modUses.append(rm.getId());
          }
        }
      }
      return messages.getMessage("10201", md.getId(), req.getId(),
        req.getVersion(), seenversion.getVersion() + modUses.toString());
    }
  }

  /**
   * Check that the dependencies are satisfied.
   *
   * @param md Module to be checked
   * @return empty list if if no problems, or list of error messages
   *
   * This could be done like we do conflicts, by building a map and checking
   * against that...
   */
  private static List<String> checkDependencies(ModuleDescriptor md,
    Map<String, ModuleDescriptor> modlist) {

    List<String> list = new LinkedList<>(); // error messages (empty=no errors)
    logger.debug("Checking dependencies of " + md.getId());
    for (InterfaceDescriptor req : md.getRequiresList()) {
      String res = checkOneDependency(md, req, modlist);
      if (res != null) {
        list.add(res);
      }
    }
    return list;
  }


  /**
   * Check that all dependencies are satisfied. Usually called with a copy of
   * the modules list, after making some change.
   *
   * @param modlist list to check
   * @return error message, or "" if all is ok
   */
  public static String checkAllDependencies(Map<String, ModuleDescriptor> modlist) {
    List<String> list = new LinkedList<>();
    for (ModuleDescriptor md : modlist.values()) {
      List<String> res = checkDependencies(md, modlist);
      list.addAll(res);
    }
    if (list.isEmpty()) {
      return "";
    } else {
      return String.join(". ", list);
    }
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

}
