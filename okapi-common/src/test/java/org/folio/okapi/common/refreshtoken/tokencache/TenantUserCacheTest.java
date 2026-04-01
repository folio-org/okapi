package org.folio.okapi.common.refreshtoken.tokencache;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

class TenantUserCacheTest {

  @Test
  void lookup() {
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

  static class ExtendedTokenKey extends TenantUserCache.TokenKey {
    public final String extension;

    public ExtendedTokenKey(String tenant, String user, String extension) {
      super(tenant, user);
      this.extension = extension;
    }
  }


  @Test
  void tokenKey() {
    var tokenKey1 = new TenantUserCache.TokenKey("a", "b");
    assertThat(tokenKey1.equals(tokenKey1), is(true));
    assertThat(tokenKey1.hashCode(), is(tokenKey1.hashCode()));

    var tokenKey2 = new TenantUserCache.TokenKey("a", "b");
    assertThat(tokenKey1.equals(tokenKey2), is(true));
    assertThat(tokenKey1.hashCode(), is(tokenKey2.hashCode()));

    var tokenKey3 = new TenantUserCache.TokenKey("a", "c");
    assertThat(tokenKey1.equals(tokenKey3), is(false));
    assertThat(tokenKey1.hashCode(), is(not(tokenKey3.hashCode())));

    var tokenKey4 = new TenantUserCache.TokenKey("b", "c");
    assertThat(tokenKey1.equals(tokenKey4), is(false));
    assertThat(tokenKey1.hashCode(), is(not(tokenKey4.hashCode())));

    var tokenKey5 = new ExtendedTokenKey("a", "b", "e");
    assertThat(tokenKey1.equals(tokenKey5), is(false));

    var tokenKey6 = new ExtendedTokenKey("a", "b", "f");
    assertThat(tokenKey5.equals(tokenKey6), is(true));
    assertThat(tokenKey5.hashCode(), is(tokenKey6.hashCode()));

    assertThat(tokenKey6.equals(null), is(false));
  }
}
