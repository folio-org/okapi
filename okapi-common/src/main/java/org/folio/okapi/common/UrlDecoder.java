package org.folio.okapi.common;

import java.nio.charset.StandardCharsets;

public class UrlDecoder {
  private UrlDecoder() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Decode URL query component.
   * @param url component
   * @return decoded value
   */
  public static String decode(String url) {
    return java.net.URLDecoder.decode(url, StandardCharsets.UTF_8);
  }

  /**
   * Decode URL component with option for query decoding path (plus=true).
   * @param url component
   * @param plus true if decoding path component; false if decoding query component
   * @return decoded value
   */
  public static String decode(String url, boolean plus) {
    if (!plus) {
      return decode(url.replace("+", "%2B"));
    } else {
      return decode(url);
    }
  }
}
