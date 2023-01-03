package org.folio.okapi.common.refreshtoken.tokencache;

public interface ExpiryMap<K,V> {
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
