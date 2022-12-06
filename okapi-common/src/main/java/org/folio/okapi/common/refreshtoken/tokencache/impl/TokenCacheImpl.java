package org.folio.okapi.common.refreshtoken.tokencache.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.folio.okapi.common.refreshtoken.tokencache.TokenCache;

public class TokenCacheImpl implements TokenCache {

  static class CacheValue {
    String value;
    long expires;

    boolean expired() {
      return expires < System.currentTimeMillis();
    }
  }

  Map<String,CacheValue> entries = new LinkedHashMap<>();

  final int capacity;

  public TokenCacheImpl(int capacity) {
    this.capacity = capacity;
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
    prune();
  }

  private void prune() {
    entries.entrySet().removeIf(e -> e.getValue().expired());
    AtomicInteger d = new AtomicInteger(entries.size() - capacity);
    entries.entrySet().removeIf(e -> d.getAndDecrement() > 0);
  }
}

