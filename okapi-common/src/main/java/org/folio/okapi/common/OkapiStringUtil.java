package org.folio.okapi.common;

public final class OkapiStringUtil {
  private OkapiStringUtil() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  /**
   * Trim all trailing slashes. Return null for null input.
   */
  public static String trimTrailingSlashes(CharSequence s) {
    if (s == null) {
      return null;
    }
    int end = s.length() - 1;
    while (end >= 0 && s.charAt(end) == '/') {
      end--;
    }
    return s.subSequence(0, end + 1).toString();
  }
}
