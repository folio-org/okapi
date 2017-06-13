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
    logger.info("pull create");
    ProxyContext pc = new ProxyContext(ctx, "okapi.pull.modules");
    try {
      logger.info("GETTING: " + ctx.getBodyAsString());
      final PullDescriptor pmd = Json.decodeValue(ctx.getBodyAsString(),
        PullDescriptor.class);
      pm.pull(pmd, res -> {
        if (res.failed()) {
          pc.responseError(res.getType(), res.cause());
        } else {
          pc.responseJson(200, "null");
        }
      });
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }
}
