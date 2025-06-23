package org.folio.okapi.util;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.vertx.core.MultiMap;
import java.util.List;
import org.folio.okapi.common.Constants;
import org.folio.okapi.common.XOkapiHeaders;

public class TokenHeader {

  private TokenHeader() { }

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
    String cookieHeader = headers.get("Cookie");
    if (cookieHeader != null) {
      List<Cookie> cookies = ServerCookieDecoder.STRICT.decodeAll(cookieHeader);
      String accessToken = null;
      for (Cookie cookie : cookies) {
        if (Constants.COOKIE_ACCESS_TOKEN.equals(cookie.name())) {
          if (accessToken != null) {
            throw new IllegalArgumentException("Multiple " + Constants.COOKIE_ACCESS_TOKEN
                + " cookie names");
          }
          accessToken = cookie.value();
        }
      }
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
