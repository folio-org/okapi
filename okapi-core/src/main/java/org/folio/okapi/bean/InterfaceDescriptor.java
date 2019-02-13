package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.util.ProxyContext;

/**
 * InterfaceDescriptor describes an interface a module can provide, or depend
 * on. * Basically just a name, and a version number. Version numbers are in the form
 * X.Y.Z where X is the major version of the interface, Y is the minor version
 * of the interface, and Z is the software version of the module. Also
 * the InterfaceType, and the routing entries for the interface.
 */
// S1168: Empty arrays and collections should be returned instead of null
// S1192: String literals should not be duplicated
@java.lang.SuppressWarnings({"squid:S1168", "squid:S1192"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterfaceDescriptor {

  private String id;
  private String version;
  private String interfaceType; // enum: "proxy" (default), "system", "internal", multiple
  private RoutingEntry[] handlers;
  private String[] scope;
  private final Logger logger = OkapiLogger.get();

  public InterfaceDescriptor() {
    this.id = null;
    this.version = null;
    this.interfaceType = null;
    this.handlers = null;
    this.scope = null;
  }

  public InterfaceDescriptor(String id, String version) {
    this.id = id;
    if (validateVersion(version)) {
      this.version = version;
    } else {
      throw new IllegalArgumentException("Bad version number '" + version + "'");
    }
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    if (validateVersion(version)) {
      this.version = version;
    } else {
      throw new IllegalArgumentException("Bad version number '" + version + "'");
    }
  }

  /**
   * Validate that the version conforms to the format XX.YY.ZZ or XX.YY
   *
   * @return true if a good version number
   */
  public static boolean validateVersion(String version) {
    int[] p = versionParts(version, 0);
    return p != null;
  }

  /**
   * Return the version parts.
   *
   * @param version
   * @return an array of 3 elements, XX, YY, ZZ, with -1 for missing parts
   */
  private static int[] versionParts(String version, int idx) {
    final String[] verComp = version.split(" ");
    if (verComp.length <= idx) {
      return null;
    }
    final String[] parts = verComp[idx].split("\\.");
    if (parts.length < 2 || parts.length > 3) {
      return null;
    }
    int[] p = new int[3];
    for (int i = 0; i < 3; i++) {
      if (i < parts.length) {
        try {
          p[i] = Integer.parseInt(parts[i]);
        } catch (NumberFormatException ex) {
          return null;
        }
      } else {
        p[i] = -1;
      }
    }
    return p;
  }

  /**
   * Check if this InterfaceDescriptor is compatible with the required one.
   *
   * @param required
   * @return
   */
  public boolean isCompatible(InterfaceDescriptor required) {
    if (!this.getId().equals(required.getId())) {
      return false; // not the same interface at all
    }
    int[] t = InterfaceDescriptor.versionParts(this.version, 0);
    if (t == null) {
      return false;
    }
    for (int idx = 0;; idx++) {
      int[] r = InterfaceDescriptor.versionParts(required.version, idx);
      if (r == null) {
        break;
      }
      if (t[0] == r[0] && (t[1] > r[1] || (t[1] == r[1] && r[2] <= t[2])))
        return true;
    }
    return false;
  }

  public String getInterfaceType() {
    return interfaceType;
  }

  public void setInterfaceType(String interfaceType) {
    this.interfaceType = interfaceType;
  }

  /**
   * Checks if the interface is a regular handler. Not a system interface, not
   * multiple, and not old-fashioned _tenant. Used to skip conflict checks.
   *
   * @return
   */
  @JsonIgnore
  public boolean isRegularHandler() {
    return isType("proxy");
  }

  @JsonIgnore
  public boolean isType(String type) {
    String haveType;
    if (id.startsWith("_")) {
      haveType = "system";
    } else if (interfaceType == null) {
      haveType = "proxy";
    } else {
      haveType = interfaceType;
    }
    return type.equals(haveType);
  }

  @JsonIgnore
  public List<RoutingEntry> getAllRoutingEntries() {
    List<RoutingEntry> all = new ArrayList<>();
    if (handlers != null) {
      Collections.addAll(all, handlers);
    }
    return all;
  }

  public RoutingEntry[] getHandlers() {
    return handlers;
  }

  public void setHandlers(RoutingEntry[] handlers) {
    this.handlers = handlers;
  }

  /**
   * Validate a moduleInterface.
   *
   * Writes Warnings in the log in case of deprecated features.
   *
   * @param section "provides" or "requires" - the rules differ
   * @return "" if ok, or a simple error message
   */
  public String validate(ProxyContext pc, String section, String mod) {
    logger.debug("Validating ModuleInterface " + Json.encode(this));
    if (id == null) {
      return "id is missing for module " + mod;
    }
    String prefix = "Module '" + mod + "' interface '" + id + "': ";
    if (version == null) {
      return "version is missing for module " + mod;
    }

    String err;
    err = validateGeneral(mod);
    if (!err.isEmpty()) {
      return err;
    }
    if (version.matches("\\d+\\.\\d+\\.\\d+")) {
      pc.warn(prefix + "has a 3-part version number '" + version + "'."
        + "Interfaces should be 2-part");
    }

    if ("_tenant".equals(this.id) && !"1.0".equals(version)) {
      pc.warn(prefix + " is '" + version + "'."
        + " should be '1.0'");
    }
    if ("_tenantPermissions".equals(this.id) && !"1.0".equals(version)) {
      pc.warn(prefix + " is '" + version + "'. should be '1.0'");
    }

    if (section.equals("provides")) {
      err = validateProvides(pc, mod);
      if (!err.isEmpty()) {
        return err;
      }
    }
    if (section.equals("requires")) {
      err = validateRequires(mod);
      if (!err.isEmpty()) {
        return err;
      }
    }
    return "";
  }

  /**
   * Validate those things that just have to be right.
   * @return "" if ok, or an error message
   */
  private String validateGeneral(String mod) {
    String it = getInterfaceType();
    if (it != null && !it.equals("proxy") && !it.equals("system") && !it.equals("multiple")) {
      return "Bad interface type '" + it + "' for module " + mod;
    }
    if (!validateVersion(version)) {
      return "Bad interface version number '" + version + "' for module " + mod;
    }
    return "";
  }

  /**
   * Validate those things that apply to the "provides" section.
   */
  private String validateProvides(ProxyContext pc, String mod) {
    if (handlers != null) {
      for (RoutingEntry re : handlers) {
        String err = re.validateHandlers(pc, mod);
        if (!err.isEmpty()) {
          return err;
        }
      }
    }
    return "";
  }

  /**
   * Validate those things that apply to the "requires" section.
   */
  private String validateRequires(String mod) {
    if (handlers != null && handlers.length > 0) {
      return "No handlers allowed in 'requires' section for module " + mod;
    }
    return "";
  }

  @JsonIgnore
  public List<String> getScopeArray() {
    if (scope == null) {
      return new ArrayList<>();
    } else {
      return Arrays.asList(scope);
    }
  }

  public String[] getScope() {
    return scope;
  }

  public void setScope(String[] scope) {
    this.scope = scope;
  }

}
