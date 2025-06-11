package org.folio.okapi.util;

import java.util.Collections;
import java.util.Iterator;
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
   * Constructor using the provided TTL and maxSize.
   * 
   * <p>Once the cache reaches maximum capcity, the least-recently accessed entry will be evicted
   * upon insertion of a new entry
   *
   * <p>Cache entries will be pruned if they're expired upon access via the <code>get(...)</code>
   * method.
   *
   * <p>The <code>put(...)</code> method attempts to shrink the cache size by pruning the 2
   * least-recently accessed entries if they're expired.
   *
   * @param ttl cache entry time to live in milliseconds
   * @param maxSize the maximum number of entries that may be cached at once
   */
  private TokenCache(long ttl, int maxSize) {
    logger.info("Initializing token cache w/ ttl: {}, maxSize: {}", ttl, maxSize);
    this.ttl = ttl;

    this.cache = Collections.synchronizedMap(new LruCache(maxSize));
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
    String key = genKey(tenant, method, path, keyToken);
    MetricsHelper.recordTokenCacheCached(tenant, method, path, userId);
    logger.debug("Caching: {} -> {}", key, token);
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
    String key = genKey(tenant, method, path, token);
    CacheEntry ret = cache.get(key);
    if (ret == null) {
      MetricsHelper.recordTokenCacheMiss(tenant, method, path, userId);
      logger.debug("Cache Miss: {}", key);
      return null;
    } else if (ret.isExpired()) {
      MetricsHelper.recordTokenCacheExpired(tenant, method, path, userId);
      logger.debug("Cache Hit (Expired): {}", key);
      cache.remove(key);
      return null;
    } else {
      MetricsHelper.recordTokenCacheHit(tenant, method, path, userId);
      logger.debug("Cache Hit: {} -> {}", key, ret.token);
      return ret;
    }
  }

  private String genKey(String tenantId, String method, String path, String token) {
    String tok = token == null ? "null" : token.replaceAll("[\n\t\r]", "");
    return (tenantId + "|" + method + "|" + path + "|" + tok);
  }

  public static Builder builder() {
    return new Builder();
  }

  public int size() {
    return cache.size();
  }

  public static final class LruCache extends LinkedHashMap<String, CacheEntry> {

    private static final long serialVersionUID = -6197036022604882327L;
    private final int maxEntries;

    public LruCache(final int maxEntries) {
      super((int) (maxEntries / 0.75), 0.75f, true);
      this.maxEntries = maxEntries;
    }

    @Override
    public CacheEntry put(String key, CacheEntry value) {
      CacheEntry prev = super.put(key, value);

      if (super.size() >= 3) {
        Iterator<Map.Entry<String, CacheEntry>> iter = entrySet().iterator();
        for (int i = 0; i < 2; i++) {
          Map.Entry<String, CacheEntry> entry = iter.next();
          if (entry.getValue().isExpired()) {
            iter.remove();
          } else {
            break;
          }
        }
      }

      return prev;
    }

    @Override
    protected boolean removeEldestEntry(final Map.Entry<String, CacheEntry> eldest) {
      return super.size() > maxEntries;
    }

    @Override
    public boolean equals(Object obj) {
      if (!super.equals(obj)) {
        return false;
      }
      LruCache other = (LruCache) obj;
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
    public final String permissions;
    public final String userId;
    public final long expires;

    private CacheEntry() {
      // Should never get here.
      throw new IllegalArgumentException();
    }

    /**
     * Create a cache entry.
     * 
     * @param token the access token to cache
     * @param userId the X-Okapi-User-Id header
     * @param permissions the X-Okapi-Permissions header
     * @param expires instant in ms since epoch when this cache entry expires
     */
    public CacheEntry(String token, String userId, String permissions, long expires) {
      this.token = token;
      this.permissions = permissions;
      this.userId = userId;
      this.expires = expires;
    }

    public boolean isExpired() {
      return System.currentTimeMillis() > expires;
    }
  }
}
