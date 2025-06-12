package org.folio.okapi.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TenantValidatorTest {

  @Test
  void utilityClass() {
    UtilityClassTester.assertUtilityClass(TenantValidator.class);
  }

  @ParameterizedTest
  @CsvSource({
    "a, ",
    "a1, ",
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, ",
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, Tenant id must not exceed 31 characters: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "1, Tenant id must not start with a digit: 1",
    "foo_bar, Tenant id must not contain underscore: foo_bar",
    "a-z, Tenant id must not contain minus: a-z",
    "universität, 'Invalid tenant id, may only contain a-z and 0-9 and must match [a-z][a-z0-9]{0,30} but it is universität'",
  })
  void validate(String tenantId, String errorMessage) {
    var result = TenantValidator.validate(tenantId);
    if (errorMessage == null) {
      assertThat(result.succeeded()).isTrue();
    } else {
      assertThat(result.cause().getMessage()).isEqualTo(errorMessage);
    }
  }
}
