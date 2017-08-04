package org.folio.okapi.util;

public class ModuleId implements Comparable<ModuleId> {

  private String product;
  private SemVer semVer;

  public ModuleId(String s) {
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

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append("module: ");
    b.append(product);
    if (semVer != null) {
      b.append(" ");
      b.append(semVer.toString());
    }
    return b.toString();
  }

  public static int compare(String i1, String i2) {
    ModuleId m1 = new ModuleId(i1);
    ModuleId m2 = new ModuleId(i2);
    return m1.compareTo(m2);
  }

  public boolean hasPrefix(ModuleId other) {
    if (!this.product.equals(other.product)) {
      return false;
    } else if (this.semVer != null && other.semVer != null) {
      return semVer.hasPrefix(other.semVer);
    } else if (this.semVer == null && other.semVer != null) {
      return false;
    } else {
      return true;
    }
  }
}
