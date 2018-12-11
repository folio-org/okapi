package org.folio.okapi.common;
import java.util.Collection;

public class ModuleId implements Comparable<ModuleId> {

  private String product;
  private SemVer semVer;
  private final String id;

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

  public String getId() {
    return id;
  }

  public boolean hasSemVer() {
    return semVer != null;
  }

  public boolean hasNpmSnapshot() {
    return semVer != null && semVer.hasNpmSnapshot();
  }

  public boolean hasPreRelease() {
    return semVer != null && semVer.hasPreRelease();
  }

  public String getProduct() {
    return product;
  }

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
