package org.folio.okapi.common.refreshtoken.tokencache;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Assert;
import org.junit.Test;

public class TenantUserCacheTest {

  @Test
  public void lookup() {
    TenantUserCache tk = new TenantUserCache(10);
    StringBuilder tenant = new StringBuilder("tenant1");
    StringBuilder user = new StringBuilder("user1");

    tk.put(tenant.toString(), user.toString(), "v1", System.currentTimeMillis() + 10);
    assertThat(tk.get("tenant1", "user1"), is("v1"));
    assertThat(tk.get("tenant1", "user"), is(nullValue()));
    assertThat(tk.get("tenant", "user1"), is(nullValue()));

    Assert.assertThrows(IllegalArgumentException.class, () -> tk.put("tenant", null, "v1",0));
    Assert.assertThrows(IllegalArgumentException.class, () -> tk.put(null, "user",  "v1",0));
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
