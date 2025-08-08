package org.folio.okapi.util;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class FuturisedHttpClientTest implements WithAssertions {
  @Test
  void requestFailsWithNullPointerException(Vertx vertx, VertxTestContext vtc) {
    FuturisedHttpClient.getSystemClient(vertx, new JsonObject()).request(null).onComplete(vtc.failing(t -> {
      assertThat(t).isInstanceOf(NullPointerException.class);
      vtc.completeNow();
    }));
  }
}
