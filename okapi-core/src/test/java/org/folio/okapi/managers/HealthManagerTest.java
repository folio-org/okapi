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

  @Test
  void testPort0(Vertx vertx, VertxTestContext context) {
    HealthManager m = new HealthManager(0);
    m.init(vertx, Collections.emptyList()).onComplete(context.succeedingThenComplete());
  }

  @Test
  void testPortReadinessPort9130(Vertx vertx, VertxTestContext context) {
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
    m.init(vertx, Arrays.asList(new IsAlive())).onComplete(context.succeeding(res -> {
      WebClient client = WebClient.create(vertx);
      client.get(port, "localhost", "/liveness")
          .send(context.succeeding(response -> {
            assertThat(response.statusCode()).isEqualTo(204);
            context.completeNow();
          }));
    }));
  }

  @Test
  void testPortLivenessFailure(Vertx vertx, VertxTestContext context) {
    final int port = 9130;
    HealthManager m = new HealthManager(port);
    m.init(vertx, Arrays.asList(new IsAlive(), new IsNotAlive())).onComplete(context.succeeding(res -> {
      WebClient client = WebClient.create(vertx);
      client.get(port, "localhost", "/liveness")
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
