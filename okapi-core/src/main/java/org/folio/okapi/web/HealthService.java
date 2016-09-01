package org.folio.okapi.web;

import io.vertx.ext.web.RoutingContext;
import static org.folio.okapi.common.HttpResponse.*;

public class HealthService {

  public void get(RoutingContext ctx) {
    responseJson(ctx, 200).end("[ ]");
  }
}
