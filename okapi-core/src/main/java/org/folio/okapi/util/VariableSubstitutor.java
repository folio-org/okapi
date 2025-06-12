package org.folio.okapi.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Substitute percent variables like %p, %c and %%. The percent variable name
 * consists of a percent character and the following character.
 */
public final class VariableSubstitutor {
  private VariableSubstitutor() {
  }

  /**
   * Return {@code original} with percent variables replaced.
   *
   * <p>Replace %p by {@code valueP}, %c by {@code valueC}, %% by % and
   * a percent sign followed by any other character by an empty string.
   *
   * <p>If valueP or valueC is null its replacement is an empty string.
   *
   * <p>Substitutions will not be substituted again:
   *
   * <p>{@code replace("%%p %%c %p %c", "p=%c", "c=%p") = "%p %c p=%c c=%p"}
   */
  public static String replace(String original, String valueP, String valueC) {
    if (original == null) {
      return null;
    }
    int pos = original.indexOf('%');
    if (pos == -1) {
      return original;
    }
    String p = valueP == null ? "" : valueP;
    String c = valueC == null ? "" : valueC;
    StringBuilder result = new StringBuilder(original.length() + c.length() + p.length());
    result.append(original, 0, pos);
    boolean isVariable = false;
    for ( ; pos < original.length(); pos++) {
      char current = original.charAt(pos);
      if (! isVariable) {
        if (current == '%') {
          isVariable = true;
        } else {
          result.append(current);
        }
        continue;
      }
      isVariable = false;
      switch (current) {
        case 'p':
          result.append(p);
          break;
        case 'c':
          result.append(c);
          break;
        case '%':
          result.append('%');
          break;
        default:
          // replace undefined % variable by empty string
      }
    }
    return result.toString();
  }

  /**
   * Replace percent variables in each String that is an JsonArray element
   * or a JsonObject value by using {@link #replace(String, String, String)}.
   * No replacement in JsonObject keys.
   *
   * <p>Replace within the complete JSON tree of {@code json}.
   */
  public static void replace(JsonArray json, String valueP, String valueC) {
    int n = json.size();
    for (int i = 0; i < n; i++) {
      Object value = json.getValue(i);
      if (value instanceof JsonArray) {
        replace((JsonArray) value, valueP, valueC);
      } else if (value instanceof JsonObject) {
        replace((JsonObject) value, valueP, valueC);
      } else if (value instanceof String) {
        json.set(i, replace((String) value, valueP, valueC));
      }
    }
  }

  /**
   * Replace percent variables in each String that is an JsonArray element
   * or a JsonObject value by using {@link #replace(String, String, String)}.
   * No replacement in JsonObject keys.
   *
   * <p>Replace within the complete JSON tree of {@code json}.
   */
  public static void replace(JsonObject json, String valueP, String valueC) {
    json.forEach(entry -> {
      Object value = entry.getValue();
      if (value instanceof JsonArray) {
        replace((JsonArray) value, valueP, valueC);
      } else if (value instanceof JsonObject) {
        replace((JsonObject) value, valueP, valueC);
      } else if (value instanceof String) {
        entry.setValue(replace((String) value, valueP, valueC));
      }
    });
  }
}
