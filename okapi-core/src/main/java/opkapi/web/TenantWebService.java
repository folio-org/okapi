/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package opkapi.web;

import okapi.bean.Tenant;
import okapi.bean.TenantDescriptor;
import okapi.bean.TenantModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.Map;
import okapi.service.TenantManager;
import okapi.util.ErrorType;
import static okapi.util.ErrorType.*;

public class TenantWebService {
  
  final private Vertx vertx;
  TenantManager tenants;

  Map<String, Tenant> tenantMap = new HashMap<>();
  
  public TenantWebService(Vertx vertx, TenantManager tenantManager) {
    this.vertx = vertx;
    this.tenants = tenantManager;
  }

  public void create(RoutingContext ctx) {
    try {
      final TenantDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantDescriptor.class);
      final String id = td.getId();
      final String uri = ctx.request().uri() + "/" + id;
      
      tenants.put(id, new Tenant(td));
      ctx.response().setStatusCode(201).putHeader("Location", uri).end();
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }
  
  public void list(RoutingContext ctx) {
    String s = Json.encodePrettily(tenants.getIds());
    ctx.response().end(s);
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    Tenant tenant = tenants.get(id);
    if (tenant == null) {
      ctx.response().setStatusCode(404).end();
      return;      
    }
    String s = Json.encodePrettily(tenant.getDescriptor());
    ctx.response().end(s);
  }
  
  public Tenant get(String id) {  // TODO - should not be needed, belongs in the TenantService, which already has it
    return tenants.get(id);
  }
  
  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    if ( tenants.delete(id))
      ctx.response().setStatusCode(204).end();
    else
      ctx.response().setStatusCode(404).end();
  }
  
  public void enableModule(RoutingContext ctx) {
    try {
      final String id = ctx.request().getParam("id");
      final TenantModuleDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantModuleDescriptor.class);
      final String module = td.getModule();
      ErrorType err =  tenants.enableModule(id, module);
      switch(err) {
        case OK:
          ctx.response().setStatusCode(200).end();  // 204 - no content??
          break;
        case NOT_FOUND:
          ctx.response().setStatusCode(404).end();
          break;
        default:
          ctx.response().setStatusCode(400).end();
      }
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }

  public void listModules(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    Tenant tenant = tenants.get(id);
    if (tenant == null) {
      ctx.response().setStatusCode(404).end();
      return;
    }
    String s = Json.encodePrettily(tenant.listModules());
    ctx.response().setStatusCode(200).end(s);
  }
}
