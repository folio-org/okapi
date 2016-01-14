/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.okapi.conduit.service;

import com.indexdata.okapi.conduit.Tenant;
import com.indexdata.okapi.conduit.TenantDescriptor;
import com.indexdata.okapi.conduit.TenantModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.Map;

public class TenantService {
  
  final private Vertx vertx;
  
  Map<String, Tenant> enabled = new HashMap<>();
  
  public TenantService(Vertx vertx) {
    this.vertx = vertx;
  }

  public void create(RoutingContext ctx) {
    try {
      final TenantDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantDescriptor.class);
      final String name = td.getName();
      final String uri = ctx.request().uri() + "/" + name;
      
      enabled.put(name, new Tenant(td));
      ctx.response().setStatusCode(201).putHeader("Location", uri).end();
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }
  
  public void list(RoutingContext ctx) {
    String s = Json.encodePrettily( enabled.keySet());
    ctx.response().end(s);
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    Tenant tenant = enabled.get(id);
    if (tenant == null) {
      ctx.response().setStatusCode(404).end();
      return;      
    }
    String s = Json.encodePrettily(tenant.getDescriptor());
    ctx.response().end(s);
  }
  
  public Tenant get(String id) {
    return enabled.get(id);
  }
  
  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    if (!enabled.containsKey(id)) {
      ctx.response().setStatusCode(404).end();
      return;
    }
    enabled.remove(id);
    ctx.response().setStatusCode(204).end();
  }
  
  public void enableModule(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    Tenant tenant = enabled.get(id);
    if (tenant == null) {
      ctx.response().setStatusCode(404).end();
      return;
    }
    try {
      final TenantModuleDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantModuleDescriptor.class);
      
      final String module = td.getModule();
      tenant.enableModule(module);
      ctx.response().setStatusCode(200).end();
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }
}
