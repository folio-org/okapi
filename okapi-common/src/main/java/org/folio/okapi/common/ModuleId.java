package org.folio.okapi.common;
import java.util.Collection;

public class ModuleId implements Comparable<ModuleId> {

  private String product;
  private SemVer semVer;
  private final String id;

  /**
   * Construct Module ID from Module ID string or product only (moduleID without
   * version).
   * May throw IllegalArgumentException for invalid syntax for semantic version.
   * @param s Module ID or product name. The module ID does not have restrictions
   * on characters, but a digit following a hypyen-minus marks the beginning of
   * a semantic version .. See {@link org.folio.okapi.common.SemVer}.
   */
  public ModuleId(String s) {
    id = s;
    for (int i = 0; i < s.length() - 1; i++) {
      if (s.charAt(i) == '-' && Character.isDigit(s.charAt(i + 1))) {
        product = s.substring(0, i);
        semVer = new SemVer(s.substring(i + 1));
        return;
      }
    }
    product = s;
    semVer = null;
  }

  /**
   * Returns Module ID as string
   * @return string representation
   */
  public String getId() {
    return id;
  }

  /**
   * Returns SemVer class that is part of this ModuleID
   * @return SemVer instance or null if no semantic version is present
   */
  public SemVer getSemVer() {
    return semVer;
  }

  /**
   * Returns whether there's a version associated with this instance
   * @return true if there's a version; false otherwise
   */
  public boolean hasSemVer() {
    return semVer != null;
  }

  /**
   * Whether semantic version is an NPM snapshot
   * @return true if there's an NPM snapshot version; false otherwise
   */
  public boolean hasNpmSnapshot() {
    return semVer != null && semVer.hasNpmSnapshot();
  }

  /**
   * Whether there's pre release with version
   * @return true if there's a version with pre-release; false otherwise
   */
  public boolean hasPreRelease() {
    return semVer != null && semVer.hasPreRelease();
  }

  /**
   * Returns product part of module ID (Eg mod-foo-1.2.3 returns mod-foo).
   * @return product string
   */
  public String getProduct() {
    return product;
  }

  /**
   * Compare one module with another
   * @param other module (this compare to other)
   * @return +-5 for product diff, +-4 for major diff, +-3 for minor,
   * +-2 for rest, +-1 for patch, 0=equal
   */
  @Override
  public int compareTo(ModuleId other) {
    int cmp = product.compareTo(other.product);
    if (cmp > 0) {
      return 5; // 4, 3, 2, 1 for major, minor, patch, rest
    } else if (cmp < 0) {
      return -5; // 4, 3, 2, 1 for major, minor, patch, rest
    } else if (semVer != null && other.semVer != null) {
      return semVer.compareTo(other.semVer);
    } else if (semVer != null && other.semVer == null) {
      return 4;
    } else if (semVer == null && other.semVer != null) {
      return -4;
    } else {
      return 0;
    }
  }

  /**
   * Whether two products match
   * @param that other module
   * @return true if equal; false otherwise
   */
  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof ModuleId)) {
      return false;
    }
    return compareTo((ModuleId) that) == 0;
  }

  /**
   * @return hash-code
   */
  @Override
  public int hashCode() {
    int c = product.hashCode();
    if (semVer != null) {
      c = c * 37 + semVer.hashCode();
    }
    return c;
  }

  /**
   * String representation of module ID
   * @return string representation
   */
  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(product);
    if (semVer != null) {
      b.append("-");
      b.append(semVer.toString());
    }
    return b.toString();
  }

  /**
   * static comparsion
   * @param i1 left
   * @param i2 right
   * @return See
   * {@link org.folio.okapi.common.ModuleId#compareTo(org.folio.okapi.common.ModuleId)
   */
  public static int compare(String i1, String i2) {
    ModuleId m1 = new ModuleId(i1);
    ModuleId m2 = new ModuleId(i2);
    return m1.compareTo(m2);
  }

  /**
   * Returns newest module out of a list of modules
   * @param l list of module IDs
   * @return newest module (null for empty list)
   */
  public String getLatest(Collection<String> l) {
    ModuleId bModule = null;
    String bId = null;
    for (String cId : l) {
      ModuleId cModule = new ModuleId(cId);
      if (product.equals(cModule.getProduct())
        && (bModule == null || cModule.compareTo(bModule) > 0)) {
        bId = cId;
        bModule = cModule;
      }
    }
    return bId != null ? bId : id;
  }

  /**
   * Whether this module has some prefix
   * @param other module ID which serves as prefix
   * @return true if this module has prefix of other; false otherwise
   */
  public boolean hasPrefix(ModuleId other) {
    if (!this.product.equals(other.product) || this.semVer == null) {
      return false;
    } else if (other.semVer != null) {
      return semVer.hasPrefix(other.semVer);
    } else {
      return true;
    }
  }
}
