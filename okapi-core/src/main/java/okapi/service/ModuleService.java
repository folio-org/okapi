/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import okapi.bean.ModuleDescriptor;
import okapi.bean.ModuleInstance;
import okapi.bean.Modules;
import okapi.bean.Ports;
import okapi.bean.ProcessModuleHandle;
import okapi.bean.Tenant;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Iterator;
import okapi.bean.RoutingEntry;

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

      final String id = md.getId();
      String url;
      final int use_port = ports.get();
      int spawn_port = -1;
      ModuleInstance m = modules.get(id);
      if (m != null) {
        ctx.response().setStatusCode(400).end("module " + id
                + " already deployed");
        return;
      }
      if (md.getUrl() == null) {
        if (use_port == -1) {
          ctx.response().setStatusCode(400).end("module " + id
                  + " can not be deployed: all ports in use");
        }
        spawn_port = use_port;
        url = "http://localhost:" + use_port;
      } else {
        ports.free(use_port);
        url = md.getUrl();
      }
      if (md.getDescriptor() != null) {
        // enable it now so that activation for 2nd one will fail
        ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, md.getDescriptor(),
                spawn_port);
        modules.put(id, new ModuleInstance(md, pmh, url));

        pmh.start(future -> {
          if (future.succeeded()) {
            ctx.response().setStatusCode(201)
                    .putHeader("Location", ctx.request().uri() + "/" + id)
                    .end();
          } else {
            modules.remove(md.getId());
            ports.free(use_port);
            ctx.response().setStatusCode(500).end(future.cause().getMessage());
          }
        });
      } else {
        modules.put(id, new ModuleInstance(md, null, url));
            ctx.response().setStatusCode(201)
                    .putHeader("Location", ctx.request().uri() + "/" + id)
                    .end();
      }
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
  public void list(RoutingContext ctx) {
    String s = Json.encodePrettily(modules.list());
    ctx.response().end(s);
  }

  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    ModuleInstance m = modules.get(id);
    if (m == null) {
      ctx.response().setStatusCode(404).end();
      return;
    }

    ProcessModuleHandle pmh = m.getProcessModuleHandle();
    if (pmh == null) {
      ctx.response().setStatusCode(204).end();
    } else {
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
  }
  
  /** 
   * Add the trace headers to the response 
   */
  private void addTraceHeaders(RoutingContext ctx, List<String> traceHeaders) {
    for ( String th : traceHeaders ) {
      ctx.response().headers().add("X-Okapi-Trace", th);
    }
  }

  private void makeTraceHeader(RoutingContext ctx, ModuleInstance mi, int statusCode, long startTime, List<String> traceHeaders) {
    long timeDiff = (System.nanoTime() - startTime) / 1000;
    traceHeaders.add(ctx.request().method() + " "
            + mi.getModuleDescriptor().getId() + ":"
            + statusCode+ " " + timeDiff + "us");
    addTraceHeaders(ctx, traceHeaders);
  }

  private boolean match(RoutingEntry e, HttpServerRequest req) {
    if (req.uri().startsWith(e.getPath())) {
      String[] methods = e.getMethods();
      for (int j = 0; j < methods.length; j++) {
        if (methods[j].equals("*") || methods[j].equals(req.method().name())) {
          return true;
        }
      }
    }
    return false;
  }

  public Iterator<ModuleInstance> getModulesForRequest(HttpServerRequest hreq, Tenant t) {
    List<ModuleInstance> r = new ArrayList<>();
    for (String s : modules.list()) {
      if (t.isEnabled(s)) {
        RoutingEntry[] rr = modules.get(s).getModuleDescriptor().getRoutingEntries();
        for (int i = 0; i < rr.length; i++) {
          if (match(rr[i], hreq)) {
            ModuleInstance mi = new ModuleInstance(modules.get(s), rr[i]);
            r.add(mi);
          }
        }
      }
    }

    Comparator<ModuleInstance> cmp = new Comparator<ModuleInstance>() {
      public int compare(ModuleInstance a, ModuleInstance b) {
        return a.getRoutingEntry().getLevel().compareTo(b.getRoutingEntry().getLevel());
      }
    };
    r.sort(cmp);
    return r.iterator();
  }

  public void proxy(RoutingContext ctx) {
    String tenant_id = ctx.request().getHeader("X-Okapi-Tenant");
    if (tenant_id == null) {
      ctx.response().setStatusCode(403).end("Missing Tenant");
      return;
    }
    Tenant tenant = tenantService.get(tenant_id);
    if (tenant == null) {
      ctx.response().setStatusCode(400).end("No such Tenant " + tenant_id);
      return;     
    }
    Iterator<ModuleInstance> it = getModulesForRequest(ctx.request(), tenant);
    List<String> traceHeaders = new ArrayList<>();
    ReadStream<Buffer> content = ctx.request();
    content.pause();
    proxyR(ctx, it, traceHeaders, content, null);
  }

  private void proxyRequestHttpClient(RoutingContext ctx,
          Iterator<ModuleInstance> it,
          List<String> traceHeaders, Buffer bcontent,
          ModuleInstance mi, long startTime)
  {
    HttpClientRequest c_req = httpClient.requestAbs(ctx.request().method(),
            mi.getUrl() + ctx.request().uri(), res -> {
              if (res.statusCode() < 200 || res.statusCode() >= 300) {
                ctx.response().setChunked(true);
                ctx.response().setStatusCode(res.statusCode());
                ctx.response().headers().setAll(res.headers());
                makeTraceHeader(ctx, mi, res.statusCode(), startTime, traceHeaders);
                res.handler(data -> {
                  ctx.response().write(data);
                });
                res.endHandler(x -> {
                  ctx.response().end();
                });
                res.exceptionHandler(x -> {
                  System.out.println("res exception " + x.getMessage());
                });
              } else if (it.hasNext()) {
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
    c_req.exceptionHandler(res -> {
      ctx.response().setStatusCode(500).end("connect url "
              + mi.getUrl() + ": " + res.getMessage());
    });
    c_req.setChunked(true);
    c_req.headers().setAll(ctx.request().headers());
    c_req.end(bcontent);
  }

  private void proxyRequestOnly(RoutingContext ctx,
          Iterator<ModuleInstance> it, List<String> traceHeaders,
          ReadStream<Buffer> content, Buffer bcontent, ModuleInstance mi, long startTime)
  {
    if (bcontent != null) {
      proxyRequestHttpClient(ctx, it, traceHeaders, bcontent, mi, startTime);
    } else {
      final Buffer incoming = Buffer.buffer();
      content.handler(data -> {
        incoming.appendBuffer(data);
      });
      content.endHandler(v -> {
        proxyRequestHttpClient(ctx, it, traceHeaders, incoming, mi, startTime);
      });
      content.resume();
    }
  }

  private void proxyRequestResponse(RoutingContext ctx,
          Iterator<ModuleInstance> it, List<String> traceHeaders,
          ReadStream<Buffer> content, Buffer bcontent, ModuleInstance mi, long startTime)
  {
    HttpClientRequest c_req = httpClient.requestAbs(ctx.request().method(),
            mi.getUrl() + ctx.request().uri(), res -> {
              if (res.statusCode() >= 200 && res.statusCode() < 300
              && it.hasNext()) {
                makeTraceHeader(ctx, mi, res.statusCode(), startTime, traceHeaders);
                res.pause();
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
    c_req.exceptionHandler(res -> {
      ctx.response().setStatusCode(500).end("connect url "
              + mi.getUrl() + ": " + res.getMessage());
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
      content.resume();
    }
  }

  private void proxyHeaders(RoutingContext ctx,
          Iterator<ModuleInstance> it, List<String> traceHeaders,
          ReadStream<Buffer> content, Buffer bcontent, ModuleInstance mi, long startTime) {
    HttpClientRequest c_req = httpClient.requestAbs(ctx.request().method(),
            mi.getUrl() + ctx.request().uri(), res -> {
              if (res.statusCode() < 200 || res.statusCode() >= 300) {
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
              } else if (it.hasNext()) {
                res.endHandler(x -> {
                  proxyR(ctx, it, traceHeaders, content, bcontent);
                });
              } else {
                ctx.response().setChunked(true);
                ctx.response().setStatusCode(res.statusCode());
                ctx.response().headers().setAll(res.headers());
                makeTraceHeader(ctx, mi, res.statusCode(), startTime, traceHeaders);
                if (bcontent == null) {
                  content.handler(data -> {
                    ctx.response().write(data);
                  });
                  content.endHandler(v -> {
                    ctx.response().end();
                  });
                  content.exceptionHandler(v -> {
                    System.out.println("content exception " + v.getMessage());
                  });
                  content.resume();
                } else {
                  ctx.response().end(bcontent);
                }
              }
            });
    c_req.exceptionHandler(res -> {
      ctx.response().setStatusCode(500).end("connect url "
              + mi.getUrl() + ": " + res.getMessage());
    });
    // c_req.setChunked(true);
    // c_req.headers().setAll(ctx.request().headers());
    c_req.end();
  }

  private void proxyR(RoutingContext ctx,
          Iterator<ModuleInstance> it, List<String> traceHeaders,
          ReadStream<Buffer> content, Buffer bcontent) {
    if (!it.hasNext()) {
      content.resume();
      addTraceHeaders(ctx, traceHeaders);
      ctx.response().setStatusCode(404).end();
    } else {
      ModuleInstance mi = it.next();
      final long startTime = System.nanoTime();
      String rtype = mi.getRoutingEntry().getType();
      if ("request-only".equals(rtype)) {
        proxyRequestOnly(ctx, it, traceHeaders, content, bcontent, mi, startTime);
      } else if ("request-response".equals(rtype)) {
        proxyRequestResponse(ctx, it, traceHeaders, content, bcontent, mi, startTime);
      } else if ("headers".equals(rtype)) {
        proxyHeaders(ctx, it, traceHeaders, content, bcontent, mi, startTime);
      } else {
        System.out.println("rtype = " + rtype);
      }
    }
  }
} // class
