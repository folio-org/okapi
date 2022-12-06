package org.folio.okapi.common.refreshtoken.tokencache;

import org.folio.okapi.common.refreshtoken.tokencache.impl.TokenCacheImpl;

public interface TokenCache {
  /**
   * Create a cache with given capacity.
   * @param capacity number of tokens kept before they are removed at put
   * @return cache instance.
   */
  static TokenCache create(int capacity) {
    return new TokenCacheImpl(capacity);
  }

  /**
   * Put a cache value for tenant and user.
   * @param tenant Okapi tenant
   * @param user user within tenant
   * @param value typically a token; should not be null
   * @param expiresTimeMillis the point where the entry expires.
   */
  void put(String tenant, String user, String value, long expiresTimeMillis);


  /**
   * Get a cache entry.
   * @param tenant Okapi tenant
   * @param user user within tenant
   * @return value if found; null if not found
   */
  String get(String tenant, String user);

}
