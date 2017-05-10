package org.folio.okapi.discovery;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.util.ProxyContext;

/**
 * Web service functions for "/_/discovery".
 */
public class DiscoveryService {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final DiscoveryManager dm;

  private static final String INST_ID = "instid";
  private static final String SRVC_ID = "srvcid";

  public DiscoveryService(DiscoveryManager dm) {
    this.dm = dm;
  }

  public void create(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.discovery.create");
    try {
      final DeploymentDescriptor pmd = Json.decodeValue(ctx.getBodyAsString(),
              DeploymentDescriptor.class);
      dm.addAndDeploy(pmd, res -> {
        if (res.failed()) {
          pc.responseError(res.getType(), res.cause());
        } else {
          DeploymentDescriptor md = res.result();
          final String s = Json.encodePrettily(md);
          final String uri = ctx.request().uri()
            + "/" + md.getSrvcId() + "/" + md.getInstId();
          pc.responseJson(201, s, uri);
        }
      });
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void delete(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.discovery.delete");
    final String instId = ctx.request().getParam(INST_ID);
    if (instId == null) {
      pc.responseError(400, "instId missing");
      return;
    }
    final String srvcId = ctx.request().getParam(SRVC_ID);
    if (srvcId == null) {
      pc.responseError(400, "srvcId missing");
      return;
    }
    dm.removeAndUndeploy(srvcId, instId, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        pc.responseText(204, "");
      }
    });
  }

  public void get(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.discovery.get");
    final String instId = ctx.request().getParam(INST_ID);
    if (instId == null) {
      pc.responseError(400, "instId missing");
      return;
    }
    final String srvcId = ctx.request().getParam(SRVC_ID);
    if (srvcId == null) {
      pc.responseError(400, "srvcId missing");
      return;
    }
    dm.get(srvcId, instId, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }

  public void getSrvcId(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.discovery.get.srvcid");
    final String srvcId = ctx.request().getParam(SRVC_ID);
    if (srvcId == null) {
      pc.responseError(400, "srvcId missing");
      return;
    }
    dm.get(srvcId, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        List<DeploymentDescriptor> result = res.result();
        if (result.isEmpty()) {
          pc.responseError(404, "srvcId " + srvcId + " not found");
        } else {
          final String s = Json.encodePrettily(res.result());
          pc.responseJson(200, s);
        }
      }
    });
  }

  public void getAll(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.discovery.get.all");
    dm.get(res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }

  public void health(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.discovery.health.all");
    final String instId = ctx.request().getParam(INST_ID);
    if (instId == null) {
      pc.responseError(400, "instId missing");
      return;
    }
    final String srvcId = ctx.request().getParam(SRVC_ID);
    if (srvcId == null) {
      pc.responseError(400, "srvcId missing");
      return;
    }
    dm.health(srvcId, instId, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }

  public void healthSrvcId(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.discovery.health.srvc");
    final String srvcId = ctx.request().getParam(SRVC_ID);
    if (srvcId == null) {
      pc.responseError(400, "srvcId missing");
      return;
    }
    dm.health(srvcId, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }

  public void healthAll(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.discovery.health.all");
    dm.health(res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }

  public void getNodes(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.discovery.nodes.list");
    dm.getNodes(res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }

  public void getNode(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.discovery.nodes.get");
    final String id = ctx.request().getParam("id");
    if (id == null) {
      pc.responseError(400, "id missing");
      return;
    }
    dm.getNode(id, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        pc.responseJson(200, s);
      }
    });
  }

}
