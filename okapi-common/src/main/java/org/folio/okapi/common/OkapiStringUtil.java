package org.folio.okapi.common;

import io.vertx.core.buffer.Buffer;

public final class OkapiStringUtil {
  private static final int MAX_LOG_STRING_LENGTH = 200;

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

  /**
   * Remove line-breaking characters to avoid log mixup
   * (<a href="https://next.sonarqube.com/sonarqube/coding_rules?open=javasecurity%3AS5145">javasecurity:S5145</a>).
   */
  public static String removeLogCharacters(String s) {
    if (s == null) {
      return null;
    }
    return s.replaceAll("[\\n\\r\\t]", "_");
  }

  /**
   * If buffer doesn't exceed maxLength return it. Otherwise return it after trimming it to
   * maxLength by removing characters in the middle and replacing them with the … character.
   */
  @SuppressWarnings({
      "java:S109",  // suppress "Assign this magic number 2 to a well-named constant
      // and use the constant instead." because / 2 indicates a half.
  })
  public static String trim(Buffer buffer, int maxLength) {
    if (buffer == null) {
      return null;
    }
    if (buffer.length() <= maxLength) {
      return buffer.toString();
    }
    return buffer.getString(0, maxLength / 2) + "…"
        + buffer.getString(buffer.length() - (maxLength - 1) / 2, buffer.length());
  }

  /**
   * If s doesn't exceed maxLength return it. Otherwise return it after trimming it to
   * maxLength by removing characters in the middle and replacing them with the … character.
   */
  @SuppressWarnings({
      "java:S109",  // suppress "Assign this magic number 2 to a well-named constant
      // and use the constant instead." because / 2 indicates a half.
  })
  public static String trim(CharSequence s, int maxLength) {
    if (s == null) {
      return null;
    }
    if (s.length() <= maxLength) {
      return s.toString();
    }
    return s.subSequence(0, maxLength / 2) + "…"
        + s.subSequence(s.length() - (maxLength - 1) / 2, s.length());
  }

  /**
   * Limit to 200 characters and replace each line breaking character with _.
   *
   * @see #trim(Buffer, int)
   * @see #removeLogCharacters(String)
   */
  public static String sanitizeForLog(Buffer buffer) {
    return removeLogCharacters(trim(buffer, MAX_LOG_STRING_LENGTH));
  }

  /**
   * Limit to 200 characters and replace each line breaking character with _.
   *
   * @see #trim(CharSequence, int)
   * @see #removeLogCharacters(String)
   */
  public static String sanitizeForLog(CharSequence s) {
    return removeLogCharacters(trim(s, MAX_LOG_STRING_LENGTH));
  }
}
