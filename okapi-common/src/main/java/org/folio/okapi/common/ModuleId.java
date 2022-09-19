package org.folio.okapi.common;

import java.util.Collection;
import java.util.List;

public class ModuleId implements Comparable<ModuleId> {

  private final String product;
  private final SemVer semVer;
  private final String id;

  static boolean isAsciiLetter(char c) {
    return c >= 97 && c <= 122; // a-z
  }

  static boolean isAsciiNumeric(char c) {
    return c >= 48 && c <= 57;  // 0-9
  }

  /**
   * Construct Module identifier from string.
   *
   * <p>The identifier string consists of a product name and optionally followed
   * by semantic version. Example of valid identifiers:
   * <ul>
   *   <li>"foo-1.0.0" : product foo with version 1.0.0.</li>
   *   <li>"foo-bar-2.0.0-SNAPSHOT" : product foo-bar with version 2.0.0-SNAPSHOT.</li>
   *   <li>"zoe" : product Zoe without version.</li>
   *   <li>"folio_email-shipping-1.0.0" : FOLIO UI module.</li>
   * </ul>
   * Example of invalid identifiers:
   * <ul>
   *   <li>"mod_inventory"</li>
   *   <li>"FOO"</li>
   *   <li>"foo 1.0.0"</li>
   *   <li>"123"</li>
   *   <li>"a/b"</li>
   * </ul>
   * @param s Module ID or product name. The module ID may include of lowercase letters, digits,
   *          and hyphen-minus. Leading character, however, must be lower-case letter.
   *          A digit following a hyphen-minus marks the beginning of a semantic version.
   *          See {@link org.folio.okapi.common.SemVer}.
   * @throws IllegalArgumentException if the module identifier is invalid.
   */
  public ModuleId(String s) {
    id = s;
    if (s.isEmpty()) {
      throw new IllegalArgumentException("ModuleID must not be empty");
    }
    if (!isAsciiLetter(s.charAt(0))) {
      throw new IllegalArgumentException("ModuleID '" + id + "' must start with lowercase letter");
    }
    final boolean ui = s.startsWith("folio_");
    int i = ui ? 6 : 1;
    for (; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '-') {
        if (i >= s.length() - 1) {
          throw new IllegalArgumentException("ModuleID '" + id
              + "' has non-allowed character at offset " + (i + 1));
        }
        c = s.charAt(++i);
        if (isAsciiNumeric(c)) {
          product = s.substring(0, i - 1);
          productCheck(ui);
          semVer = new SemVer(s.substring(i));
          return;
        }
        if (!isAsciiLetter(c)) {
          throw new IllegalArgumentException("ModuleID '" + id
              + "' has non-allowed character at offset " + i);
        }
      } else if (!isAsciiLetter(c) && !isAsciiNumeric(c)) {
        throw new IllegalArgumentException("ModuleID '" + id
            + "' has non-allowed character at offset " + i);
      }
    }
    product = s;
    productCheck(ui);
    semVer = null;
  }

  void productCheck(boolean ui) {
    if (ui) {
      return;
    }
    if (product.length() > 31) {
      throw new IllegalArgumentException("ModuleID '" + product
          + "' exceeding 31 characters");
    }
    for (String res : List.of("catalog", "date", "role", "time", "timestamp", "user")) {
      if (res.equals(product)) {
        throw new IllegalArgumentException("ModuleID '" + product + "' is a reserved name");
      }
    }
  }

  /**
   * Returns Module ID as string.
   * @return string representation
   */
  public String getId() {
    return id;
  }

  /**
   * Returns SemVer class that is part of this ModuleID.
   * @return SemVer instance or null if no semantic version is present
   */
  public SemVer getSemVer() {
    return semVer;
  }

  /**
   * Returns whether there's a version associated with this instance.
   * @return true if there's a version; false otherwise
   */
  public boolean hasSemVer() {
    return semVer != null;
  }

  /**
   * Whether semantic version is an NPM snapshot.
   * @return true if there's an NPM snapshot version; false otherwise
   */
  public boolean hasNpmSnapshot() {
    return semVer != null && semVer.hasNpmSnapshot();
  }

  /**
   * Whether there's pre-release with version.
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
   * Compare one module with another.
   * @param other module (this compare to other module)
   * @return +-5 for product diff, +-4 for major diff, +-3 for minor,
   *     +-2 for rest, +-1 for patch, 0=equal
   */
  @Override
  public int compareTo(ModuleId other) {
    int cmp = product.compareTo(other.product);
    if (cmp > 0) {
      return 5; // 4, 3, 2, 1 for major, minor, patch, rest
    } else if (cmp < 0) {
      return -5; // 4, 3, 2, 1 for major, minor, patch, rest
    }
    if (semVer != null) {
      if (other.semVer != null) {
        return semVer.compareTo(other.semVer);
      } else {
        return 4;
      }
    } else if (other.semVer != null) {
      return -4;
    } else {
      return 0;
    }
  }

  /**
   * Whether two products match.
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

  @Override
  public int hashCode() {
    int c = product.hashCode();
    if (semVer != null) {
      c = c * 37 + semVer.hashCode();
    }
    return c;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(product);
    if (semVer != null) {
      b.append("-");
      b.append(semVer);
    }
    return b.toString();
  }

  /**
   * Module id comparison.
   * @param i1 left
   * @param i2 right
   * @return See
   * {@link org.folio.okapi.common.ModuleId#compareTo(org.folio.okapi.common.ModuleId)}
   */
  public static int compare(String i1, String i2) {
    ModuleId m1 = new ModuleId(i1);
    ModuleId m2 = new ModuleId(i2);
    return m1.compareTo(m2);
  }

  /**
   * Returns the newest module out of a list of modules including this.
   * @param l list of module IDs
   * @return newest module (possibly this module)
   */
  public String getLatest(Collection<String> l) {
    ModuleId latestModule = this;
    for (String curId : l) {
      ModuleId curModule = new ModuleId(curId);
      if (product.equals(curModule.getProduct())
          && curModule.compareTo(latestModule) > 0) {
        latestModule = curModule;
      }
    }
    return latestModule.getId();
  }

  /**
   * Test whether this module has some prefix.
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
