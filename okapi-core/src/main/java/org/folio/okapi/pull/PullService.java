package org.folio.okapi.pull;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.folio.okapi.bean.PullDescriptor;
import org.folio.okapi.util.ProxyContext;

public class PullService {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final PullManager pm;

  public PullService(PullManager pm) {
    this.pm = pm;
  }

  public void create(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.pull.modules");
    try {
      final PullDescriptor pmd = Json.decodeValue(ctx.getBodyAsString(),
        PullDescriptor.class);
      pm.pull(pmd, res -> {
        if (res.failed()) {
          pc.responseError(res.getType(), res.cause());
        } else {
          pc.responseJson(200, Json.encodePrettily(res.result()));
        }
      });
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }
}
