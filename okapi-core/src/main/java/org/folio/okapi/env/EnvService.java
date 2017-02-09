package org.folio.okapi.env;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.folio.okapi.bean.EnvEntry;
import static org.folio.okapi.common.HttpResponse.responseJson;
import static org.folio.okapi.common.HttpResponse.responseText;
import static org.folio.okapi.common.HttpResponse.responseError;

/**
 * Web service functions for "/_/env".
 */
public class EnvService {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final EnvManager envManager;

  public EnvService(EnvManager envManager) {
    this.envManager = envManager;
  }

  public void create(RoutingContext ctx) {
    try {
      final EnvEntry pmd = Json.decodeValue(ctx.getBodyAsString(),
              EnvEntry.class);
      envManager.add(pmd, res -> {
        if (res.failed()) {
          responseError(ctx, res.getType(), res.cause());
        } else {
          final String s = Json.encodePrettily(pmd);
          responseJson(ctx, 201)
                  .putHeader("Location", ctx.request().uri()
                          + "/" + pmd.getName()).end(s);
        }
      });
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    if (id == null) {
      responseError(ctx, 400, "id missing");
      return;
    }
    envManager.remove(id, res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        responseText(ctx, 204).end();
      }
    });
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    if (id == null) {
      responseError(ctx, 400, "id missing");
      return;
    }
    envManager.get(id, res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        responseJson(ctx, 200).end(s);
      }
    });
  }

  public void getAll(RoutingContext ctx) {
    envManager.get(res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        responseJson(ctx, 200).end(s);
      }
    });
  }
}
