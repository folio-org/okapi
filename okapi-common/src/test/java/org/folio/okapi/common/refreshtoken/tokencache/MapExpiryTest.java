package org.folio.okapi.common.refreshtoken.tokencache;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.awaitility.Awaitility;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class MapExpiryTest {
  @Test
  public void testCapacity() {
    ExpiryMap<String,String> tk = ExpiryMap.create(1);
    assertThat(tk.get("user1"), is(nullValue()));
    tk.put("user1", "v1", System.currentTimeMillis() + 10000);
    assertThat(tk.get("user1"), is("v1"));
    tk.put("user2", "v2", System.currentTimeMillis() + 10000);
    assertThat(tk.get("user2"), is("v2"));
    assertThat(tk.get("user1"), is(nullValue()));
  }

  @Test
  public void testExpiryPut() {
    ExpiryMap<String,String> tk = ExpiryMap.create(1);

    // removed immediately even though capacity is not exceeded
    tk.put("user1", "v1", System.currentTimeMillis() - 1);
    assertThat(tk.get("user1"), is(nullValue()));

    // saved
    tk.put("user1", "v1", System.currentTimeMillis() + 10000);
    assertThat(tk.get("user1"), is("v1"));

    // capacity exceeded
    tk.put("user2", "v2", System.currentTimeMillis() + 5000);
    assertThat(tk.get("user1"), is(nullValue()));
    assertThat(tk.get("user2"), is("v2"));
  }

  @Test
  public void testCapacityWithExpiration() {
    ExpiryMap tk = ExpiryMap.create(2);
    tk.put("user1", "v1", System.currentTimeMillis() + 5);
    tk.put("user2", "v2", System.currentTimeMillis() + 5);
    Awaitility.await().pollInterval(1, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.MILLISECONDS)
        .until(() -> tk.get("user2") == null);
    tk.put("user1", "w1", System.currentTimeMillis() + 100);
    tk.put("user3", "w3", System.currentTimeMillis() + 100);
    assertThat(tk.get("user1"), is("w1"));
    assertThat(tk.get("user2"), is(nullValue()));
    assertThat(tk.get("user3"), is("w3"));
  }
}
