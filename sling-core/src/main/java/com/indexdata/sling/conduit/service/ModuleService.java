/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit.service;

import com.indexdata.sling.conduit.ModuleDescriptor;
import com.indexdata.sling.conduit.ModuleInstance;
import com.indexdata.sling.conduit.Modules;
import com.indexdata.sling.conduit.Ports;
import com.indexdata.sling.conduit.ProcessModuleHandle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class ModuleService {
  Modules modules;
  Ports ports;

  final private Vertx vertx;

  public ModuleService(Vertx vertx, int port_start, int port_end) {
    this.vertx = vertx;
    this.ports = new Ports(port_start, port_end);
    this.modules = new Modules();
  }

  public void create(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);

      final String name = md.getName();
      ModuleInstance m = modules.get(name);
      if (m != null) {
        ctx.response().setStatusCode(400).end("module " + name
                + " already deployed");
        return;
      }

      final String uri = ctx.request().uri() + "/" + name;
      final int use_port = ports.get();
      if (use_port == -1) {
        ctx.response().setStatusCode(400).end("module " + name
                + " can not be deployed: all ports in use");
      }
      // enable it now so that activation for 2nd one will fail
      ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, md.getDescriptor(),
              use_port);
      modules.put(name, new ModuleInstance(md, pmh, use_port));

      pmh.start(future -> {
        if (future.succeeded()) {
          ctx.response().setStatusCode(201).putHeader("Location", uri).end();
        } else {
          modules.remove(md.getName());
          ports.free(use_port);
          ctx.response().setStatusCode(500).end(future.cause().getMessage());
        }
      });
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }
  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    ModuleInstance m = modules.get(id);
    if (m == null) {
      ctx.response().setStatusCode(404).end();
      return;
    }
    String s = Json.encodePrettily(modules.get(id).getModuleDescriptor());
    ctx.response().end(s);
  }

  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    ModuleInstance m = modules.get(id);
    if (id == null) {
      ctx.response().setStatusCode(404).end();
      return;
    }
    ProcessModuleHandle pmh = m.getProcessModuleHandle();
    pmh.stop(future -> {
      if (future.succeeded()) {
        modules.remove(id);
        ports.free(pmh.getPort());
        ctx.response().setStatusCode(204).end();
      } else {
        ctx.response().setStatusCode(500).end(future.cause().getMessage());
      }
    });
  }
  
  /** 
   * Add the trace headers to the response 
   */
  private void addTraceHeaders(RoutingContext ctx, List<String> traceHeaders) {
    for ( String th : traceHeaders ) {
      ctx.response().headers().add("X-Sling-Trace", th);
    }
  }
  
  public void proxy(RoutingContext ctx) {
    Iterator<ModuleInstance> it = modules.getModulesForRequest(ctx.request());
    List<String> traceHeaders = new ArrayList<>();
    ReadStream<Buffer> content = ctx.request();
    // content.pause();
    proxyR(ctx, it, traceHeaders, content);
  }
  
  private void proxyR(RoutingContext ctx,
          Iterator<ModuleInstance> it, List<String> traceHeaders,
          ReadStream<Buffer> content) {
    if (!it.hasNext()) {
      addTraceHeaders(ctx, traceHeaders);
      ctx.response().setStatusCode(404).end();
    } else {
      ModuleInstance mi = it.next();
      final long startTime = System.nanoTime();
      HttpClient c = vertx.createHttpClient();

      HttpClientRequest c_req = c.request(ctx.request().method(), mi.getPort(),
              "localhost", ctx.request().uri(), res -> {
                if (res.statusCode() >= 200 && res.statusCode() < 300
                && it.hasNext()) {
                  long timeDiff = (System.nanoTime() - startTime) / 1000;
                  traceHeaders.add(ctx.request().method() + " "
                          + mi.getModuleDescriptor().getName() + ":"
                          + res.statusCode() + " " + timeDiff + "us");
                  addTraceHeaders(ctx, traceHeaders);
                  ReadStream<Buffer> response = res;
                  proxyR(ctx, it, traceHeaders, response);
                } else {
                  ctx.response().setChunked(true);
                  ctx.response().setStatusCode(res.statusCode());
                  ctx.response().headers().setAll(res.headers());
                  long timeDiff = (System.nanoTime() - startTime) / 1000;
                  traceHeaders.add(ctx.request().method() + " "
                          + mi.getModuleDescriptor().getName() + ":"
                          + res.statusCode() + " " + timeDiff + "us");
                  addTraceHeaders(ctx, traceHeaders);

                  res.handler(data -> {
                    ctx.response().write(data);
                  });
                  res.endHandler(v -> {
                    ctx.response().end();
                  });
                }
              });
      c_req.setChunked(true);
      c_req.headers().setAll(ctx.request().headers());
      content.handler(data -> {
        c_req.write(data);
      });
      content.endHandler(v -> c_req.end());
    }
  }
} // class
