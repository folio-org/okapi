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
import com.indexdata.sling.conduit.RoutingEntry;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Iterator;
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
  
  private boolean match(RoutingEntry[] routingEntries, HttpServerRequest req,
          boolean head) {
    for (int i = 0; i < routingEntries.length; i++) {
      RoutingEntry e = routingEntries[i];
      if (req.uri().startsWith(e.getPath())) {
        String[] methods = e.getMethods();
        for (int j = 0; j < methods.length; j++) {
          if (head) {
            return methods[j].equals("CHECK");
          } else {
            if (methods[j].equals("*") || methods[j].equals(req.method().name()))
              return true;
          }
        }
      }
    }
    return false;
  }

  /** 
   * Add the trace headers to the response 
   */
  private void addTraceHeaders(RoutingContext ctx, List<String> traceHeaders) {
    for ( String th : traceHeaders ) {
      System.out.println("X-Sling-Trace: " + th);
      ctx.response().headers().add("X-Sling-Trace", th);
    }
  }
  
  public void proxy(RoutingContext ctx) {
    ctx.request().pause();
    List<String> traceHeaders = new ArrayList<>();
    Iterator<String> it = modules.iterator();
    proxyHead(ctx, it, traceHeaders);
  }

  // TODO - This can be simplified, no need to recurse, just iterate until
  // the first found.
  public void proxyRequest(RoutingContext ctx, 
          Iterator<String> it, List<String> traceHeaders ) {
    if (!it.hasNext()) {
      addTraceHeaders(ctx, traceHeaders);
      ctx.response().setStatusCode(404).end();
    } else {
      String m = it.next();
      ModuleInstance mi = modules.get(m);
      if (!match(mi.getModuleDescriptor().getRoutingEntries(), ctx.request(), false)) {
        proxyRequest(ctx, it,traceHeaders);
      } else {
        final long startTime = System.nanoTime();
        HttpServerRequest req = ctx.request();
        HttpClient c = vertx.createHttpClient();
        System.out.println("Make request to " + mi.getModuleDescriptor().getName());
        HttpClientRequest c_req = c.request(ctx.request().method(), mi.getPort(),
                "localhost", req.uri(), res -> {
                  System.out.println("Got response " + res.statusCode() + " from " + mi.getModuleDescriptor().getName());
                  ctx.response().setChunked(true);
                  ctx.response().setStatusCode(res.statusCode());
                  ctx.response().headers().setAll(res.headers());
                  long timeDiff = ( System.nanoTime() - startTime) / 1000 ;
                  // Actually, this is a bit too early to stop measuring the time, but we
                  // need to do all headers before we do the data, or they get lost
                  // along the way.
                  traceHeaders.add( ctx.request().method() + " " + 
                          mi.getModuleDescriptor().getName() + ":" + 
                          res.statusCode() + " " + timeDiff + "us" );
                  addTraceHeaders(ctx, traceHeaders);
                  System.out.println(" .. Final headers: " + Json.encode(ctx.response().headers().entries()));
                  res.handler(data -> {
                    ctx.response().write(data);
                  });
                  res.endHandler(v -> {
                    ctx.response().end();
                  });
                });
        System.out.println("Make request phase two to " + mi.getModuleDescriptor().getName());
        c_req.setChunked(true);
        c_req.headers().setAll(req.headers());
        System.out.println(" ... Setting data handler ");
        req.handler(data -> {
          System.out.println(" ... request data handler: " + data);
          c_req.write(data);
        });
        req.endHandler(v -> c_req.end());
        System.out.println(" ... Resuming reading");
        req.resume();
        System.out.println(" ... done");
      }
    }
  }

  public void proxyHead(RoutingContext ctx, 
          Iterator<String> it, List<String> traceHeaders) {
    if (!it.hasNext()) {
      proxyRequest(ctx, modules.iterator(), traceHeaders);
    } else {
      String m = it.next();
      ModuleInstance mi = modules.get(m);
      if (!match(mi.getModuleDescriptor().getRoutingEntries(), ctx.request(), true)) {
        proxyHead(ctx, it, traceHeaders);
      } else {
        final long startTime = System.nanoTime();
        HttpServerRequest req = ctx.request();
        HttpClient c = vertx.createHttpClient();
        System.out.println("Make head method=" + ctx.request().method() + " to " + mi.getModuleDescriptor().getName());
        HttpClientRequest c_req = c.get(mi.getPort(),
                "localhost", req.uri(), res -> {
                  System.out.println("Got head res " + res.statusCode() + "/" + res.statusMessage() +
                          " from " + mi.getModuleDescriptor().getName());
                  if (res.statusCode() == 202) {
                    long timeDiff = (System.nanoTime() - startTime) / 1000;
                    traceHeaders.add("CHECK " + mi.getModuleDescriptor().getName() + ":"
                            + res.statusCode() + " " + timeDiff + "us");
                    proxyHead(ctx, it, traceHeaders);
                    return;
                  }
                  ctx.response().headers().setAll(res.headers());
                  int status = res.statusCode();
                  ctx.response().setStatusCode(status);
                  System.out.println("Make head: Setting status code " + status);
                  res.handler(data -> {
                    System.out.println("Got data " + data);
                    ctx.response().write(data);
                  });
                  res.endHandler(v -> {
                    long timeDiff = (System.nanoTime() - startTime) / 1000;
                    traceHeaders.add("CHECK " + mi.getModuleDescriptor().getName() + ":"
                            + res.statusCode() + " " + timeDiff + "us");
                    addTraceHeaders(ctx, traceHeaders);
                    ctx.response().end();
                  });
                });
        System.out.println("Make head phase two to " + mi.getModuleDescriptor().getName());
        c_req.headers().setAll(req.headers());
        c_req.end();
      }
    }
  }

} // class
