package org.folio.okapi.util;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.common.MetricsUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;

class MetricsHelperTest {

  @BeforeAll
  static void setupAll() {
    System.getProperties().setProperty(MetricsUtil.ENABLE_METRICS, "true");
    System.getProperties().setProperty(MetricsUtil.SIMPLE_OPTS, "{}");
  }

  @AfterAll
  static void teardownAll() {
    System.getProperties().remove(MetricsUtil.ENABLE_METRICS);
    System.getProperties().remove(MetricsUtil.SIMPLE_OPTS);
  }

  @BeforeEach
  void setup() {
    MetricsUtil.init(new VertxOptions());
  }

  @AfterEach
  void teardown() {
    MetricsUtil.stop();
  }

  @Test
  void testNotEnabled() {
    MetricsUtil.stop();
    assertNull(MetricsHelper.getTimerSample());
    assertNull(MetricsHelper.recordHttpClientResponse(null, "a", 0, "b", null));
    assertNull(MetricsHelper.recordHttpServerProcessingTime(null, "a", 0, "b", null));
    assertNull(MetricsHelper.recordHttpClientError("a", "b", "c"));
    assertNull(MetricsHelper.recordCodeExecutionTime(null, "a"));
    assertNull(MetricsHelper.recordTokenCacheCached("a", "b", "c", "d"));
    assertNull(MetricsHelper.recordTokenCacheExpired("a", "b", "c", "d"));
    assertNull(MetricsHelper.recordTokenCacheHit("a", "b", "c", "d"));
    assertNull(MetricsHelper.recordTokenCacheMiss("a", "b", "c", "d"));
  }

  @Test
  void testRecordHttpServerProcessingTime() {
    Timer.Sample sample = MetricsHelper.getTimerSample();
    // null ModuleInstance should be OK
    Timer timer = MetricsHelper.recordHttpServerProcessingTime(sample, "a", 200, "GET", null);
    assertEquals(1, timer.count());

    ModuleInstance mi = createModuleInstance(true);
    timer = MetricsHelper.recordHttpServerProcessingTime(sample, "a", 200, "GET", mi);
    assertEquals(1, timer.count());

    // null tenant and httpMethod
    timer = MetricsHelper.recordHttpServerProcessingTime(sample, null, 200, null, mi);
    assertEquals(1, timer.count());
  }

  @Test
  void testRecordHttpClientResponseTime() {
    Timer.Sample sample = MetricsHelper.getTimerSample();
    // null ModuleInstance should be OK
    Timer timer = MetricsHelper.recordHttpClientResponse(sample, "a", 200, "GET", null);
    assertEquals(1, timer.count());
    ModuleInstance mi = createModuleInstance(true);
    timer = MetricsHelper.recordHttpClientResponse(sample, "a", 200, "GET", mi);
    assertEquals(1, timer.count());

    // change module instance routing entry type
    mi = createModuleInstance(false);
    timer = MetricsHelper.recordHttpClientResponse(sample, "a", 200, "GET", mi);
    assertEquals(1, timer.count());

    // legacy case where module instance has no routing entry
    mi = createModuleInstanceWithoutRoutingEntry(true);
    timer = MetricsHelper.recordHttpClientResponse(sample, "a", 200, "GET", mi);
    assertEquals(1, timer.count());
    mi = createModuleInstanceWithoutRoutingEntry(false);
    timer = MetricsHelper.recordHttpClientResponse(sample, "a", 200, "GET", mi);
    assertEquals(1, timer.count());
  }

  @Test
  void testRecordTokenCacheEvent() {
    String userId = "03975dd7-8004-48cf-bd21-4d7ff2e74ca2";
    String anotherUserId = "54412e3d-a024-4914-8d54-8b84e66513a6";

    long ttl = 200L;

    TokenCache cache = TokenCache.builder()
        .withTtl(ttl)
        .build();

    // test case where there is no userId
    MetricsHelper.recordTokenCacheCached("tenant", "GET", "/foo/bar", null);

    Counter cachedCounter = MetricsHelper.recordTokenCacheCached("tenant", "GET", "/foo/bar", userId);
    assertEquals(1, cachedCounter.count());
    cache.put("tenant", "GET", "/foo/bar", userId, "perms", "keyToken", "tokenToCache");
    assertEquals(2, cachedCounter.count());
    cache.put("tenant", "GET", "/foo/bar", anotherUserId, "perms", "keyToken", "tokenToCache");
    assertEquals(2, cachedCounter.count());

    Counter missedCounter = MetricsHelper.recordTokenCacheMiss("tenant", "POST", "/foo/bar/123", userId);
    assertEquals(1, missedCounter.count());
    cache.get("tenant", "POST", "/foo/bar/123", userId, "keyToken");
    assertEquals(2, missedCounter.count());

    Counter hitCounter = MetricsHelper.recordTokenCacheHit("tenant", "GET", "/foo/bar", userId);
    assertEquals(1, hitCounter.count());
    cache.get("tenant", "GET", "/foo/bar", userId, "keyToken");
    assertEquals(2, hitCounter.count());

    Counter expiresCounter = MetricsHelper.recordTokenCacheExpired("tenant", "GET", "/foo/bar", userId);
    assertEquals(1, expiresCounter.count());

    await().with()
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .atMost(ttl + 20, TimeUnit.MILLISECONDS)
        .until(() -> cache.get("tenant", "GET", "/foo/bar", userId, "keyToken") == null);

    assertEquals(2, expiresCounter.count());
  }

  @Test
  void testRecordHttpClientError() {
    Counter counter = MetricsHelper.recordHttpClientError("a", "GET", "/a");
    assertEquals(1, counter.count());
    // increment by one
    MetricsHelper.recordHttpClientError("a", "GET", "/a");
    assertEquals(2, counter.count());
  }

  @Test
  void testRecordCodeExecutionTime() {
    Timer.Sample sample = MetricsHelper.getTimerSample();
    Timer timer = MetricsHelper.recordCodeExecutionTime(sample, "a");
    assertEquals(1, timer.count());
  }

  private ModuleInstance createModuleInstance(boolean handler) {
    ModuleDescriptor md = new ModuleDescriptor();
    md.setId("abc-1.0");
    RoutingEntry re = new RoutingEntry();
    re.setPath("/a");
    re.setPhase("auth");
    return new ModuleInstance(md, re, "/", HttpMethod.GET, handler);
  }

  private ModuleInstance createModuleInstanceWithoutRoutingEntry(boolean handler) {
    ModuleDescriptor md = new ModuleDescriptor();
    md.setId("abc-1.0");
    return new ModuleInstance(md, null, "/", HttpMethod.GET, handler);
  }

}
