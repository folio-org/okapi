package org.folio.okapi.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class OkapiStream {

  public static String toString(InputStream inputStream) {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    try {
      while ((length = inputStream.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }
    } catch (Exception ex) {
    }
    return result.toString();
  }
}
