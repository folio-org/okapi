package org.folio.okapi.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class TestBase {

  /**
   * @return resource file at resourcePath as UTF-8
   */
  protected static String getResource(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte [] buffer = new byte[1024];
      while (true) {
        int length = inputStream.read(buffer);
        if (length == -1) {
          break;
        }
        result.write(buffer, 0, length);
      }
      return result.toString(StandardCharsets.UTF_8.name());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
