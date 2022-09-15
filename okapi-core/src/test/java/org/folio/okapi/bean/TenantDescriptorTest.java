package org.folio.okapi.bean;

import org.folio.okapi.util.OkapiError;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;


class TenantDescriptorTest {

  @Test
  public void valid() {
    List.of("a", "a123456789012345678901234567890", "abcd").forEach(id -> {
      TenantDescriptor t = new TenantDescriptor();
      t.setId(id);
      assertThat(t.getId(), is(id));
    });
  }

  @Test
  public void badChars() {
    List.of("søvang", "camelCase", "a_b", "a_", "a_12__2", " a", "a ",
        "a1234567890123456789012345678901").forEach(id -> {
      TenantDescriptor tenant = new TenantDescriptor();
      Throwable t = Assert.assertThrows(OkapiError.class, () -> tenant.setId(id));
      assertThat(t.getMessage(), containsString("must match pattern"));
    });
  }

  @Test
  public void reserved() {
    List.of("pg").forEach(id -> {
      TenantDescriptor tenant = new TenantDescriptor();
      Throwable t = Assert.assertThrows(OkapiError.class, () -> tenant.setId(id));
      assertThat(t.getMessage(), containsString("reserved"));
    });
  }

  @Test
  public void missing() {
    TenantDescriptor tenant = new TenantDescriptor();
    Throwable t = Assert.assertThrows(OkapiError.class, () -> tenant.setId(null));
    assertThat(t.getMessage(), containsString("Tenant id required"));
    t = Assert.assertThrows(OkapiError.class, () -> tenant.setId(""));
    assertThat(t.getMessage(), containsString("Tenant id required"));
  }

}
