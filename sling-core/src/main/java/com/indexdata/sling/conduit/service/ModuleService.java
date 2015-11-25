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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class ModuleService {
  
  class ModuleInstance {
    ModuleDescriptor md;
    ProcessModuleHandle pmh;
    int port;

    public ModuleInstance(ModuleDescriptor md, ProcessModuleHandle pmh) {
      this.md = md;
      this.pmh = pmh;
    }
  }
  
  Map<String,ModuleInstance> enabled = new HashMap<>();
  Boolean[] ports;
  
  final private Vertx vertx;
  final int port_start;
  
  public ModuleService(Vertx vertx, int port_start, int port_end) {
    this.vertx = vertx;
    this.ports = new Boolean[port_end - port_start];
    this.port_start = port_start;
    for (int i = port_start; i < port_end; i++)
        ports[i - port_start] = false;
  }

  public void create(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);
      
      final String name = md.getName();
      if (enabled.containsKey(name)) {
         ctx.response().setStatusCode(400).end("module " + name + 
                 " already deployed");
         return;
      }
   
      final String uri = ctx.request().absoluteURI() + "/" + name;
      int i;
      for (i = 0; i < ports.length; i++)
        if (ports[i] == false) {
          break;      
        }
      if (i == ports.length) {
         ctx.response().setStatusCode(400).end("module " + name + 
                 " can not be deployed: all ports in use");
      }
      ports[i] = true;
      final int use_port = i + port_start;
      // enable it now so that activation for 2nd one will fail
      ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, md.getDescriptor(),
              use_port);
      enabled.put(name, new ModuleInstance(md, pmh));

      pmh.start(future -> {
        if (future.succeeded()) {
          ctx.response().setStatusCode(201).putHeader("Location", uri).end();
        } else {
          enabled.remove(md.getName());
          ports[use_port - port_start] = false;
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
        int p = pmh.getPort();
        if (p > 0) {          
           ports[p - port_start] = false;        
        }
        ctx.response().setStatusCode(204).end();
      } else {
        ctx.response().setStatusCode(500).end(future.cause().getMessage());
      }
    });
  }
}
