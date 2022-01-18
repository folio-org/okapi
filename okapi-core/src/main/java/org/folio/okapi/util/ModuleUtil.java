package org.folio.okapi.util;

import io.vertx.core.MultiMap;
import io.vertx.core.json.DecodeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.ModuleId;

public final class ModuleUtil {
  private ModuleUtil() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  private static final Messages messages = Messages.getInstance();

  /**
   * Create tenant install options from HTTP request parameters.
   * @param params HTTP server request parameters
   * @return tenant install options
   */
  public static TenantInstallOptions createTenantOptions(MultiMap params) {
    TenantInstallOptions options = new TenantInstallOptions();

    options.setSimulate(getParamBoolean(params, "simulate", false));
    options.setModuleVersionFilter(new ModuleVersionFilter(params));
    options.setDeploy(getParamBoolean(params, "deploy", false));
    options.setPurge(getParamBoolean(params, "purge", false));
    options.setTenantParameters(params.get("tenantParameters"));
    options.setInvoke(params.get("invoke"));
    options.setAsync(getParamBoolean(params, "async", false));
    options.setIgnoreErrors(getParamBoolean(params, "ignoreErrors", false));
    options.setReinstall(getParamBoolean(params, "reinstall", false));
    return options;
  }

  /**
   * Lookup boolean query parameter in HTTP request.
   * @param params HTTP server request parameters
   * @param name name of query parameter
   * @param defValue default value if omitted
   * @return boolean value
   */
  public static boolean getParamBoolean(MultiMap params, String name, boolean defValue) {
    String v = params.get(name);
    if (v == null) {
      return defValue;
    } else if ("true".equals(v)) {
      return true;
    } else if ("false".equals(v)) {
      return false;
    }
    throw new DecodeException("Bad boolean for parameter " + name + ": " + v);
  }

  /**
   * Lookup integer query parameter in HTTP request.
   * @param params HTTP server request parameters
   * @param name name of query parameter
   * @param defValue default value if omitted (or null if the parameter is mandatory)
   * @return integer value
   */
  public static int getParamInteger(MultiMap params, String name, Integer defValue) {
    String v = params.get(name);
    if (v == null) {
      if (defValue == null) {
        throw new DecodeException("Missing value for parameter '" + name + "'");
      }
      return defValue;
    }
    try {
      return Integer.parseInt(v);
    } catch (NumberFormatException e) {
      throw new DecodeException("Bad integer for parameter '" + name + "'");
    }
  }

  private static boolean interfaceCheck(
      InterfaceDescriptor[] interfaces, String interfaceStr, String scope) {
    if (interfaceStr == null) {
      return true;
    }
    if (interfaces == null) {
      return false;
    }
    String[] interfaceList = interfaceStr.split(",");
    String[][] interfacePair = new String[interfaceList.length][];
    for (int i = 0; i < interfaceList.length; i++) {
      interfacePair[i] = interfaceList[i].split("=");
    }
    for (InterfaceDescriptor pi : interfaces) {
      List<String> gotScope = pi.getScopeArray();
      if (scope == null || gotScope.contains(scope)) {
        for (String [] kv : interfacePair) {
          if (kv.length == 2) {
            InterfaceDescriptor req = new InterfaceDescriptor(kv[0], kv[1]);
            if (pi.isCompatible(req)) {
              return true;
            }
          } else if (pi.getId().equals(kv[0])) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Produce list of modules based on various filters.
   * @param params HTTP server request parameters
   * @param list list of modules to consider
   * @param full true: force full view of each module; false: consider "full" query parameter
   * @param includeName whether to include module name property always
   * @return list of modules
   */
  public static List<ModuleDescriptor> filter(
      MultiMap params, List<ModuleDescriptor> list, boolean full, boolean includeName) {
    ModuleId filter = null;
    String filterStr = params.get("filter");
    if (filterStr != null) {
      filter = new ModuleId(filterStr);
    }
    final String latestStr = params.get("latest");
    final String provideStr = params.get("provide");
    final String requireStr = params.get("require");
    final String orderByStr = params.get("orderBy");
    final String orderStr = params.get("order");
    final ModuleVersionFilter moduleVersionFilter = new ModuleVersionFilter(params);
    final String scope = params.get("scope");
    if (!full) {
      full = getParamBoolean(params, "full", false);
    }
    Iterator<ModuleDescriptor> iterator = list.iterator();
    while (iterator.hasNext()) {
      ModuleDescriptor md = iterator.next();
      String id = md.getId();
      ModuleId idThis = new ModuleId(id);
      if ((filter != null && !idThis.hasPrefix(filter))
          || !moduleVersionFilter.matchesModule(idThis)
          || !(interfaceCheck(md.getRequires(), requireStr, scope)
          || interfaceCheck(md.getOptional(), requireStr, scope))
          || !interfaceCheck(md.getProvides(), provideStr, scope)) {
        iterator.remove();
      }
    }
    if (latestStr != null) {
      try {
        int limit = Integer.parseInt(latestStr);
        DepResolution.getLatestProducts(limit, list);
      } catch (NumberFormatException ex) {
        throw new DecodeException(messages.getMessage("11608", "latest", ex.getMessage()));
      }
    }
    if (orderByStr != null) {
      if (!"id".equals(orderByStr)) {
        throw new DecodeException(messages.getMessage("11604", orderByStr));
      }
      if (orderStr == null || "desc".equals(orderStr)) {
        list.sort(Collections.reverseOrder());
      } else if ("asc".equals(orderStr)) {
        Collections.sort(list);
      } else {
        throw new DecodeException(messages.getMessage("11605", orderStr));
      }
    } else {
      Collections.sort(list);
    }
    if (full) {
      return list;
    }
    List<ModuleDescriptor> ml = new ArrayList<>(list.size());
    for (ModuleDescriptor md : list) {
      ml.add(new ModuleDescriptor(md, includeName));
    }
    return ml;
  }

  /**
   * Return comma separated list of Module Descriptors.
   * @param moduleDescriptors list of modules
   * @return comma separated list
   */
  public static String moduleList(List<ModuleDescriptor> moduleDescriptors) {
    StringBuilder str = new StringBuilder();
    for (ModuleDescriptor md : moduleDescriptors) {
      if (str.length() > 0) {
        str.append(", ");
      }
      str.append(md.getId());
    }
    return str.toString();
  }

  /**
   * Return list of obsolete modules (for clean up).
   * <p>A module is considered a snapshot if it's part of earlier releases than saveReleases
   * *and* it is earlier than the latest saveSnapshots in that release</p>
   * @param mdl list of modules to consider - will be modified.
   * @param saveReleases number of latest non-snapshot releases that are preserved,
   * @param saveSnapshots number of latest snapshots releases that are preserved.
   * @return list of obsolete modules.
   */
  public static List<ModuleDescriptor> getObsolete(List<ModuleDescriptor> mdl,
                                                   int saveReleases, int saveSnapshots) {
    mdl.sort(Collections.reverseOrder());
    Iterator<ModuleDescriptor> it = mdl.listIterator();
    List<ModuleDescriptor> obsoleteList = new ArrayList<>();
    ModuleId idPrev = null;
    int numberReleases = 0;
    int numberSnapshots = 0;
    while (it.hasNext()) {
      ModuleDescriptor md = it.next();
      ModuleId id = new ModuleId(md.getId());
      int diff = idPrev == null ? -5 : id.compareTo(idPrev);
      if (diff == -5) {
        numberReleases = 0;
        numberSnapshots = 0;
      }
      if ((id.hasPreRelease() || id.hasNpmSnapshot())
          && numberReleases >= saveReleases && numberSnapshots >= saveSnapshots) {
        obsoleteList.add(md);
      }
      if (diff >= -1 || id.hasNpmSnapshot()) {
        numberSnapshots++;
      } else {
        numberSnapshots = 0;
        numberReleases++;
      }
      idPrev = id;
    }
    return obsoleteList;
  }
}
