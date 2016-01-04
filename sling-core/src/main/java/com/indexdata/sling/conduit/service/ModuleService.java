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
import com.indexdata.sling.conduit.Tenant;
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
  private Modules modules;
  private Ports ports;
  private HttpClient httpClient;
  private TenantService tenantService;

  final private Vertx vertx;

  public ModuleService(Vertx vertx, int port_start, int port_end, TenantService ts) {
    this.vertx = vertx;
    this.ports = new Ports(port_start, port_end);
    this.modules = new Modules();
    this.tenantService = ts;
    this.httpClient = vertx.createHttpClient();
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

  private void makeTraceHeader(RoutingContext ctx, ModuleInstance mi, int statusCode, long startTime, List<String> traceHeaders) {
    long timeDiff = (System.nanoTime() - startTime) / 1000;
    traceHeaders.add(ctx.request().method() + " "
            + mi.getModuleDescriptor().getName() + ":"
            + statusCode+ " " + timeDiff + "us");
    addTraceHeaders(ctx, traceHeaders);
  }

  
  public void proxy(RoutingContext ctx) {
    String tenant_id = ctx.request().getHeader("X-Sling-Tenant");
    if (tenant_id == null) {
      ctx.response().setStatusCode(403).end("Missing Tenant");
      return;
    }
    Tenant tenant = tenantService.get(tenant_id);
    if (tenant == null) {
      ctx.response().setStatusCode(400).end("No such Tenant " + tenant_id);
      return;     
    }
    Iterator<ModuleInstance> it = modules.getModulesForRequest(ctx.request(), tenant);
    List<String> traceHeaders = new ArrayList<>();
    ReadStream<Buffer> content = ctx.request();
    proxyR(ctx, it, traceHeaders, content, null);
  }
  
  private void proxyR(RoutingContext ctx,
          Iterator<ModuleInstance> it, List<String> traceHeaders,
          ReadStream<Buffer> content, Buffer bcontent) {
    if (!it.hasNext()) {
      addTraceHeaders(ctx, traceHeaders);
      ctx.response().setStatusCode(404).end();
    } else {
      ModuleInstance mi = it.next();
      final long startTime = System.nanoTime();
      String rtype = mi.getRoutingEntry().getType();
      if ("request-only".equals(rtype)) {
        if (bcontent != null) {
          HttpClientRequest c_req = httpClient.request(ctx.request().method(), mi.getPort(),
                "localhost", ctx.request().uri(), res -> {
                  if (res.statusCode() >= 200 && res.statusCode() < 300
                  && it.hasNext()) {
                    makeTraceHeader(ctx, mi, res.statusCode(), startTime, traceHeaders);
                    proxyR(ctx, it, traceHeaders, null, bcontent);
                  } else {
                    ctx.response().setChunked(true);
                    ctx.response().setStatusCode(res.statusCode());
                    ctx.response().headers().setAll(res.headers());
                    makeTraceHeader(ctx, mi, res.statusCode(), startTime, traceHeaders);
                    res.endHandler(x -> {
                      ctx.response().end(bcontent);
                    });
                    res.exceptionHandler(x -> {
                      System.out.println("res exception " + x.getMessage());
                    });
                  }
                });     
          c_req.headers().setAll(ctx.request().headers());
          c_req.end(bcontent);
        } else {
          final Buffer incoming = Buffer.buffer();
          content.handler(data -> {
            incoming.appendBuffer(data);
          });
          content.endHandler(v -> {
            HttpClientRequest c_req = httpClient.request(ctx.request().method(), mi.getPort(),
                    "localhost", ctx.request().uri(), res -> {
                      if (res.statusCode() >= 200 && res.statusCode() < 300
                      && it.hasNext()) {
                        makeTraceHeader(ctx, mi, res.statusCode(), startTime, traceHeaders);
                        proxyR(ctx, it, traceHeaders, null, incoming);
                      } else {
                        ctx.response().setChunked(true);
                        ctx.response().setStatusCode(res.statusCode());
                        ctx.response().headers().setAll(res.headers());
                        makeTraceHeader(ctx, mi, res.statusCode(), startTime, traceHeaders);                      
                        res.endHandler(x -> {
                          ctx.response().end(incoming);
                        });
                        res.exceptionHandler(x -> {
                          System.out.println("res exception " + x.getMessage());
                        });
                      }
                    });
            c_req.setChunked(true);
            c_req.headers().setAll(ctx.request().headers());
            c_req.end(incoming);
          });
        }
      } else if ("request-response".equals(rtype)) {
        HttpClientRequest c_req = httpClient.request(ctx.request().method(), mi.getPort(),
                "localhost", ctx.request().uri(), res -> {
                  if (res.statusCode() >= 200 && res.statusCode() < 300
                  && it.hasNext()) {
                    makeTraceHeader(ctx, mi, res.statusCode(), startTime, traceHeaders);
                    proxyR(ctx, it, traceHeaders, res, null);
                  } else {
                    ctx.response().setChunked(true);
                    ctx.response().setStatusCode(res.statusCode());
                    ctx.response().headers().setAll(res.headers());
                    makeTraceHeader(ctx, mi, res.statusCode(), startTime, traceHeaders);
                    res.handler(data -> {
                      ctx.response().write(data);
                    });
                    res.endHandler(v -> {
                      ctx.response().end();
                    });
                    res.exceptionHandler(v -> {
                      System.out.println("res exception " + v.getMessage());
                    });
                  }
                });
        c_req.setChunked(true);
        c_req.headers().setAll(ctx.request().headers());
        if (bcontent != null) {
          c_req.end(bcontent);
        } else {
          content.handler(data -> {
            c_req.write(data);
          });
          content.endHandler(v -> {
            c_req.end();
          });
          content.exceptionHandler(v -> {
            System.out.println("content exception " + v.getMessage());
          });
        }
      } else {
        System.out.println("rtype = " + rtype);
      }
    }
  }

} // class
