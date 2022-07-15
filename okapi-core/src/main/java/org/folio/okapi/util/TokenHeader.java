package org.folio.okapi.util;

import io.vertx.core.MultiMap;
import org.folio.okapi.common.XOkapiHeaders;

public class TokenHeader {

  private TokenHeader() { }

  /**
   * Get cookie value from name. We are being strict here!
   * @param cookie cookie string
   * @param name name to look for
   * @return value; null if not found.
   * @throws IllegalArgumentException if name occurs multiple times.
   */
  static String cookieValue(String cookie, String name) {
    String[] components = cookie.split("; ");
    String v = null;
    for (String component : components) {
      if (component.startsWith(name + "=")) {
        if (v != null) {
          throw new IllegalArgumentException("multiple occurrences of " + name + " in cookie");
        }
        v = component.substring(component.indexOf('=') + 1);
      }
    }
    return v;
  }

  /**
   * Get token from X-Okapi-Token, Authorization, Cookie headers.
   * @param headers request headers.
   * @return resulting X-Okapi-Token string; null if no token found.
   */
  public static String check(MultiMap headers) {
    String token = headers.get(XOkapiHeaders.TOKEN);
    String auth = headers.get(XOkapiHeaders.AUTHORIZATION);
    if (auth != null) {
      if (auth.startsWith("Bearer ")) {
        auth = auth.substring(6).trim();
      }
      if (token != null) {
        if (!auth.equals(token)) {
          throw new IllegalArgumentException("X-Okapi-Token is not equal to Authorization token");
        }
      } else {
        token = auth;
        headers.set(XOkapiHeaders.TOKEN, auth);
      }
      headers.remove(XOkapiHeaders.AUTHORIZATION);
    }
    String cookie = headers.get("Cookie");
    if (cookie != null) {
      String accessToken = cookieValue(cookie, "accessToken");
      if (accessToken != null) {
        if (token != null) {
          if (!token.equals(accessToken)) {
            throw new IllegalArgumentException("X-Okapi-Token conflicts with Cookie");
          }
        } else {
          token = accessToken;
          headers.set(XOkapiHeaders.TOKEN, accessToken);
        }
      }
    }
    return token;
  }
}
