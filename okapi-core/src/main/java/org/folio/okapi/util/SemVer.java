package org.folio.okapi.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class SemVer implements Comparable<SemVer> {

  private String version;
  private String product;
  private List<String> preRelease = new LinkedList<>();
  private List<String> versions = new LinkedList();
  private String metadata;

  public SemVer(char sep, String s) {
    for (int i = 0; i < s.length() - 1; i++) {
      if (s.charAt(i) == sep && Character.isDigit(s.charAt(i + 1))) {
        product = s.substring(0, i);
        setVersion(s.substring(i + 1));
        return;
      }
    }
    throw new IllegalArgumentException("missing semantic version: " + s);
  }

  public SemVer(String v) {
    product = null;
    setVersion(v);
  }

  private int compareComp(String c1, String c2) {
    if (Character.isDigit(c1.charAt(0)) && Character.isDigit(c2.charAt(0))) {
      return Integer.parseInt(c1) - Integer.parseInt(c2);
    } else {
      return c1.compareTo(c2);
    }
  }

  private int parseComp(String v, int offset, List<String> result) {
    int i = offset;
    if (i >= v.length()) {
      return -1;
    }
    if (Character.isDigit(v.charAt(i))) {
      while (i < v.length() && Character.isDigit(v.charAt(i))) {
        i++;
      }
    } else {
      while (i < v.length()) {
        char ch = v.charAt(i);
        if (ch == '-' || ch == '+' || ch == '.') {
          break;
        }
        i++;
      }
    }
    if (i == offset) {
      return -1;
    }
    result.add(v.substring(offset, i));
    return i;
  }

  private void parseVersion(String v) {
    int offset = 0;
    String result = "0";

    offset = parseComp(v, offset, versions);
    if (offset == -1) {
      throw new IllegalArgumentException("missing major version: " + v);
    }
    while (offset < v.length() && v.charAt(offset) == '.') {
      offset = parseComp(v, offset + 1, versions);
      if (offset == -1) {
        throw new IllegalArgumentException("missing version");
      }
    }
    if (offset < v.length() && v.charAt(offset) == '-') {
      offset = parseComp(v, offset + 1, preRelease);
      if (offset == -1) {
        throw new IllegalArgumentException("missing pre-release version");
      }
      while (offset < v.length() && v.charAt(offset) == '.') {
        offset = parseComp(v, offset + 1, preRelease);
        if (offset == -1) {
          throw new IllegalArgumentException("missing pre-release version");
        }
      }
    }
    if (offset == v.length()) {
      metadata = null;
    } else if (v.charAt(offset) == '+') {
      metadata = v.substring(offset + 1);
    } else {
      throw new IllegalArgumentException("invalid semver: " + v);
    }
  }

  public int compareTo(SemVer other) {
    Iterator<String> i1 = this.versions.iterator();
    Iterator<String> i2 = other.versions.iterator();
    int level = 3; // major returns +-3, minor +-2, rest +-1.
    while (i1.hasNext() && i2.hasNext()) {
      int v = compareComp(i1.next(), i2.next());
      if (v > 0) {
        return level;
      } else if (v < 0) {
        return -level;
      }
      if (level > 1) {
        level--;
      }
    }
    if (i1.hasNext()) {
      return level;
    } else if (i2.hasNext()) {
      return -level;
    }
    i1 = this.preRelease.iterator();
    i2 = other.preRelease.iterator();
    // omitted pre-release makes it a higher version
    if (!i1.hasNext() && i2.hasNext()) {
      return 1;
    }
    if (i1.hasNext() && !i2.hasNext()) {
      return -1;
      }
    while (i1.hasNext() && i2.hasNext()) {
      int v = compareComp(i1.next(), i2.next());
      if (v > 0) {
        return 1;
      } else if (v < 0) {
        return -1;
      }
    }
    if (i1.hasNext()) {
      return 1;
    } else if (i2.hasNext()) {
      return -1;
    }
    if (this.metadata != null && other.metadata != null) {
      int v = this.metadata.compareTo(other.metadata);
      if (v > 0) {
        return 1;
      } else if (v < 0) {
        return -1;
      }
    } else if (this.metadata != null && other.metadata == null) {
      return 1;
    } else if (this.metadata == null && other.metadata != null) {
      return -1;
    }
    return 0;
  }

  private void setVersion(String v) {
    parseVersion(v);
    this.version = v;
  }

  public String getVersion() {
    return version;
  }

  public String getProduct() {
    return product;
  }

  public String toString() {
    StringBuilder b = new StringBuilder();
    Iterator<String> it = this.versions.iterator();
    if (it.hasNext()) {
      b.append("versions:");
      while (it.hasNext()) {
        b.append(" ");
        b.append(it.next());
      }
    }
    it = this.preRelease.iterator();
    if (it.hasNext()) {
      b.append(" pre:");
      while (it.hasNext()) {
        b.append(" ");
        b.append(it.next());
      }
    }
    if (metadata != null) {
      b.append(" metadata: ");
      b.append(metadata);
    }
    return b.toString();
  }
}
