package org.folio.okapi.deployment;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.util.ProxyContext;

/**
 * Web service to manage deployments.
 */
public class DeploymentWebService {
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final DeploymentManager md;
  private final static String INST_ID = "instid";

  public DeploymentWebService(DeploymentManager md) {
    this.md = md;
  }

  public void create(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.deployment.create");
    try {
      final DeploymentDescriptor pmd = Json.decodeValue(ctx.getBodyAsString(),
              DeploymentDescriptor.class);
      md.deploy(pmd, res -> {
        if (res.failed()) {
          pc.responseError(res.getType(), res.cause());
        } else {
          final String s = Json.encodePrettily(res.result());
          final String url = ctx.request().uri() + "/" + res.result().getInstId();
          pc.responseJson(201, s, url);
        }
      });
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void delete(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.deployment.create");
    final String id = ctx.request().getParam(INST_ID);
    md.undeploy(id, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        pc.responseText(204, "");
      }
    });
  }

  public void list(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.deployment.create");
    md.list(res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }

  public void get(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.deployment.create");
    final String id = ctx.request().getParam(INST_ID);
    md.get(id, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }

}
