package org.folio.okapi.common.refreshtoken.tokencache;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class TenantUserCacheTest {

  @Test
  public void lookup() {
    TenantUserCache tk = new TenantUserCache(10);

    tk.put("tenant1", "user1", "v1", System.currentTimeMillis() + 10);
    assertThat(tk.get("tenant1", "user1"), is("v1"));
    assertThat(tk.get("tenant1", "user"), is(nullValue()));
    assertThat(tk.get("tenant", "user1"), is(nullValue()));
  }

  @Test
  public void tokenKey() {
    TenantUserCache.TokenKey tokenKey1 = new TenantUserCache.TokenKey("a", "b");
    assertThat(tokenKey1.equals(tokenKey1), is(true));

    TenantUserCache.TokenKey tokenKey2 = new TenantUserCache.TokenKey("a", "b");
    assertThat(tokenKey1.equals(tokenKey2), is(true));

    TenantUserCache.TokenKey tokenKey3 = new TenantUserCache.TokenKey("a", "c");
    assertThat(tokenKey1.equals(tokenKey3), is(false));

    TenantUserCache.TokenKey tokenKey4 = new TenantUserCache.TokenKey("b", "c");
    assertThat(tokenKey1.equals(tokenKey4), is(false));

    assertThat(tokenKey1.equals("a"), is(false));
  }
}
