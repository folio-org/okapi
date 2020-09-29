package org.folio.okapi.util;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

@TestMethodOrder(OrderAnnotation.class)
class MetricsHelperTest {

  @AfterAll
  static void disableMetrics() {
    MetricsHelper.setEnabled(false);
  }

  @BeforeEach
  void enableMetrics() {
    MetricsHelper.setEnabled(true);
  }

  @Test
  @Order(1)
  void testMetricsNotEnabled() {
    MetricsHelper.setEnabled(false);
    assertNull(MetricsHelper.getTimerSample());
    assertNull(MetricsHelper.recordHttpClientResponse(null, "a", 0, "b", null));
    assertNull(MetricsHelper.recordHttpServerProcessingTime(null, "a", 0, "b", null));
    assertNull(MetricsHelper.recordHttpClientError("a", "b", "c"));
  }

  @Test
  void testMetricsEnabled() {
    assertTrue(MetricsHelper.isEnabled());
    assertNotNull(MetricsHelper.getTimerSample());
  }

  @Test
  void testConfig() {
    VertxOptions vopt = new VertxOptions();
    MetricsHelper.config(vopt, null, null, null, null);
    verifyConfig(vopt, "http://localhost:8086", "okapi", null, null);
    MetricsHelper.config(vopt, "a", "b", "c", "d");
    verifyConfig(vopt, "a", "b", "c", "d");
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

    long ttl = 2000L;

    TokenCache cache = TokenCache.builder()
        .withTtl(ttl)
        .build();

    // test case where there is no userId
    MetricsHelper.recordTokenCacheCached("tenant",  "GET",  "/foo/bar", null);
    
    Counter cachedCounter =
        MetricsHelper.recordTokenCacheCached("tenant", "GET", "/foo/bar", userId);
    assertEquals(1, cachedCounter.count());
    cache.put("tenant", "GET", "/foo/bar", userId, "perms", "keyToken", "tokenToCache");
    assertEquals(2, cachedCounter.count());
    cache.put("tenant", "GET", "/foo/bar", anotherUserId, "perms", "keyToken", "tokenToCache");
    assertEquals(2, cachedCounter.count());

    Counter missedCounter =
        MetricsHelper.recordTokenCacheMiss("tenant", "POST", "/foo/bar/123", userId);
    assertEquals(1, missedCounter.count());
    cache.get("tenant", "POST", "/foo/bar/123", userId, "keyToken");
    assertEquals(2, missedCounter.count());

    Counter hitCounter = MetricsHelper.recordTokenCacheHit("tenant", "GET", "/foo/bar", userId);
    assertEquals(1, hitCounter.count());
    cache.get("tenant", "GET", "/foo/bar", userId, "keyToken");
    assertEquals(2, hitCounter.count());

    Counter expiresCounter =
        MetricsHelper.recordTokenCacheExpired("tenant", "GET", "/foo/bar", userId);
    assertEquals(1, expiresCounter.count());

    await().with()
      .pollInterval(20, TimeUnit.MILLISECONDS)
      .atMost(ttl + 100, TimeUnit.MILLISECONDS)
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
  void testGetHost() {
    assertNotEquals(MetricsHelper.HOST_UNKNOWN, MetricsHelper.getHost());
    try (MockedStatic<InetAddress> mocked = Mockito.mockStatic(InetAddress.class)) {
      mocked.when(InetAddress::getLocalHost).thenThrow(UnknownHostException.class);
      assertEquals(MetricsHelper.HOST_UNKNOWN, MetricsHelper.getHost());
    }
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

  private void verifyConfig(VertxOptions vopt, String url, String db, String user, String pass) {
    JsonObject jo = vopt.getMetricsOptions().toJson().getJsonObject("influxDbOptions");
    assertEquals(url, jo.getString("uri"));
    assertEquals(db, jo.getString("db"));
    if (user == null) {
      assertFalse(jo.containsKey("userName"));
    } else {
      assertEquals(user, jo.getString("userName"));
    }
    if (pass == null) {
      assertFalse(jo.containsKey("password"));
    } else {
      assertEquals(pass, jo.getString("password"));
    }
  }

}
