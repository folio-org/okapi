package org.folio.okapi.bean;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoutingEntryTest {
  @Test
  void testMethods() {
    RoutingEntry t = new RoutingEntry();

    t.setPath("/a");
    assertFalse(t.match("/a", "GET"));

    String[] methods = new String[2];
    methods[0] = "HEAD";
    methods[1] = "GET";
    t.setMethods(methods);
    assertTrue(t.match("/a", "GET"));
    assertTrue(t.match("/a", "HEAD"));
    assertFalse(t.match("/a", "POST"));
    assertTrue(t.match("/a", null));

    methods[0] = "HEAD";
    methods[1] = "*";
    t.setMethods(methods);
    assertTrue(t.match("/a", "GET"));
    assertTrue(t.match("/a", "HEAD"));
    assertTrue(t.match("/a", "POST"));
  }

  @Test
  void testPath() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";

    t.setMethods(methods);
    assertTrue(t.match("/a", "GET"));
    assertFalse(t.match("/a", "POST"));

    t.setPath("/a");
    assertTrue(t.match(null, "GET"));
    assertFalse(t.match("/", "GET"));
    assertTrue(t.match("/a", "GET"));
    assertTrue(t.match("/a/", "GET"));
    assertFalse(t.match("", "GET"));
    assertFalse(t.match("/?query", "GET"));
    assertFalse(t.match("/#x", "GET"));
  }

  @Test
  void testPathPatternSimple() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);
    t.setPathPattern("/");
    assertTrue(t.match(null, "GET"));
    assertTrue(t.match("/", "GET"));
    assertFalse(t.match("/", "POST"));
    assertFalse(t.match("/a", "GET"));
    assertFalse(t.match("/a/", "GET"));
    assertFalse(t.match("", "GET"));
    assertTrue(t.match("/?query", "GET"));
    assertTrue(t.match("/#x", "GET"));
  }

  @Test
  void testPathPatternGlob() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);
    t.setPathPattern("/*");

    assertTrue(t.match("/", "GET"));
    assertFalse(t.match("/", "POST"));
    assertTrue(t.match("/a", "GET"));
    assertTrue(t.match("/a/", "GET"));
    assertFalse(t.match("", "GET"));
    assertTrue(t.match("/?query", "GET"));
    assertTrue(t.match("/#x", "GET"));

    t.setPathPattern("/*/a");
    assertFalse(t.match("/", "GET"));
    assertFalse(t.match("/", "POST"));
    assertFalse(t.match("/a", "GET"));
    assertFalse(t.match("/a/", "GET"));
    assertFalse(t.match("", "GET"));
    assertFalse(t.match("/?query", "GET"));
    assertFalse(t.match("/#x", "GET"));
    assertTrue(t.match("/b/a", "GET"));
    assertTrue(t.match("/c/b/a", "GET"));
  }

  @Test
  void testPathPatternId() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);

    t.setPathPattern("/a/{id}");
    assertFalse(t.match("/", "GET"));
    assertFalse(t.match("/", "POST"));
    assertFalse(t.match("/a", "GET"));
    assertFalse(t.match("/a/", "GET"));
    assertFalse(t.match("", "GET"));
    assertFalse(t.match("/?query", "GET"));
    assertFalse(t.match("/#x", "GET"));
    assertTrue(t.match("/a/b", "GET"));
    assertTrue(t.match("/a/0-9", "GET"));
    assertTrue(t.match("/a/0-9?a=1", "GET"));
    assertTrue(t.match("/a/0-9#a=1", "GET"));
    assertFalse(t.match("/a/b/", "GET"));
    assertFalse(t.match("/a/b/", "GET"));
    assertFalse(t.match("/a/b/c", "GET"));

    t.setPathPattern("/a/{id}/c");
    assertFalse(t.match("/", "GET"));
    assertFalse(t.match("/", "POST"));
    assertFalse(t.match("/a", "GET"));
    assertFalse(t.match("/a/", "GET"));
    assertFalse(t.match("", "GET"));
    assertFalse(t.match("/?query", "GET"));
    assertFalse(t.match("/#x", "GET"));
    assertFalse(t.match("/a/b", "GET"));
    assertFalse(t.match("/a/0-9", "GET"));
    assertFalse(t.match("/a/b/", "GET"));
    assertFalse(t.match("/a/b/", "GET"));
    assertTrue(t.match("/a/b/c", "GET"));
  }

  @Test
  void testFastMatch() {
    assertTrue(RoutingEntry.fastMatch("{id}/", "a/"));
    assertFalse(RoutingEntry.fastMatch("{/", "a/"));
    assertTrue(RoutingEntry.fastMatch("{", "a"));
    assertTrue(RoutingEntry.fastMatch("{}/", "a/"));
    assertFalse(RoutingEntry.fastMatch("{id}/", "/"));
    assertFalse(RoutingEntry.fastMatch("/{id}/", "//"));
    assertTrue(RoutingEntry.fastMatch("/{id}/", "/a/"));
    assertTrue(RoutingEntry.fastMatch("{id}/", "a/#a?"));
    assertTrue(RoutingEntry.fastMatch("{id}/", "a/?a#"));
    assertFalse(RoutingEntry.fastMatch("{id}/", "a#/"));
    assertFalse(RoutingEntry.fastMatch("{id}/", "a?/"));
    assertTrue(RoutingEntry.fastMatch("{ud}/", "a/?a"));
    assertTrue(RoutingEntry.fastMatch("{id1}/*/{id2}", "a/b/c/d"));
    assertTrue(RoutingEntry.fastMatch("{id1}/*/{id2}", "a//d"));
    assertFalse(RoutingEntry.fastMatch("{id1}/*/{id2}", "a/d"));
    assertFalse(RoutingEntry.fastMatch("{id1}/*/{id2}", "//"));
  }

  @Test
  void testInvalidPatterns() {
    RoutingEntry t = new RoutingEntry();
    assertThrows(DecodeException.class, () -> t.setPathPattern("/a{a{"));

    assertThrows(DecodeException.class, () -> t.setPathPattern("/a{"));

    assertThrows(DecodeException.class, () -> t.setPathPattern("/?a=b"));

    assertThrows(DecodeException.class, () -> t.setPathPattern("/a.b"));

    assertThrows(DecodeException.class, () -> t.setPathPattern("/a\\b"));

    assertThrows(DecodeException.class, () -> t.setPathPattern("/a{:}b"));
  }

  @Test
  void testRedirectPath() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "GET";
    t.setMethods(methods);
    t.setPathPattern("/req");
    t.setRedirectPath("/res");
    assertTrue(t.match("/req", "GET"));
    assertEquals("/res", t.getRedirectUri("/req"));
    assertEquals("/res?abc", t.getRedirectUri("/req?abc"));
    assertEquals("/res?abc#a", t.getRedirectUri("/req?abc#a"));
    assertEquals("/res#a", t.getRedirectUri("/req#a"));

    t.setPathPattern("/req/{id}");
    t.setRedirectPath("/res/1234");
    assertTrue(t.match("/req/2", "GET"));
    assertEquals("/res/1234", t.getRedirectUri("/req/2"));

    t.setPathPattern("/req/{id}/bongo");
    t.setRedirectPath("/res/1234/a/b");
    assertTrue(t.match("/req/q/bongo", "GET"));
    assertEquals("/res/1234/a/b", t.getRedirectUri("/req/2/bongo"));

    t.setPathPattern("/req/*/s");
    t.setRedirectPath("/res/1234");
    assertTrue(t.match("/req/a/s", "GET"));
    assertEquals("/res/1234", t.getRedirectUri("/req/a/s"));

  }

  @Test
  void testRewritePath() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = new String[1];
    methods[0] = "*";
    t.setMethods(methods);
    t.setPathPattern("/*");
    t.setRewritePath("/events");
    assertTrue(t.match("/", "GET"));
    assertEquals("/events", t.getRewritePath());
  }

  @Test
  void testBadMethods() {
    RoutingEntry t = new RoutingEntry();
    Exception e = assertThrows(DecodeException.class, () -> {
      String[] methods = { "GET", "GYF" };
        t.setMethods(methods);
      });
    assertEquals("GYF", e.getMessage());

    e = assertThrows(DecodeException.class, () -> {
      String[] methods = { "get" };
      t.setMethods(methods);
    });
    assertEquals("get", e.getMessage());
  }

  @Test
  void testGoodMethods() {
    RoutingEntry t = new RoutingEntry();
    String[] methods = { "GET", "PUT", "POST", "DELETE",
        "OPTIONS", "PATCH", "CONNECT", "TRACE", "*"};
    t.setMethods(methods);
    String[] methods1 = t.getMethods();
    assertEquals("GET", methods1[0]);
    assertEquals("*", methods1[8]);
  }

  @Test
  void testDefaultMethod() {
    RoutingEntry t = new RoutingEntry();
    assertEquals(HttpMethod.PUT, t.getDefaultMethod(HttpMethod.PUT));
    String[] methods0 = {};
    t.setMethods(methods0);
    assertEquals(HttpMethod.PUT, t.getDefaultMethod(HttpMethod.PUT));

    String[] methods2 = {"POST"};
    t.setMethods(methods2);
    assertEquals(HttpMethod.POST, t.getDefaultMethod(HttpMethod.PUT));
  }

  @ParameterizedTest
  @CsvSource({
    "      , /y/b, false",  // null pattern
    "/{x}/b, /y/b, true",
    "/{tenantId}/b,            /testlib/b,    false",
    "/{tenantId}/b,            /diku/b,       true",
    "/{tenantId}/b/{tenantId}, /diku/b/other, false",
    "/{tenantId}/b/{tenantId}, /diku/b/diku,  true",
    "/{tenantId}/b/{moduleId}, /diku/b/other, true",
  })
  void testMatchUriTenant(String pattern, String uri, boolean expected) {
    RoutingEntry routingEntry = new RoutingEntry();
    if (pattern != null) {
      routingEntry.setPathPattern(pattern);
    }
    assertThat(routingEntry.matchUriTenant(uri, "diku"), is(expected));
  }

  @Test
  void testPermissionsRequiredTenant() {
    RoutingEntry t = new RoutingEntry();
    assertNull(t.getPermissionsRequiredTenant());
    t.setPermissionsRequiredTenant(new String[0]);
    assertNotNull(t.getPermissionsRequiredTenant());
  }

  @Test
  void testTimerScheduleEncoding() {
    Schedule bean = new Schedule();
    Assertions.assertThat(bean.getDelayMilliSeconds()).isEqualTo(0L);
    String cron = "3 2 * * *";
    bean.setCron(cron);
    Assertions.assertThat(bean.getDelayMilliSeconds()).isGreaterThan(0L);
    String encode = Json.encode(bean);
    assertEquals(encode, "{\"cron\":\"" + cron + "\"}");
    Schedule bean2 = Json.decodeValue(encode, Schedule.class);
    assertEquals(bean2.getCron(), cron);
  }

  @Test
  void testTimerScheduleBadSpecv() {
    Assertions.assertThatThrownBy(() ->
      Json.decodeValue("{\"cron\":\"3 2 x * *\"}", Schedule.class))
    .isInstanceOf(DecodeException.class)
    .hasMessageContaining("Invalid chars: X");
  }

  @Test
  void testTimerScheduleEmpty() {
    RoutingEntry t = new RoutingEntry();
    Assertions.assertThat(t.getDelayMilliSeconds()).isEqualTo(0L);
    t.setSchedule(new Schedule());
    Assertions.assertThat(t.getDelayMilliSeconds()).isEqualTo(0L);
  }
}
