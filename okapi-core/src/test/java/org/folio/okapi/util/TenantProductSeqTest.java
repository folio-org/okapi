package org.folio.okapi.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TenantProductSeqTest {

  static Stream<Arguments> tenantProductSeq() {
    return Stream.of(
        Arguments.of(null, "mod-foo", 9, "mod-foo_9"),
        Arguments.of(null, "mod-foo-bar", 999, "mod-foo-bar_999"),
        Arguments.of("tenant", "mod-foo", 0, "tenant_mod-foo_0"),
        Arguments.of("test_tenant", "mod-foo-bar", 123, "test_tenant_mod-foo-bar_123")
        );
  }

  @ParameterizedTest
  @MethodSource
  void tenantProductSeq(String tenantId, String product, int seq, String tenantProductSeq) {
    var actual = new TenantProductSeq(tenantId, product, seq).toString();
    assertThat(actual, is(tenantProductSeq));

    var actual2 = new TenantProductSeq(tenantProductSeq);
    assertThat(actual2.getTenantId(), is(tenantId));
    assertThat(actual2.getProduct(), is(product));
    assertThat(actual2.getSeq(), is(seq));

    var actual3 = new TenantProductSeq("diku", tenantProductSeq);
    assertThat(actual3.getTenantId(), is("diku"));
    assertThat(actual3.getProduct(), is(product));
    assertThat(actual3.getSeq(), is(seq));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      "_",
      "-",
      "mod-foo",
      "mod-foo_",
      "tenant_mod-foo",
      "tenant_mod-foo_",
  })
  void tenantProductSeqFail(String tenantProductSeq) {
    assertThrows(NumberFormatException.class, () -> new TenantProductSeq(tenantProductSeq));
  }
}
