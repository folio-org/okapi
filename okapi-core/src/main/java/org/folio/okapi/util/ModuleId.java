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
    throw new IllegalArgumentException("missing semantic version: " + s);
  }

  public int compareTo(ModuleId other) {
    return semVer.compareTo(other.semVer);
  }

  public String toString() {
    return "product: " + product + semVer.toString();
  }

  public static int compare(String i1, String i2) {
    ModuleId m1 = new ModuleId(i1);
    ModuleId m2 = new ModuleId(i2);
    return m1.compareTo(m2);
  }
}
