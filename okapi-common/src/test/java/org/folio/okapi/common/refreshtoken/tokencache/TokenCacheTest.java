package org.folio.okapi.common.refreshtoken.tokencache;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class TokenCacheTest {

  @Test
  public void test1() {
    TenantUserCache tk = new TenantUserCache(10);

    tk.put("tenant1", "user1", "v1", System.currentTimeMillis() + 10);
    assertThat(tk.get("tenant1", "user1"), is("v1"));
    assertThat(tk.get("tenant1", "user"), is(nullValue()));
    assertThat(tk.get("tenant", "user1"), is(nullValue()));
  }
}
