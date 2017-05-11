package org.folio.okapi.env;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.util.ProxyContext;

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
    ProxyContext pc = new ProxyContext(ctx, "okapi.env.create");
    try {
      final EnvEntry pmd = Json.decodeValue(ctx.getBodyAsString(),
              EnvEntry.class);
      envManager.add(pmd, res -> {
        if (res.failed()) {
          pc.responseError(res.getType(), res.cause());
        } else {
          final String js = Json.encodePrettily(pmd);
          final String uri = ctx.request().uri() + "/" + pmd.getName();
          pc.responseJson(201, js, uri);
        }
      });
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void delete(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.env.delete");
    final String id = ctx.request().getParam("id");
    if (id == null) {
      pc.responseError(400, "id missing");
      return;
    }
    envManager.remove(id, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        pc.responseText(204, "");
      }
    });
  }

  public void get(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.env.get");
    final String id = ctx.request().getParam("id");
    if (id == null) {
      pc.responseError(400, "id missing");
      return;
    }
    envManager.get(id, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }

  public void getAll(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.env.list");
    envManager.get(res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }
}
