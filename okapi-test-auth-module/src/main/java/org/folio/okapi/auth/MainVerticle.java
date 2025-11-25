package org.folio.okapi.auth;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import java.lang.management.ManagementFactory;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;


/**
 * The auth module provides two services: login and filter. URI "/authn/login"
 * takes username, password, and other parameters, and returns a token. URI
 * "/filter" takes the token, and verifies that everything is all right. This is
 * a very trivial dummy module, that provides simple hard-coded authentication
 * for any user who can append '-password' to his username to make a fake
 * password. This module can also be used for testing other filter phases,
 * like 'pre' and 'post'.
 */
@java.lang.SuppressWarnings({"squid:S1192"})

public class MainVerticle extends VerticleBase {

  private final Logger logger = OkapiLogger.get();

  /** main for auth module. */
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    System.setProperty("vertx.logger-delegate-factory-class-name",
        "io.vertx.core.logging.Log4jLogDelegateFactory");

    vertx.deployVerticle(new MainVerticle()).onFailure(x -> {
      Logger logger = OkapiLogger.get();
      logger.error("Failed to deploy module", x);
      System.exit(1);
    });
  }

  @Override
  public Future<?> start() {
    Router router = Router.router(vertx);
    Auth auth = new Auth();

    final int port = Integer.parseInt(
        System.getProperty("http.port", System.getProperty("port", "9020")));

    logger.info("Starting auth {} on port {}",
        ManagementFactory.getRuntimeMXBean().getName(), port);

    router.post("/authn/login").handler(BodyHandler.create());
    router.post("/authn/login").handler(auth::login);
    router.route("/authn/login").handler(auth::accept);
    router.get("/authn/listTenants").handler(auth::listTenants);
    router.post("/_/tenant").handler(auth::tenantOp);
    router.route("/*").handler(auth::filter);

    return vertx.createHttpServer()
        .requestHandler(router)
        .listen(port);
  }

}
