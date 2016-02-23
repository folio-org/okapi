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
import okapi.service.TenantManager;
import okapi.service.TenantStore;
import okapi.util.ErrorType;
import static okapi.util.ErrorType.*;

public class TenantWebService {
  
  final private Vertx vertx;
  TenantManager tenants;
  TenantStore tenantStore;
  
  public TenantWebService(Vertx vertx, TenantManager tenantManager, TenantStore tenantStore) {
    this.vertx = vertx;
    this.tenants = tenantManager;
    this.tenantStore = tenantStore;
  }

  public void init(RoutingContext ctx) {
    tenantStore.init(res->{
      if (res.succeeded()) {
        ctx.response().setStatusCode(204).end();
        /*
        this.sendReloadSignal(res2->{
          if ( res.succeeded()){
            ctx.response().setStatusCode(204).end();
          }else
            ctx.response().setStatusCode(500).end(res2.cause().getMessage());
        });
                */
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }


  public void create(RoutingContext ctx) {
    try {
      final TenantDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
        TenantDescriptor.class);
      Tenant t = new Tenant(td);
      final String id = td.getId();
      if (tenants.insert(t)) {
        tenantStore.insert(t, res -> {
          if (res.succeeded()) {
            final String uri = ctx.request().uri() + "/" + id;
            ctx.response().setStatusCode(201).putHeader("Location", uri).end();
          } else { // TODO - Check what errors the mongo store can return
            ctx.response().setStatusCode(400).end(res.cause().getMessage());
          }
        });
      } else {
        ctx.response().setStatusCode(400).end("Duplicate id " + id);
      }
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }

  public void list(RoutingContext ctx) {
    tenantStore.listIds(res->{
      if (res.succeeded()) {
        String s = Json.encodePrettily(res.result());
        ctx.response().end(s);
      } else {
        ctx.response().setStatusCode(400).end(res.cause().getMessage());
      }
    });
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    tenantStore.get(id, res -> {
      if ( res.succeeded() ) {
        Tenant t = res.result();
        String s = Json.encodePrettily(t.getDescriptor());
        ctx.response().end(s);
      } else {
        if ( res.getType() == NOT_FOUND )
          ctx.response().setStatusCode(404).end(res.cause().getMessage());
        else
          ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }
  
  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    if ( tenants.delete(id)) {
      tenantStore.delete(id, res->{
        if ( res.succeeded()) {
          ctx.response().setStatusCode(204).end();
        } else {
          ctx.response().setStatusCode(500).end(res.cause().getMessage());
        }
      });
    } else {
      ctx.response().setStatusCode(404).end();
    }
  }
  
  public void enableModule(RoutingContext ctx) {
    try {
      final String id = ctx.request().getParam("id");
      final TenantModuleDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantModuleDescriptor.class);
      final String module = td.getModule();
      // TODO - Validate we know about that module!
      ErrorType err =  tenants.enableModule(id, module);
      if ( err == OK ) {
        tenantStore.enableModule(id, module, res->{
          if ( res.succeeded() ) {
            ctx.response().setStatusCode(200).end();  // 204 - no content??
          } else {
            if (res.getType() == NOT_FOUND) {
              ctx.response().setStatusCode(404).end(res.cause().getMessage());
            } else {
              ctx.response().setStatusCode(500).end(res.cause().getMessage());
            }
          }
        });

      } else if ( err == NOT_FOUND ) {
          ctx.response().setStatusCode(404).end("Tenant " + id + " not found (enableModule)");
      } else {
          ctx.response().setStatusCode(500).end();
      }
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }

  public void listModules(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    tenantStore.get(id, res->{
      if ( res.succeeded()) {
        Tenant t = res.result();
        String s = Json.encodePrettily(t.listModules());
        ctx.response().setStatusCode(200).end(s);
      } else {
        if ( res.getType() == NOT_FOUND) {
          ctx.response().setStatusCode(404).end(res.cause().getMessage());
        } else {
          ctx.response().setStatusCode(500).end(res.cause().getMessage());
        }
      }
    });
  }

} // class
