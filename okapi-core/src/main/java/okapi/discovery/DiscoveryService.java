/*
 * Copyright (C) 2016 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.discovery;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
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
          final String s = Json.encodePrettily(pmd);
          responseJson(ctx, 201)
                  .putHeader("Location", ctx.request().uri()
                          + "/" + pmd.getSrvcId()
                          + "/" + pmd.getInstId())
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
      responseError(ctx, 400, "srvcId missing");
      return;
    }
    dm.get(srvcId, res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        List<DeploymentDescriptor> result = res.result();
        if (result.isEmpty()) {
          responseError(ctx, 404, "srvcId " + srvcId + " not found");
        } else {
          final String s = Json.encodePrettily(res.result());
          responseJson(ctx, 200).end(s);
        }
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

  public void health(RoutingContext ctx) {
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
    dm.health(srvcId, instId, res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        responseJson(ctx, 200).end(s);
      }
    });
  }

  public void healthSrvcId(RoutingContext ctx) {
    final String srvcId = ctx.request().getParam(SRVC_ID);
    if (srvcId == null) {
      responseError(ctx, 400, "srvcId missing");
      return;
    }
    dm.health(srvcId, res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        responseJson(ctx, 200).end(s);
      }
    });
  }

  public void healthAll(RoutingContext ctx) {
    dm.health(res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        responseJson(ctx, 200).end(s);
      }
    });
  }

}
