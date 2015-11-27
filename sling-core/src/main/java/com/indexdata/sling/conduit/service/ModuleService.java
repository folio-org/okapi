/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit.service;

import com.indexdata.sling.conduit.ModuleDescriptor;
import com.indexdata.sling.conduit.ProcessModuleHandle;
import com.indexdata.sling.conduit.RoutingEntry;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleService {

  class ModuleInstance {

    ModuleDescriptor md;
    ProcessModuleHandle pmh;
    int port;

    ModuleInstance(ModuleDescriptor md, ProcessModuleHandle pmh, int port) {
      this.md = md;
      this.pmh = pmh;
      this.port = port;
    }
  }

  class Ports {

    int port_start;
    int port_end;
    Boolean[] ports;

    Ports(int port_start, int port_end) {
      this.port_start = port_start;
      this.port_end = port_end;
      this.ports = new Boolean[port_end - port_start];
      for (int i = 0; i < ports.length; i++) {
        ports[i] = false;
      }
    }

    int get() {
      for (int i = 0; i < ports.length; i++) {
        if (ports[i] == false) {
          ports[i] = true;
          return i + port_start;
        }
      }
      return -1;
    }

    void free(int p) {
      if (p > 0) {
        ports[p - port_start] = false;
      }
    }
  }

  LinkedHashMap<String, ModuleInstance> enabled = new LinkedHashMap<>();
  Ports ports;

  final private Vertx vertx;

  public ModuleService(Vertx vertx, int port_start, int port_end) {
    this.vertx = vertx;
    this.ports = new Ports(port_start, port_end);
  }

  public void create(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);

      final String name = md.getName();
      if (enabled.containsKey(name)) {
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
      enabled.put(name, new ModuleInstance(md, pmh, use_port));

      pmh.start(future -> {
        if (future.succeeded()) {
          ctx.response().setStatusCode(201).putHeader("Location", uri).end();
        } else {
          enabled.remove(md.getName());
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

    if (!enabled.containsKey(id)) {
      ctx.response().setStatusCode(404).end();
      return;
    }
    String s = Json.encodePrettily(enabled.get(id).md);
    ctx.response().end(s);
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

  public void proxy(RoutingContext ctx) {
    Iterator<String> it = enabled.keySet().iterator();
    proxyHead(ctx, it);
  }

  public void proxyRequest(RoutingContext ctx, Iterator<String> it) {
    if (!it.hasNext()) {
      ctx.response().setStatusCode(404).end();
    } else {
      String m = it.next();
      ModuleInstance mi = enabled.get(m);
      if (!match(mi.md.getRoutingEntries(), ctx.request(), false)) {
        proxyRequest(ctx, it);
      } else {
        HttpServerRequest req = ctx.request();
        HttpClient c = vertx.createHttpClient();
        System.out.println("Make request to " + mi.md.getName());
        HttpClientRequest c_req = c.request(ctx.request().method(), mi.port,
                "localhost", req.uri(), res -> {
                  System.out.println("Got response " + res.statusCode() + " from " + mi.md.getName());
                  ctx.response().setChunked(true);
                  ctx.response().setStatusCode(res.statusCode());
                  ctx.response().headers().setAll(res.headers());
                  res.handler(data -> {
                    ctx.response().write(data);
                  });
                  res.endHandler(v -> ctx.response().end());
                });
        System.out.println("Make request phase two to " + mi.md.getName());
        c_req.setChunked(true);
        c_req.headers().setAll(req.headers());
        req.handler(data -> {
          c_req.write(data);
        });
        req.endHandler(v -> c_req.end());
      }
    }
  }

  public void proxyHead(RoutingContext ctx, Iterator<String> it) {
    if (!it.hasNext()) {
      proxyRequest(ctx, enabled.keySet().iterator());
    } else {
      String m = it.next();
      ModuleInstance mi = enabled.get(m);
      if (!match(mi.md.getRoutingEntries(), ctx.request(), true)) {
        proxyHead(ctx, it);
      } else {
        HttpServerRequest req = ctx.request();
        HttpClient c = vertx.createHttpClient();
        System.out.println("Make head method=" + ctx.request().method() + " to " + mi.md.getName());
        HttpClientRequest c_req = c.get(mi.port,
                "localhost", req.uri(), res -> {
                  System.out.println("Got head res " + res.statusCode() + "/" + res.statusMessage() + " from " + mi.md.getName());
                  if (res.statusCode() == 202) {
                    proxyHead(ctx, it);
                    return;
                  }
                  ctx.response().setStatusCode(res.statusCode());
                  ctx.response().headers().setAll(res.headers());
                  res.handler(data -> {
                    System.out.println("Got data " + data);
                    ctx.response().write(data);
                  });
                  res.endHandler(v -> ctx.response().end());
                  return;
                });
        System.out.println("Make head phase two to " + mi.md.getName());
        c_req.headers().setAll(req.headers());
        c_req.end();
      }
    }
  }

} // class
