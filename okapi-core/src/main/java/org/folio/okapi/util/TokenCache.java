package org.folio.okapi.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;

public class TokenCache {

  public static final long DEFAULT_TTL = 3 * 60 * 1000L;
  public static final int DEFAULT_MAX_SIZE = 10000;

  private final Map<String, CacheEntry> cache;
  private final long ttl;

  private static final Logger logger = OkapiLogger.get(TokenCache.class);

  /**
   * Convenience constructor using the default TTL and max size.
   */
  public TokenCache() {
    this(DEFAULT_TTL, DEFAULT_MAX_SIZE);
  }

  /**
   * Constructor using the provided TTL and maxSize.
   * 
   * @param ttl cache entry time to live in milliseconds
   * @param maxSize the maximum number of entries that may be cached at once
   */
  private TokenCache(long ttl, int maxSize) {
    this.ttl = ttl;

    this.cache = Collections.synchronizedMap(new LruCache<>(maxSize));
  }

  /**
   * Cache an entry.
   * 
   * @param method HTTP method
   * @param path path pattern
   * @param userId X-Okapi-User-Id header to cache
   * @param xokapiPerms X-Okapi-Permissions header to cache
   * @param keyToken the token to be used in the cache key, from the request which triggered the
   *        call to mod-authtoken
   * @param token access token to cache - from the mod-authtoken response
   */
  public void put(String tenant, String method, String path, String userId, String xokapiPerms,
      String keyToken, String token) {
    long now = System.currentTimeMillis();
    CacheEntry entry = new CacheEntry(token, userId, xokapiPerms, now + ttl);
    String key = genKey(method, path, userId, keyToken);
    MetricsHelper.recordTokenCacheCached(tenant, method, path, userId);
    logger.info("Caching: {} -> {}", key, token);
    cache.put(key, entry);
  }

  /**
   * Get a cached entry.
   * 
   * @param tenant tenant id
   * @param method HTTP method
   * @param path path pattern
   * @param token X-Okapi-Token header
   * @param userId X-Okapi-User-Id header
   * @return cache entry or null
   */
  public CacheEntry get(String tenant, String method, String path, String userId, String token) {
    String key = genKey(method, path, userId, token);
    CacheEntry ret = cache.get(key);
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

  private String genKey(String method, String path, String userId, String token) {
    return (userId + "|" + method + "|" + path + "|" + token).replaceAll("[\n\t\r]", "");
  }

  public static Builder builder() {
    return new Builder();
  }
  
  public int size() {
    return cache.size();
  }
  
  public static final class LruCache<K, V> extends LinkedHashMap<K, V> {

    private static final long serialVersionUID = -6197036022604882327L;
    private final int maxEntries;

    public LruCache(final int maxEntries) {
      super((int) (maxEntries / 0.75f), 0.75f, true);
      this.maxEntries = maxEntries;
    }

    @Override
    protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
      return super.size() > maxEntries;
    }

    @Override
    public boolean equals(Object obj) {
      if (!super.equals(obj)) {
        return false;
      }
      LruCache<?, ?> other = (LruCache<?, ?>) obj;
      return this.maxEntries == other.maxEntries;
    }

    @Override
    public int hashCode() {
      return 89 * super.hashCode() + maxEntries;
    }
  }

  public static final class Builder {

    private long ttl = DEFAULT_TTL;
    private int maxSize = DEFAULT_MAX_SIZE;

    public TokenCache build() {
      return new TokenCache(this.ttl, this.maxSize);
    }

    public Builder withTtl(long ttl) {
      this.ttl = ttl;
      return this;
    }

    public Builder withMaxSize(int maxSize) {
      this.maxSize = maxSize;
      return this;
    }
  }

  public static final class CacheEntry {
    public final String token;
    public final String xokapiPermissions;
    public final String xokapiUserid;
    public final long expires;

    private CacheEntry() {
      // Should never get here.
      throw new IllegalArgumentException();
    }

    /**
     * Create a cache entry.
     * 
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
