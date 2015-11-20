/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit.service;

import com.indexdata.sling.conduit.ModuleDescriptor;
import com.indexdata.sling.conduit.ProcessModuleHandle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author adam
 */
public class ModuleService {
  
  class ModuleInstance {
    ModuleDescriptor md;
    ProcessModuleHandle pmh;

    public ModuleInstance(ModuleDescriptor md, ProcessModuleHandle pmh) {
      this.md = md;
      this.pmh = pmh;
    }
    
  }
  
  Map<String,ModuleInstance> enabled = new HashMap<>();
  
  final private Vertx vertx;
  
  public ModuleService(Vertx vertx) {
    this.vertx = vertx;
  }
   
  public void create(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);
      
      if (enabled.containsKey(md.getName())) {
         ctx.response().setStatusCode(400).end("module " + md.getName() + 
                 " already deployed");
         return;
      }
      
      ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, md.getDescriptor());
      final String uri = ctx.request().absoluteURI() + "/" + md.getName();

      pmh.start(future -> {
        if (future.succeeded()) {
          enabled.put(md.getName(), new ModuleInstance(md, pmh));
          ctx.response().setStatusCode(201).putHeader("Location", uri).end();
        } else {
          ctx.response().setStatusCode(500).end(future.cause().getMessage());
        }
      });
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }
 
  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    if (!enabled.containsKey(id)) {
      ctx.response().setStatusCode(404).end();
      return;
    }
    ProcessModuleHandle pmh = enabled.get(id).pmh;
    pmh.stop(future -> {
      if (future.succeeded()) {
        enabled.remove(id);
        ctx.response().setStatusCode(204).end();
      } else {
        ctx.response().setStatusCode(500).end(future.cause().getMessage());
      }
    });
  }
}
