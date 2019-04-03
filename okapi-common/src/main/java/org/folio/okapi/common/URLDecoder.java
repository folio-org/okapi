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
}
