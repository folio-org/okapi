package org.folio.okapi.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.MultiMap;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TokenHeaderTest {

  @Test
  void cookies1() {
    String c = "\n\r \tname=value; name2=value2; name3=value3 ";
    assertThat(TokenHeader.cookieValue(c, "namea")).isNull();;
    assertThat(TokenHeader.cookieValue(c, "name")).isEqualTo("value");
    assertThat(TokenHeader.cookieValue(c, "name2")).isEqualTo("value2");
    assertThat(TokenHeader.cookieValue(c, "name3")).isEqualTo("value3");
  }

  @Test
  void cookies2() {
    String c = "name=value; name=value2; name3=value3";
    String msg = Assertions.assertThrows(IllegalArgumentException.class,
        () -> TokenHeader.cookieValue(c, "name")).getMessage();
    assertThat(msg).isEqualTo("multiple occurrences of name in cookie");
    assertThat(TokenHeader.cookieValue(c, "name3")).isEqualTo("value3");
  }

  @Test
  void cookies3() {
    String c = "name=value;name2=value2; name3=value3";
    assertThat(TokenHeader.cookieValue(c, "name")).isEqualTo("value;name2=value2");
    assertThat(TokenHeader.cookieValue(c, "name3")).isEqualTo("value3");
  }

  @Test
  void headersEmpty() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    assertThat(TokenHeader.check(headers)).isNull();
    assertThat(headers).isEmpty();
  }

  @Test
  void headersOkapiToken() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.set(XOkapiHeaders.TOKEN, "123");
    assertThat(TokenHeader.check(headers)).isEqualTo("123");
  }

  @Test
  void headersAuthorization() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.set(XOkapiHeaders.AUTHORIZATION, "Bearer 123");
    assertThat(TokenHeader.check(headers)).isEqualTo("123");
    assertThat(headers.get(XOkapiHeaders.TOKEN)).isEqualTo("123");
    assertThat(headers.contains(XOkapiHeaders.AUTHORIZATION)).isFalse();
  }

  @Test
  void headersAuthorizationAndTokenMatch() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.set(XOkapiHeaders.AUTHORIZATION, "Bearer 123");
    headers.set(XOkapiHeaders.TOKEN, "123");
    assertThat(TokenHeader.check(headers)).isEqualTo("123");
    assertThat(headers.get(XOkapiHeaders.TOKEN)).isEqualTo("123");
    assertThat(headers.contains(XOkapiHeaders.AUTHORIZATION)).isFalse();
  }

  @Test
  void headersAuthorizationAndTokenMismatch() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.set(XOkapiHeaders.AUTHORIZATION, "Bearer 123");
    headers.set(XOkapiHeaders.TOKEN, "124");
    String msg = Assertions.assertThrows(IllegalArgumentException.class,
        () -> TokenHeader.check(headers)).getMessage();
    assertThat(msg).isEqualTo("X-Okapi-Token is not equal to Authorization token");
  }

  @Test
  void headersCookie() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.set("Cookie", "other=y; accessToken=123");
    assertThat(TokenHeader.check(headers)).isEqualTo("123");
    assertThat(headers.get(XOkapiHeaders.TOKEN)).isEqualTo("123");
  }

  @Test
  void headersCookieNoAccess1() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.set("Cookie", "other=y");
    assertThat(TokenHeader.check(headers)).isNull();
  }

  @Test
  void headersCookieNoAccess2() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.set("Cookie", "other=y");
    headers.set(XOkapiHeaders.TOKEN, "123");
    assertThat(TokenHeader.check(headers)).isEqualTo("123");
    assertThat(headers.get(XOkapiHeaders.TOKEN)).isEqualTo("123");
  }

  @Test
  void headersCookieAndTokenMatch() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.set("Cookie", "accessToken=123");
    headers.set(XOkapiHeaders.TOKEN, "123");
    assertThat(TokenHeader.check(headers)).isEqualTo("123");
  }

  @Test
  void headersCookieAndTokenMismatch() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.set("Cookie", "accessToken=123");
    headers.set(XOkapiHeaders.TOKEN, "124");
    String msg = Assertions.assertThrows(IllegalArgumentException.class,
        () -> TokenHeader.check(headers)).getMessage();
    assertThat(msg).isEqualTo("X-Okapi-Token conflicts with Cookie");
  }

}
