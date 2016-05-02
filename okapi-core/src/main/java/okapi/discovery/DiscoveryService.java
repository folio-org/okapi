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
import static okapi.util.HttpResponse.responseJson;
import static okapi.util.HttpResponse.responseText;
import static okapi.util.HttpResponse.responseError;

public class DiscoveryService {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final DiscoveryManager dm;

  private static final String INST_ID = "instid";
  private static final String SRVC_ID = "srvcid";

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
                          + "/" + res.result().getSrvcId()
                          + "/" + res.result().getInstId())
                  .end(s);
        }
      });
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void delete(RoutingContext ctx) {
    final String instId = ctx.request().getParam(INST_ID);
    if (instId == null) {
      responseError(ctx, 400, "instId missing");
      return;
    }
    final String srvcId = ctx.request().getParam(SRVC_ID);
    if (srvcId == null) {
      responseError(ctx, 400, "srvcId missing");
      return;
    }
    dm.remove(srvcId, instId, res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        responseText(ctx, 204).end();
      }
    });
  }

  public void get(RoutingContext ctx) {
    final String instId = ctx.request().getParam(INST_ID);
    if (instId == null) {
      responseError(ctx, 400, "instId missing");
      return;
    }
    final String srvcId = ctx.request().getParam(SRVC_ID);
    if (srvcId == null) {
      responseError(ctx, 400, "srvcId missing");
      return;
    }
    dm.get(srvcId, instId, res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        responseJson(ctx, 200).end(s);
      }
    });
  }

  public void getSrvcId(RoutingContext ctx) {
    final String srvcId = ctx.request().getParam(SRVC_ID);
    if (srvcId == null) {
      responseError(ctx, 400, "instId missing");
      return;
    }
    dm.get(srvcId, res -> {
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
