package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.folio.okapi.service.Liveness;

public class HealthManager {
  private Vertx vertx;
  private final int listenPort;
  private List<Liveness> livenessChecks;

  public HealthManager(int listenPort) {
    this.listenPort = listenPort;
  }

  /**
   * Initialize health manager.
   * This should be called when the service should start
   * serving readiness and liveness.
   * @param vertx Vert.x handle
   * @return future result
   *
   */
  public Future<Void> init(Vertx vertx, List<Liveness> livenessChecks) {
    this.vertx = vertx;
    this.livenessChecks = livenessChecks;
    if (listenPort == 0) {
      return Future.succeededFuture();
    }
    Router router = Router.router(vertx);

    router.route(HttpMethod.GET, "/readiness").handler(this::readinessHandler);
    router.route(HttpMethod.GET, "/liveness").handler(this::livenessHandler);
    HttpServerOptions serverOptions = new HttpServerOptions()
        .setHandle100ContinueAutomatically(true);
    return vertx.createHttpServer(serverOptions)
        .requestHandler(router)
        .listen(listenPort)
        .mapEmpty();
  }

  private void readinessHandler(RoutingContext ctx) {
    ctx.response().setStatusCode(204);
    ctx.response().end();
  }

  private void livenessHandler(RoutingContext ctx) {
    Future<Void> future = Future.succeededFuture();
    for (Liveness l : livenessChecks) {
      future = future.compose(x -> l.isAlive());
    }
    future.onComplete(res -> {
      if (res.failed()) {
        ctx.response().putHeader("Content-Type", "text/plain");
        ctx.response().setStatusCode(500);
        ctx.response().end(res.cause().getMessage());
        return;
      }
      ctx.response().setStatusCode(204);
      ctx.response().end();
    });
  }
}
