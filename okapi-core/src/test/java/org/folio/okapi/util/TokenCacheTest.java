package org.folio.okapi.util;

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
    long ttl = 500l;

    TokenCache cache = TokenCache.builder()
        .withTtl(ttl)
        .build();

    cache.put("tenant", "method", "path", "userId", "xokapiPerms", "foo", "fooTok");

    await().with()
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .atMost(ttl + 100, TimeUnit.MILLISECONDS)
        .until(() -> cache.get("tenant", "method", "path", "userId", "foo") == null);
  }

  @Test
  public void testLruCacheEqualsHashCode() {
    TokenCache.LruCache<String, String> cacheA = new TokenCache.LruCache<String, String>(10);
    TokenCache.LruCache<String, String> cacheB = new TokenCache.LruCache<String, String>(10);
    assertEquals(cacheA, cacheB);
    assertEquals(cacheA.hashCode(), cacheB.hashCode());

    // different entries
    cacheA.put("key", "value");
    assertNotEquals(cacheA, cacheB);
    assertNotEquals(cacheA.hashCode(), cacheB.hashCode());

    // different maxEntries
    TokenCache.LruCache<String, String> cacheC = new TokenCache.LruCache<String, String>(11);
    assertNotEquals(cacheB, cacheC);
    assertNotEquals(cacheB.hashCode(), cacheC.hashCode());
  }
}
