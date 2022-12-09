package org.folio.okapi.common.refreshtoken.tokencache.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import org.folio.okapi.common.refreshtoken.tokencache.ExpiryMap;

public class ExpiryMapImpl<K,V> implements ExpiryMap<K,V> {

  static class CacheValue<V> {
    V value;
    long expires;

    boolean expired() {
      return expires < System.currentTimeMillis();
    }
  }

  final Map<K,CacheValue<V>> entries;

  /**
   * Create token cache with given max capacity.
   * @param capacity max number of items before least recently added item is removed.
   */
  public ExpiryMapImpl(int capacity) {
    entries = new LinkedHashMap<>(capacity) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<K,CacheValue<V>> eldest) {
        return size() > capacity || eldest.getValue().expired();
      }
    };
  }

  @Override
  public V get(K key) {
    CacheValue<V> c = entries.get(key);
    return c == null || c.expired() ? null : c.value;
  }

  @Override
  public void put(K key, V value, long expires) {
    CacheValue<V> c = new CacheValue<>();
    c.value = value;
    c.expires = expires;
    entries.remove(key); // to put entry in front
    entries.put(key, c);
  }
}

