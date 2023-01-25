package org.folio.okapi.common.refreshtoken.tokencache;

import org.folio.okapi.common.refreshtoken.tokencache.impl.ExpiryMapImpl;

public class TenantUserCache {

  static class TokenKey {
    private final String tenant;
    private final String user;

    TokenKey(String tenant, String user) {
      if (tenant == null) {
        throw new IllegalArgumentException("tenant must not be null");
      }
      if (user == null) {
        throw new IllegalArgumentException("user must not be null");
      }
      this.tenant = tenant;
      this.user = user;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof TokenKey) {
        TokenKey tokenKey = (TokenKey) o; // if on java17 we didn't have to do this
        return tenant.equals(tokenKey.tenant) && user.equals(tokenKey.user);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return user.hashCode() + 31 * tenant.hashCode();
    }
  }

  final ExpiryMap<TokenKey,String> map;

  public TenantUserCache(int capacity) {
    map = new ExpiryMapImpl<>(capacity);
  }

  public void put(String tenant, String user, String token, long expiresTimeMillis) {
    map.put(new TokenKey(tenant, user), token, expiresTimeMillis);
  }

  public String get(String tenant, String user) {
    return map.get(new TokenKey(tenant, user));
  }
}
