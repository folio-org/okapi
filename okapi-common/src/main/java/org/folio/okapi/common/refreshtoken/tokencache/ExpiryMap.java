package org.folio.okapi.common.refreshtoken.tokencache;

import org.folio.okapi.common.refreshtoken.tokencache.impl.ExpiryMapImpl;

public interface ExpiryMap<K,V> {
  /**
   * Create a cache with given capacity.
   * @param capacity number of tokens kept before they are removed at put
   * @return cache instance.
   */
  static ExpiryMap create(int capacity) {
    return new ExpiryMapImpl(capacity);
  }

  /**
   * Put a cache.
   * @param key cache key
   * @param value cache value; should not be null
   * @param expiresTimeMillis the point where the entry expires.
   */
  void put(K key, V value, long expiresTimeMillis);


  /**
   * Get a cache entry.
   * @param key cache key
   * @return value if found; null if not found
   */
  V get(K key);

}
