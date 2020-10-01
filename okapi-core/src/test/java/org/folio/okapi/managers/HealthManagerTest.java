package org.folio.okapi.managers;

import io.vertx.core.Vertx;
import java.util.Collections;

import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class HealthManagerTest {

  @Test
  void testPort0(Vertx vertx, VertxTestContext context) {
    HealthManager m = new HealthManager(0);
    m.init(vertx, Collections.emptyList()).onComplete(context.succeedingThenComplete());
  }

  @Test
  void testPortPortOK(Vertx vertx, VertxTestContext context) {
    final int port = 9130;
    HealthManager m = new HealthManager(port);
    m.init(vertx, Collections.emptyList()).onComplete(context.succeedingThenComplete());
  }

  @Test
  void testPortReadinessSuccess(Vertx vertx, VertxTestContext context) {
    final int port = 9130;
    HealthManager m = new HealthManager(port);
    m.init(vertx, Collections.emptyList()).onComplete(context.succeeding(res -> {
      WebClient client = WebClient.create(vertx);
      client.get(port, "localhost", "/readiness")
          .send(context.succeeding(response -> {
            assertThat(response.statusCode()).isEqualTo(204);
            context.completeNow();
          }));
    }));
  }

  @Test
  void testPortLivenessSuccess(Vertx vertx, VertxTestContext context) {
    final int port = 9130;
    HealthManager m = new HealthManager(port);
    m.init(vertx, Collections.emptyList()).onComplete(context.succeeding(res -> {
      WebClient client = WebClient.create(vertx);
      client.get(port, "localhost", "/liveness")
          .send(context.succeeding(response -> {
            assertThat(response.statusCode()).isEqualTo(204);
            context.completeNow();
          }));
    }));
  }
}
