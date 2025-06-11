package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Arrays;
import java.util.Collections;
import org.folio.okapi.service.Liveness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class HealthManagerTest {

  private static final int PORT = 9230;

  @Test
  @SuppressWarnings("java:S2699")  // Suppress "Add at least one assertion to this test case"
  // as it is a false positive: https://github.com/SonarSource/sonar-java/pull/4141
  void testPort0(Vertx vertx, VertxTestContext context) {
    HealthManager m = new HealthManager(0);
    m.init(vertx, Collections.emptyList()).onComplete(context.succeedingThenComplete());
  }

  @Test
  void testPortReadinessPort(Vertx vertx, VertxTestContext context) {
    HealthManager m = new HealthManager(PORT);
    m.init(vertx, Collections.emptyList()).onComplete(context.succeeding(res -> {
      WebClient client = WebClient.create(vertx);
      client.get(PORT, "localhost", "/readiness")
          .send(context.succeeding(response -> {
            assertThat(response.statusCode()).isEqualTo(204);
            context.completeNow();
          }));
    }));
  }

  @Test
  void testPortLivenessSuccess(Vertx vertx, VertxTestContext context) {
    HealthManager m = new HealthManager(PORT);
    m.init(vertx, Arrays.asList(new IsAlive())).onComplete(context.succeeding(res -> {
      WebClient client = WebClient.create(vertx);
      client.get(PORT, "localhost", "/liveness")
          .send(context.succeeding(response -> {
            assertThat(response.statusCode()).isEqualTo(204);
            context.completeNow();
          }));
    }));
  }

  @Test
  void testPortLivenessFailure(Vertx vertx, VertxTestContext context) {
    HealthManager m = new HealthManager(PORT);
    m.init(vertx, Arrays.asList(new IsAlive(), new IsNotAlive())).onComplete(context.succeeding(res -> {
      WebClient client = WebClient.create(vertx);
      client.get(PORT, "localhost", "/liveness")
          .send(context.succeeding(response -> {
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.bodyAsString()).isEqualTo("my error");
            context.completeNow();
          }));
    }));
  }


  class IsNotAlive implements Liveness {
    @Override
    public Future<Void> isAlive() {
      return Future.failedFuture("my error");
    }
  }
  class IsAlive implements Liveness {
    @Override
    public Future<Void> isAlive() {
      return Future.succeededFuture();
    }
  }
}
