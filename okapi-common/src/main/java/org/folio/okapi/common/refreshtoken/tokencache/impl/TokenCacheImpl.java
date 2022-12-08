package org.folio.okapi.common.refreshtoken.tokencache.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import org.folio.okapi.common.refreshtoken.tokencache.TokenCache;

public class TokenCacheImpl implements TokenCache {

  static class CacheValue {
    String value;
    long expires;

    boolean expired() {
      return expires < System.currentTimeMillis();
    }
  }

  final Map<String,CacheValue> entries;

  /**
   * Create token cache with given max capacity.
   * @param capacity max number of items before least recently added item is removed.
   */
  public TokenCacheImpl(int capacity) {
    entries = new LinkedHashMap<>(capacity) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String,CacheValue> eldest) {
        return size() > capacity || eldest.getValue().expired();
      }
    };
  }

  private static String key(String tenant, String user) {
    return tenant + ":" + user;
  }

  @Override
  public String get(String tenant, String user) {
    CacheValue c = entries.get(key(tenant, user));
    return c == null || c.expired() ? null : c.value;
  }

  @Override
  public void put(String tenant, String user, String value, long expires) {
    CacheValue c = new CacheValue();
    c.value = value;
    c.expires = expires;
    entries.put(key(tenant, user), c);
  }
}

