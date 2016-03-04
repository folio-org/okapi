/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.web;

import io.vertx.ext.web.RoutingContext;

public class HealthService {
    public void get(RoutingContext ctx) {
        ctx.response().setStatusCode(200).end();
    }
}
