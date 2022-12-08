package org.folio.okapi.common.refreshtoken.tokencache;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Test;

public class TokenCacheTest {
  @Test
  public void testCapacity() {
    TokenCache tk = TokenCache.create(1);
    assertThat(tk.get("tenant", "user1"), is(nullValue()));
    tk.put("tenant", "user1", "v1", System.currentTimeMillis() + 10000);
    assertThat(tk.get("tenant", "user1"), is("v1"));
    tk.put("tenant", "user2", "v2", System.currentTimeMillis() + 10000);
    assertThat(tk.get("tenant", "user2"), is("v2"));
    assertThat(tk.get("tenant", "user1"), is(nullValue()));
  }

  @Test
  public void testExpiryPut() {
    TokenCache tk = TokenCache.create(1);

    // removed immediately even though capacity is not exceeded
    tk.put("tenant", "user1", "v1", System.currentTimeMillis() - 1);
    assertThat(tk.get("tenant", "user1"), is(nullValue()));

    // saved
    tk.put("tenant", "user1", "v1", System.currentTimeMillis() + 10000);
    assertThat(tk.get("tenant", "user1"), is("v1"));

    // capacity exceeded
    tk.put("tenant", "user2", "v2", System.currentTimeMillis() + 5000);
    assertThat(tk.get("tenant", "user1"), is(nullValue()));
    assertThat(tk.get("tenant", "user2"), is("v2"));
  }

  @Test
  public void testExpiryGet() {
    TokenCache tk = TokenCache.create(1);
    tk.put("tenant", "user1", "v1", System.currentTimeMillis());
    Awaitility.await().atMost(101, TimeUnit.MILLISECONDS).until(
        () -> tk.get("tenant", "user1") == null);
  }
}
