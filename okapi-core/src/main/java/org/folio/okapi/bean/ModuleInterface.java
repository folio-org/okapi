package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.folio.okapi.util.ProxyContext;

/**
 * ModuleInterface describes an interface a module can provide, or depend on.
 * Basically just a name, and a version number. Version numbers are in the form
 * X.Y.Z where X is the major version of the interface, Y is the minor version
 * of the interface, and Z is the software version of the module.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleInterface {

  private String id;
  private String version;
  private String interfaceType; // enum "proxy" (default), or "system", "multiple"
  private RoutingEntry[] routingEntries;
  private RoutingEntry[] handlers;
  private final Logger logger = LoggerFactory.getLogger("okapi");

  public ModuleInterface() {
  }

  public ModuleInterface(String id, String version) {
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

  public void setVersion(String version) throws IllegalArgumentException {
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
    if (p == null) {
      return false;
    }
    return true;
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
   * Check if this ModuleInterface is compatible with the required one.
   *
   * @param required
   * @return
   */
  public boolean isCompatible(ModuleInterface required) {
    if (!this.getId().equals(required.getId())) {
      return false; // not the same interface at all
    }
    int[] t = this.versionParts(this.version, 0);
    for (int idx = 0;; idx++) {
      int[] r = this.versionParts(required.version, idx);
      if (r == null) {
        break;
      }
      // major version has to match exactly
      if (t[0] != r[0]) {
        continue;
      }
      // minor version has to be at least the same
      if (t[1] < r[1]) {
        continue;
      }
      // if minor equals, and we have sw req, check sw
      if (t[1] == r[1] && r[2] >= 0 && t[2] < r[2]) {
        continue;
      }
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
    if (interfaceType != null && !"proxy".equals(interfaceType)) {
      return false; // explicitly some other type, like "multiple" or "system"
    }
    if (this.id.startsWith("_")) {
      return false; // old-fashioned _tenant etc. DEPRECATED
    }
    return true;
  }

  public RoutingEntry[] getRoutingEntries() {
    return routingEntries;
  }

  @JsonIgnore
  public List<RoutingEntry> getAllRoutingEntries() {
    List<RoutingEntry> all = new ArrayList<>();
    if (routingEntries != null) {
      Collections.addAll(all, routingEntries);
    }
    if (handlers != null) {
      Collections.addAll(all, handlers);
    }
    return all;
  }

  public void setRoutingEntries(RoutingEntry[] routingEntries) {
    this.routingEntries = routingEntries;
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
      return "id is missing";
    }
    String prefix = "Module '" + mod + "' interface '" + id + "': ";
    if (version == null) {
      return "version is missing";
    }

    String err;
    err = validateGeneral(pc, mod);
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
      err = validateProvides(pc, section, mod);
      if (!err.isEmpty()) {
        return err;
      }
    }
    if (section.equals("requires")) {
      err = validateRequires(pc, section, mod);
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
  private String validateGeneral(ProxyContext pc, String mod) {
    String it = getInterfaceType();
    if (it != null && !it.equals("proxy") && !it.equals("system") && !it.equals("multiple")) {
      return "Bad interface type '" + it + "'";
    }
    if (!validateVersion(version)) {
      return "Bad interface version number '" + version + "'";
    }
    return "";
  }

  /**
   * Validate those things that apply to the "provides" section.
   */
  private String validateProvides(ProxyContext pc, String section, String mod) {
    if (getRoutingEntries() != null) {
      pc.warn("Module '" + mod + "':"
        + "Provided interface " + getId()
        + " uses DEPRECATED RoutingEntries. "
        + "Use handlers instead");
    }
    RoutingEntry[] handlers = getHandlers();
    if (handlers != null) {
      for (RoutingEntry re : handlers) {
        String err = re.validate(pc, "handlers", mod);
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
  private String validateRequires(ProxyContext pc, String section, String mod) {
    RoutingEntry[] oldRoutingEntries = getRoutingEntries();
    if (oldRoutingEntries != null) {
      return "No RoutingEntries allowed in 'requires' section";
    }
    RoutingEntry[] handlers1 = getHandlers();
    if (handlers != null && handlers.length > 0) {
      return "No handlers allowed in 'requires' section";
    }
    return "";
  }

}
