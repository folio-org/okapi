package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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
  private String interfaceType; // enum "proxy" (default), or "system"
  private RoutingEntry[] routingEntries;

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
    return version.matches("\\d+\\.\\d+(\\.\\d+)?");
  }

  /**
   * Helper to get a part of the version. part 0 is XX, the major version of the
   * interface, part 1 is YY, the minor version of the interface, and part 2 is
   * ZZ, the software version. Returns -1 for unspecified parts.
   */
  private int versionPart(String version, int part) {
    int parts[] = versionParts(version);
    if (part < 0) // just to be sure
    {
      return -1;
    }
    if (part >= parts.length) {
      return -1;
    }
    return parts[part];
  }

  /**
   * Return the version parts.
   *
   * @param version
   * @return an array of 3 elements, XX, YY, ZZ, with -1 for missing parts
   */
  private int[] versionParts(String version) {
    String[] parts = version.split("\\.");
    int[] p = new int[3];
    for (int i = 0; i < 3; i++) {
      int pi;
      if (i < parts.length) {
        p[i] = Integer.parseInt(parts[i]);
      } else {
        p[i] = -1;
      }
    }
    return p;
  }

  /**
   *
   * @return XX, the major interface version
   */
  public int majorInterfaceVersion() {
    return versionPart(version, 0);
  }

  /**
   *
   * @return YY, the minor interface version
   */
  public int minorInterfaceVersion() {
    return versionPart(version, 1);
  }

  /**
   *
   * @return ZZ, the software version
   */
  public int softwareVersion() {
    return versionPart(version, 2);
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
    int[] t = this.versionParts(this.version);
    int[] r = this.versionParts(required.version);
    // major version has to match exactly
    if (t[0] != r[0]) {
      return false;
    }
    // minor version has to be at least the same
    if (t[1] < r[1]) {
      return false;
    }
    // if minor equals, and we have sw req, check sw
    if (t[1] == r[1] && r[2] >= 0 && t[2] < r[2]) {
      return false;
    }
    return true;
  }

  /**
   * Compare to another ModuleInterface.
   *
   * @param other
   * @return 0 if equal, -1 if this.id < other.id
   *   +1 id this.id > other.id +/- 2 if major versions differ +/- 3 if minor
   * versions differ +/- 4 if sw versions differ
   */
  public int compare(ModuleInterface other) {
    int cmp = this.id.compareTo(other.id);
    if (cmp > 0) {
      return 1;
    } else if (cmp < 0) {
      return -1;
    }
    int[] t = this.versionParts(this.version);
    int[] r = this.versionParts(other.version);
    if (t[0] != r[0]) {
      return Integer.signum(t[0] - r[0]) * 2;
    }
    if (t[1] != r[1]) {
      return Integer.signum(t[1] - r[1]) * 3;
    }
    if (t[2] != r[2]) {
      return Integer.signum(t[2] - r[2]) * 4;
    }
    return 0;
  }

  public String getInterfaceType() {
    return interfaceType;
  }

  public void setInterfaceType(String interfaceType) {
    this.interfaceType = interfaceType;
  }

  public RoutingEntry[] getRoutingEntries() {
    return routingEntries;
  }

  public void setRoutingEntries(RoutingEntry[] routingEntries) {
    this.routingEntries = routingEntries;
  }

  /**
   * Validate a moduleInterface.
   *
   * @param strict if false, will only log a warning on most errors
   * @param section "provides" or "requires" - the rules differ
   * @return "" if ok, or a simple error message
   */
  public String validate(boolean strict, String section) {
    logger.debug("Validating ModuleInterface " + Json.encode(this));
    String err;
    err = validateGeneral();
    if (!err.isEmpty()) {
      logger.debug("Validating ModuleInterface failed in req: " + err);
      return err;
    }
    if (section.equals("provides")) {
      err = validateProvides(strict, section);
      if (!err.isEmpty()) {
        logger.debug("Validating ModuleInterface failed in prov: " + err);
        return err;
      }
    }
    if (section.equals("requires")) {
      err = validateRequires(strict, section);
      if (!err.isEmpty()) {
        logger.debug("Validating ModuleInterface failed in prov: " + err);
        if (strict) {
          return err;
        }
        logger.warn(err);
      }
    }
    return "";
  }

  /**
   * Validate those things that just have to be right.
   * @return
   */
  private String validateGeneral() {
    String it = getInterfaceType();
    if (it != null && !it.equals("proxy") && !it.equals("system")) {
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
  private String validateProvides(boolean strict, String section) {
    if (routingEntries != null) {
      for (RoutingEntry re : routingEntries) {
        String err = re.validate(strict);
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
  private String validateRequires(boolean strict, String section) {
    if (routingEntries != null && routingEntries.length > 0) {
      return "No RoutingEntries allowed in 'provides' section";
    }
    return "";
  }

}
