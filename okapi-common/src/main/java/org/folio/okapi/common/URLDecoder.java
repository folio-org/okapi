package org.folio.okapi.common;

import java.io.UnsupportedEncodingException;

public class URLDecoder {
  public static String decode(String url) {
    try {
      return java.net.URLDecoder.decode(url, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static String decode(String url, boolean plus) {
    if (!plus) {
      return decode(url.replaceAll("\\+", "%2B"));
    } else {
      return decode(url);
    }
  }
}
