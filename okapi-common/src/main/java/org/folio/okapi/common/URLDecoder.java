package org.folio.okapi.common;

import java.io.UnsupportedEncodingException;

public class URLDecoder {
  public static String decode(String url) {
    try {
      return java.net.URLDecoder.decode(url, "UTF_8");
    } catch (UnsupportedEncodingException e) {
      return url;
    }
  }
}
