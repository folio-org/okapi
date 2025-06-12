package org.folio.okapi.util;

import org.folio.okapi.util.TokenCache.CacheEntry;
import org.folio.okapi.util.TokenCache.LruCache;
import org.junit.Test;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;
import java.util.concurrent.TimeUnit;

public class TokenCacheTest {
  
  @Test
  public void testMaxSize() {
    TokenCache cache = TokenCache.builder()
        .withMaxSize(2)
        .build();
    
    cache.put("tenant", "method", "path", "userId", "xokapiPerms", "foo", "fooTok");
    assertEquals("fooTok", cache.get("tenant", "method", "path", "userId", "foo").token);
    assertEquals(1, cache.size());
    
    cache.put("tenant", "method", "path", "userId", "xokapiPerms", "bar", "barTok");
    assertEquals("barTok", cache.get("tenant", "method", "path", "userId", "bar").token);
    assertEquals("fooTok", cache.get("tenant", "method", "path", "userId", "foo").token);
    assertEquals(2, cache.size());
    
    cache.put("tenant", "method", "path", "userId", "xokapiPerms", "baz", "bazTok");
    assertEquals("bazTok", cache.get("tenant", "method", "path", "userId", "baz").token);
    assertEquals(2, cache.size());
    
    assertEquals("fooTok", cache.get("tenant", "method", "path", "userId", "foo").token);
    assertEquals(null, cache.get("tenant", "method", "path", "userId", "bar")); //evicted.
    assertEquals("bazTok", cache.get("tenant", "method", "path", "userId", "baz").token);
  }
  
  @Test
  public void testTtl() {
    long ttl = 50L;

    TokenCache cache = TokenCache.builder()
        .withTtl(ttl)
        .build();

    cache.put("tenant", "method", "path", "userId", "xokapiPerms", "foo", "fooTok");

    await().with()
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .atMost(ttl + 20, TimeUnit.MILLISECONDS)
        .until(() -> cache.get("tenant", "method", "path", "userId", "foo") == null);
  }

  @Test
  public void testLruCacheEqualsHashCode() {
    LruCache cacheA = new LruCache(10);
    LruCache cacheB = new LruCache(10);
    assertEquals(cacheA, cacheB);
    assertEquals(cacheA.hashCode(), cacheB.hashCode());

    // different entries
    cacheA.put("key", new CacheEntry("token", "userId", "permissions", (System.currentTimeMillis() + 1000)));
    assertNotEquals(cacheA, cacheB);
    assertNotEquals(cacheA.hashCode(), cacheB.hashCode());

    // different maxEntries
    LruCache cacheC = new LruCache(11);
    assertNotEquals(cacheB, cacheC);
    assertNotEquals(cacheB.hashCode(), cacheC.hashCode());
  }

  @Test
  public void testPruneOnPut() {
    long ttl = 50L;
    int cap = 10;

    TokenCache cache = TokenCache.builder()
        .withMaxSize(cap)
        .withTtl(ttl)
        .build();

    // fill to capacity.
    for (int i = 0; i < cap; i++) {
      cache.put("tenant", "method", "path", "userId", "xokapiPerms", "foo" + i, "fooTok" + i);
    }
    assertEquals(cap, cache.size());

    long start = System.currentTimeMillis();

    // ensure there are expired entries.
    await().with()
      .pollInterval(10, TimeUnit.MILLISECONDS)
      .until(() -> System.currentTimeMillis() > (start + ttl));

    // cache one more entry, which should result in pruning two expired entries
    cache.put("tenant", "method", "path", "userId", "xokapiPerms", "bar", "barTok");
    assertEquals(cap - 2, cache.size());
  }

  @Test
  public void testDifferentTenantsSameToken() {
    TokenCache cache = TokenCache.builder()
        .withMaxSize(2)
        .build();

    // create a cache item for tenant A
    cache.put("tenantA", "method", "path", "userId", "xokapiPerms", "foo", "fooTok");
    assertEquals("fooTok", cache.get("tenantA", "method", "path", "userId", "foo").token);
    assertEquals(1, cache.size());

    // cache should not return an item for tenant B
    assertNull(cache.get("tenantB", "method", "path", "userId", "foo"));

    // create a cache item for tenant B
    cache.put("tenantB", "method", "path", "userId", "xokapiPerms", "foo", "fooTok");
    assertEquals("fooTok", cache.get("tenantB", "method", "path", "userId", "foo").token);
    assertEquals(2, cache.size());
  }
}
