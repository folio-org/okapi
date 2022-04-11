package org.folio.okapi.common;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Semantic version implementation. Based
 * <a href="https://semver.org/spec/v1.0.0.html">semver 1.0.0</a>,
 * but is a little liberal at the moment, eg only major component
 * is (eg 1) required or even more than 3 components for dot-separated
 * list (eg 1.2.3.4).
 */
public class SemVer implements Comparable<SemVer> {

  private final List<String> preRelease = new LinkedList<>();
  private final List<String> versions = new LinkedList<>();
  private final String metadata;

  /**
   * Construct semantic version from string.
   * May throw IllegalArgumentException if string supplied can not be parsed
   * @param v semantic version
   */
  public SemVer(String v) {
    int offset = 0;

    offset = parseComp(v, true, offset, versions);
    if (offset == -1) {
      throw new IllegalArgumentException("missing major version: " + v);
    }
    while (offset < v.length() && v.charAt(offset) == '.') {
      offset = parseComp(v, true, offset + 1, versions);
      if (offset == -1) {
        throw new IllegalArgumentException("missing version component");
      }
    }
    if (offset < v.length() && v.charAt(offset) == '-') {
      offset = parseComp(v, false, offset + 1, preRelease);
      if (offset == -1) {
        throw new IllegalArgumentException("missing pre-release version component");
      }
      while (offset < v.length() && v.charAt(offset) == '.') {
        offset = parseComp(v, false, offset + 1, preRelease);
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

  private boolean allDigits(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isDigit(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Compare two version components.
   * If both operands are numeric, compare numerically
   * If both operands are non-numeric, compare lexicographically
   * If mixed, non-numeric operand compares over numeric operand
   * No comparison for mixed operands - in order offer transitive sorting
   * In reality not a problem because systems most likely will use a common
   * scheme for each component for some versioning system
   * @param c1 left operand
   * @param c2 right operand
   * @return <0 if c1 < c2, 0 if c1 == c2, >0 if c1 > c2
   */
  private long compareComp(String c1, String c2) {
    if (allDigits(c1)) {
      if (allDigits(c2)) {
        return Long.parseLong(c1) - Long.parseLong(c2);
      } else {
        return -1;
      }
    } else {
      if (allDigits(c2)) {
        return 1;
      } else {
        return c1.compareTo(c2);
      }
    }
  }

  private int parseComp(String v, boolean digits, int offset, List<String> result) {
    int i = offset;
    if (!digits) {
      while (i < v.length()) {
        char ch = v.charAt(i);
        if (ch == '+' || ch == '.') {
          break;
        }
        i++;
      }
    } else {
      while (i < v.length() && Character.isDigit(v.charAt(i))) {
        i++;
      }
      if (i > offset + 18) { // 18 digits is within Long / 2^63-1
        throw new IllegalArgumentException("at most 18 digits for numeric component");
      }
    }
    if (i == offset) {
      return -1;
    }
    result.add(v.substring(offset, i));
    return i;
  }

  /**
   * Test whether the version appears to be an NPM snapshot (at least in FOLIO).
   * @return true if it appears to be an NPM snapshot; false otherwise
   */
  public boolean hasNpmSnapshot() {
    return versions.size() == 3 && versions.get(2).length() >= 5;
  }

  /**
   * Test whether version has pre-release.
   * @return true if version has pre-release; false otherwise
   */
  public boolean hasPreRelease() {
    return !preRelease.isEmpty();
  }

  /**
   * Test whether this version has prefix of other.
   * @param other the prefix
   * @return true if this version has prefix of other; false otherwise
   */
  public boolean hasPrefix(SemVer other) {
    Iterator<String> i1 = this.versions.iterator();
    Iterator<String> i2 = other.versions.iterator();
    while (i1.hasNext() && i2.hasNext()) {
      long v = compareComp(i1.next(), i2.next());
      if (v != 0) {
        return false;
      }
    }
    if (i1.hasNext()) { // i2.hasNext is null
      return true;
    }
    if (i2.hasNext()) { // i1.hasNext is null
      return false;
    }
    i1 = this.preRelease.iterator();
    i2 = other.preRelease.iterator();
    while (i1.hasNext() && i2.hasNext()) {
      long v = compareComp(i1.next(), i2.next());
      if (v != 0) {
        return false;
      }
    }
    if (i1.hasNext()) {
      return true;
    }
    if (i2.hasNext()) {
      return false;
    }
    if (other.metadata != null) {
      if (this.metadata != null) {
        int v = this.metadata.compareTo(other.metadata);
        if (v != 0) {
          return false;
        }
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * Compares two semantic versions.
   * @param other version to compare against this
   * @return -4 is this is major less than other; -3 if this is minor less than
   *     other; -2 if this is patch less than other; -1 for pre-release/build less;
   *     0 if version as equal; 1, 2, 3, 4, as the opposite negatives
   */
  @Override
  public int compareTo(SemVer other) {
    Iterator<String> i1 = this.versions.iterator();
    Iterator<String> i2 = other.versions.iterator();
    int level = 4; // major returns +-4, minor +-3, patch +- 2, rest +-1.
    while (i1.hasNext() && i2.hasNext()) {
      long v = compareComp(i1.next(), i2.next());
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
      long v = compareComp(i1.next(), i2.next());
      if (v > 0) {
        return 1;
      } else if (v < 0) {
        return -1;
      }
    }
    if (i1.hasNext()) {
      return 1;
    }
    if (i2.hasNext()) {
      return -1;
    }
    if (this.metadata != null) {
      if (other.metadata != null) {
        int v = this.metadata.compareTo(other.metadata);
        if (v > 0) {
          return 1;
        } else if (v < 0) {
          return -1;
        }
      } else {
        return 1;
      }
    } else if (other.metadata != null) {
      return -1;
    }
    return 0;
  }

  /**
   * Test whether this version is equal to other.
   * @param that Test this against that
   * @return true if equal; false otherwise
   */
  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof SemVer)) {
      return false;
    }
    return compareTo((SemVer) that) == 0;
  }

  @Override
  public int hashCode() {
    int c = 3;
    Iterator<String> it = versions.iterator();
    while (it.hasNext()) {
      c = c * 31 + it.next().hashCode();
    }
    it = preRelease.iterator();
    while (it.hasNext()) {
      c = c * 31 + it.next().hashCode();
    }
    return c;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    String sep = "";
    Iterator<String> it = this.versions.iterator();
    while (it.hasNext()) {
      b.append(sep);
      b.append(it.next());
      sep = ".";
    }
    sep = "-";
    it = this.preRelease.iterator();
    if (it.hasNext()) {
      while (it.hasNext()) {
        b.append(sep);
        b.append(it.next());
        sep = ".";
      }
    }
    if (metadata != null) {
      b.append("+");
      b.append(metadata);
    }
    return b.toString();
  }
}
