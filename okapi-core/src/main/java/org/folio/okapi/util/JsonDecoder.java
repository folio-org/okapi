package org.folio.okapi.util;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import java.util.regex.Pattern;

/**
 * Wrapper for {@link Json#decodeValue} with improved error message.
 */
public final class JsonDecoder {
  private static final Pattern pattern = Pattern.compile(
      "^Failed to decode:Cannot deserialize value of type `\\[L(?<listElement>[^;`]+);` "
      + "from Object value \\(token `JsonToken\\.START_OBJECT`\\).*; "
      + "(?<at>line: \\d+, column: \\d+)\\]$", Pattern.DOTALL);

  private JsonDecoder() {
  }

  /**
   * Same as {@link Json#decodeValue(String, Class)} but with better error message
   * when `{` is found where an array is expected.
   */
  public static <T> T decode(String str, Class<T> clazz) throws DecodeException {
    try {
      return Json.decodeValue(str, clazz);
    } catch (DecodeException e) {
      var matcher = pattern.matcher(e.getMessage());
      if (! matcher.find()) {
        throw e;
      }
      var listElement = matcher.group("listElement");
      var at = matcher.group("at");
      throw new DecodeException("Expected `[` but found `{` when trying to "
          + "deserialize an array of " + listElement + " at " + at, e);
    }
  }
}
