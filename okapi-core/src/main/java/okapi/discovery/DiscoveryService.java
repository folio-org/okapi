/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.discovery;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import okapi.bean.DeploymentDescriptor;
import static okapi.util.HttpResponse.responseError;
import static okapi.util.HttpResponse.responseJson;
import static okapi.util.HttpResponse.responseText;

public class DiscoveryService {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final DiscoveryManager dm;

  public DiscoveryService(DiscoveryManager dm) {
    this.dm = dm;
  }

  public void create(RoutingContext ctx) {
    try {
      final DeploymentDescriptor pmd = Json.decodeValue(ctx.getBodyAsString(),
              DeploymentDescriptor.class);
      dm.add(pmd, res -> {
        if (res.failed()) {
          responseError(ctx, res.getType(), res.cause());
        } else {
          final String s = Json.encodePrettily(res.result());
          responseJson(ctx, 201)
                  .putHeader("Location", ctx.request().uri()
                          + "/" + res.result().getId() + "/" + pmd.getNodeId())
                  .end(s);
        }
      });
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    final String nodeId = ctx.request().getParam("nodeid");
    dm.remove(id, nodeId, res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        responseText(ctx, 204).end();
      }
    });
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    final String nodeId = ctx.request().getParam("nodeid");
    dm.get(id, nodeId, res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        responseJson(ctx, 200).end(s);
      }
    });
  }

  public void getId(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    dm.get(id, res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        responseJson(ctx, 200).end(s);
      }
    });
  }

  public void getAll(RoutingContext ctx) {
    dm.get(res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        responseJson(ctx, 200).end(s);
      }
    });
  }
}
