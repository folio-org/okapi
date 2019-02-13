package org.folio.okapi.util;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.ModuleId;

public class ModuleUtil {
  private ModuleUtil() {
    throw new IllegalAccessError(this.toString());
  }

  private static Messages messages = Messages.getInstance();

  public static TenantInstallOptions createTenantOptions(HttpServerRequest req) {
    TenantInstallOptions options = new TenantInstallOptions();

    options.setSimulate(getParamBoolean(req, "simulate", false));
    options.setPreRelease(getParamBoolean(req, "preRelease", true));
    options.setNpmSnapshot(getParamBoolean(req, "npmSnapshot", true));
    options.setDeploy(getParamBoolean(req, "deploy", false));
    options.setPurge(getParamBoolean(req, "purge", false));
    options.setTenantParameters(req.getParam("tenantParameters"));
    return options;
  }

  public static boolean getParamBoolean(HttpServerRequest req, String name, boolean defValue) {
    String v = req.getParam(name);
    if (v == null) {
      return defValue;
    } else if ("true".equals(v)) {
      return true;
    } else if ("false".equals(v)) {
      return false;
    }
    throw new DecodeException("Bad boolean for parameter " + name + ": " + v);
  }

  private static boolean interfaceCheck(InterfaceDescriptor[] interfaces,
    String interfaceStr, String scope) {
    if (interfaceStr == null) {
      return true;
    } else {
      if (interfaces != null) {
        for (InterfaceDescriptor pi : interfaces) {
          String[] kv = interfaceStr.split("=");
          List<String> gotScope = pi.getScopeArray();
          if (pi.getId().equals(kv[0])
            && (kv.length != 2 || pi.getVersion().equals(kv[1]))
            && (scope == null || gotScope.contains(scope))) {
            return true;
          }
        }
      }
      return false;
    }
  }

  public static List<ModuleDescriptor> filter(HttpServerRequest req, List<ModuleDescriptor> list,
    boolean full, boolean includeName) {
    ModuleId filter = null;
    String filterStr = req.getParam("filter");
    if (filterStr != null) {
      filter = new ModuleId(filterStr);
    }
    final String latestStr = req.getParam("latest");
    final String provideStr = req.getParam("provide");
    final String requireStr = req.getParam("require");
    final String orderByStr = req.getParam("orderBy");
    final String orderStr = req.getParam("order");
    final boolean preRelease = getParamBoolean(req, "preRelease", true);
    final boolean npmSnapshot = getParamBoolean(req, "npmSnapshot", true);
    final String scope = req.getParam("scope");
    if (!full) {
        full = getParamBoolean(req, "full", false);
    }
    Iterator<ModuleDescriptor> iterator = list.iterator();
    while (iterator.hasNext()) {
      ModuleDescriptor md = iterator.next();
      String id = md.getId();
      ModuleId idThis = new ModuleId(id);
      if ((filter != null && !idThis.hasPrefix(filter))
        || (!npmSnapshot && idThis.hasNpmSnapshot())
        || (!preRelease && idThis.hasPreRelease())
        || !interfaceCheck(md.getRequires(), requireStr, scope)
        || !interfaceCheck(md.getProvides(), provideStr, scope)) {
        iterator.remove();
      }
    }
    if (latestStr != null) {
      try {
        int limit = Integer.parseInt(latestStr);
        list = DepResolution.getLatestProducts(limit, list);
      } catch (NumberFormatException ex) {
        throw new DecodeException(messages.getMessage("11608", "latest", ex.getMessage()));
      }
    }
    if (orderByStr != null) {
      if (!"id".equals(orderByStr)) {
        throw new DecodeException(messages.getMessage("11604", orderByStr));
      }
      if (orderStr == null || "desc".equals(orderStr)) {
        Collections.sort(list, Collections.reverseOrder());
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
      ml.add(new ModuleDescriptor(md, false, includeName));
    }
    return ml;
  }

}
