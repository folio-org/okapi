package org.folio.okapi.util;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;

public class TokenCache {

  public static final long DEFAULT_TTL = 3 * 60 * 1000L;
  
  private final Map<String, CacheEntry> cache = new HashMap<>();
  private final long ttl;
  
  private static final Logger logger = OkapiLogger.get(TokenCache.class);
  
  public TokenCache() {
    this(DEFAULT_TTL);
  }
  
  public TokenCache(long ttl) {
    this.ttl = ttl;
  }
  
  /**
   * Cache an entry.
   * @param method HTTP method
   * @param path path pattern 
   * @param userId X-Okapi-User-Id header to cache
   * @param xokapiPerms X-Okapi-Permissions header to cache
   * @param token access token to cache
   */
  public void put(String tenant, String method, String path, String userId, String xokapiPerms,
      String keyToken, String token) {
    long now = System.currentTimeMillis();
    CacheEntry entry = new CacheEntry(token, userId, xokapiPerms, now + ttl);
    String key = genKey(method, path, keyToken, userId);
    MetricsHelper.recordTokenCacheCached(tenant, method, path, userId);
    logger.info("Caching: {} {}", key, token);
    cache.put(key, entry);
  }
  
  /**
   * Get a cached entry.
   * @param tenant tenant id
   * @param method HTTP method
   * @param path path pattern
   * @param token X-Okapi-Token header
   * @param userId X-Okapi-User-Id header 
   * @return cache entry or null
   */
  public CacheEntry get(String tenant, String method, String path, String token, String userId) {
    String key = genKey(method, path, token, userId);
    CacheEntry ret = cache.get(genKey(method, path, token, userId));
    if (ret == null) {
      MetricsHelper.recordTokenCacheMiss(tenant, method, path, userId);
      logger.info("Cache Miss: {}", key);
      return ret;
    } else if (ret.isExpired()) {
      MetricsHelper.recordTokenCacheExpired(tenant, method, path, userId);
      logger.info("Cache Hit (Expired): {}", key);
      cache.remove(key);
      return null;
    } else {
      MetricsHelper.recordTokenCacheHit(tenant, method, path, userId);
      logger.info("Cache Hit: {} -> {}", key, ret.token);
      return ret;
    }
  }
  
  private String genKey(String method, String path, String token, String userId) {
    return (method + "|" + path + "|" + token + "|" + userId).replaceAll("[\n|\t|\r]", "");
  }
  
  public static final class CacheEntry {
    public final String token;
    public final String xokapiPermissions;
    public final String xokapiUserid;
    public final long expires;
    
    private CacheEntry() {
      throw new IllegalArgumentException();
      // Should never get here.
    }
    
    /**
     * Create a cache entry.
     * @param token the access token to cache
     * @param xokapiUserid the X-Okapi-User-Id header
     * @param xokapiPermissions the X-Okapi-Permissions header
     * @param expires instant in ms since epoch when this cache entry expires
     */
    public CacheEntry(String token, String xokapiUserid, String xokapiPermissions, long expires) {
      this.token = token;
      this.xokapiPermissions = xokapiPermissions;
      this.xokapiUserid = xokapiUserid;
      this.expires = expires;
    }
    
    public boolean isExpired() {
      return System.currentTimeMillis() > expires;
    }
  }
}
