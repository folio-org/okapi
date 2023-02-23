package org.folio.okapi.common.refreshtoken.tokencache;

import org.folio.okapi.common.refreshtoken.tokencache.impl.ExpiryMapImpl;

public class RefreshTokenCache {

  final ExpiryMap<String,String> map;

  public RefreshTokenCache(int capacity) {
    map = new ExpiryMapImpl<>(capacity);
  }

  public void put(String refreshToken, String accessToken, long expiresTimeMillis) {
    map.put(refreshToken, accessToken, expiresTimeMillis);
  }

  public String get(String refreshToken) {
    return map.get(refreshToken);
  }
}
