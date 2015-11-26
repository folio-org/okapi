/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.auth;

import io.vertx.ext.web.RoutingContext;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author heikki
 */

public class Auth {

  public void login(RoutingContext ctx) {
    System.out.println("Auth.login called");
  }

  public void check (RoutingContext ctx) {
    String token = ctx.request().getHeader("X-Sling-Token");
    if ( token == null || token.isEmpty() ) {
      System.out.println("Auth.check called without X-Sling-Token");
      ctx.response().setStatusCode(400).end("Auth.check called without X-Sling-Token"); // Check symbolic name for "forbidden"
      return;
    }
    System.out.println("Auth.check called with " + token);
    // Do some magic
    ctx.response().setStatusCode(200).end(); // OK
  }

  
}
