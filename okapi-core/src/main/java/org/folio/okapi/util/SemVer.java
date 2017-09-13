package org.folio.okapi.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class SemVer implements Comparable<SemVer> {

  private final List<String> preRelease = new LinkedList<>();
  private final List<String> versions = new LinkedList<>();
  private String metadata;

  public SemVer(String v) {
    int offset = 0;

    offset = parseComp(v, offset, versions);
    if (offset == -1) {
      throw new IllegalArgumentException("missing major version: " + v);
    }
    while (offset < v.length() && v.charAt(offset) == '.') {
      offset = parseComp(v, offset + 1, versions);
      if (offset == -1) {
        throw new IllegalArgumentException("missing version component");
      }
    }
    if (offset < v.length() && v.charAt(offset) == '-') {
      offset = parseComp(v, offset + 1, preRelease);
      if (offset == -1) {
        throw new IllegalArgumentException("missing pre-release version component");
      }
      while (offset < v.length() && v.charAt(offset) == '.') {
        offset = parseComp(v, offset + 1, preRelease);
        if (offset == -1) {
          throw new IllegalArgumentException("missing pre-release version component");
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

  public boolean hasPreRelease() {
    return !preRelease.isEmpty();
  }

  public boolean hasPrefix(SemVer other) {
    Iterator<String> i1 = this.versions.iterator();
    Iterator<String> i2 = other.versions.iterator();
    int level = 4; // major returns +-4, minor +-3, patch +- 2, rest +-1.
    while (i1.hasNext() && i2.hasNext()) {
      int v = compareComp(i1.next(), i2.next());
      if (v != 0) {
        return false;
      }
    }
    if (!i1.hasNext() && i2.hasNext()) {
      return false;
    }
    else if (i1.hasNext() && !i2.hasNext()) {
      return true;
    }
    i1 = this.preRelease.iterator();
    i2 = other.preRelease.iterator();
    while (i1.hasNext() && i2.hasNext()) {
      int v = compareComp(i1.next(), i2.next());
      if (v != 0) {
        return false;
      }
    }
    if (!i1.hasNext() && i2.hasNext()) {
      return false;
    }
    else if (i1.hasNext() && !i2.hasNext()) {
      return true;
    }
    if (this.metadata != null && other.metadata != null) {
      int v = this.metadata.compareTo(other.metadata);
      if (v != 0) {
        return false;
      }
    } else if (this.metadata == null && other.metadata != null) {
      return false;
    }
    return true;
  }

  public int compareTo(SemVer other) {
    Iterator<String> i1 = this.versions.iterator();
    Iterator<String> i2 = other.versions.iterator();
    int level = 4; // major returns +-4, minor +-3, patch +- 2, rest +-1.
    while (i1.hasNext() && i2.hasNext()) {
      int v = compareComp(i1.next(), i2.next());
      if (v > 0) {
        return level;
      } else if (v < 0) {
        return -level;
      }
      if (level > 2) {
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

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    Iterator<String> it = this.versions.iterator();
    if (it.hasNext()) {
      b.append("version:");
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
