/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import com.codahale.metrics.Timer;
import io.vertx.core.Handler;
import okapi.bean.ModuleInstance;
import okapi.bean.Tenant;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Iterator;
import okapi.bean.DeploymentDescriptor;
import okapi.bean.RoutingEntry;
import okapi.discovery.DiscoveryManager;
import okapi.util.DropwizardHelper;
import static okapi.util.ErrorType.NOT_FOUND;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import static okapi.util.HttpResponse.*;
import okapi.util.Success;

public class ProxyService {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  private ModuleManager modules;
  private HttpClient httpClient;
  //private TenantWebService tenantService;
  private TenantManager tenantService;
  private DiscoveryManager dm;

  final private Vertx vertx;

  public ProxyService(Vertx vertx, ModuleManager modules, TenantManager tm, DiscoveryManager dm) {
    this.vertx = vertx;
    this.modules = modules;
    this.tenantService = tm;
    this.dm = dm;
    this.httpClient = vertx.createHttpClient();
  }

  /**
   * Add the trace headers to the response
   */
  private void addTraceHeaders(RoutingContext ctx, List<String> traceHeaders) {
    for (String th : traceHeaders) {
      ctx.response().headers().add("X-Okapi-Trace", th);
    }
  }

  private void makeTraceHeader(RoutingContext ctx, ModuleInstance mi, int statusCode, 
      Timer.Context timer, List<String> traceHeaders) {
    //long timeDiff = (System.nanoTime() - timer) / 1000;
    long timeDiff = timer.stop() / 1000;
    traceHeaders.add(ctx.request().method() + " "
            + mi.getModuleDescriptor().getId() + ":"
            + statusCode + " " + timeDiff + "us");
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

  public List<ModuleInstance> getModulesForRequest(HttpServerRequest hreq, Tenant t) {
    List<ModuleInstance> r = new ArrayList<>();
    for (String s : modules.list()) {
      if (t.isEnabled(s)) {
        RoutingEntry[] rr = modules.get(s).getRoutingEntries();
        for (int i = 0; i < rr.length; i++) {
          if (match(rr[i], hreq)) {
            ModuleInstance mi = new ModuleInstance(modules.get(s), rr[i]);
            r.add(mi);
          }
        }
      }
    }
    Comparator<ModuleInstance> cmp = (ModuleInstance a, ModuleInstance b) 
      -> a.getRoutingEntry().getLevel().compareTo(b.getRoutingEntry().getLevel());
    r.sort(cmp);
    return r;
  }

  private void resolveUrls(Iterator<ModuleInstance> it, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>());
    } else {
      ModuleInstance mi = it.next();
      dm.get(mi.getModuleDescriptor().getId(), res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          List<DeploymentDescriptor> l = res.result();
          if (l.size() < 1) {
            fut.handle(new Failure<>(NOT_FOUND,mi.getModuleDescriptor().getId()));
            return;
          }
          mi.setUrl(l.get(0).getUrl());
          resolveUrls(it, fut);
        }
      });
    }
  }

  public void proxy(RoutingContext ctx) {
    String tenant_id = ctx.request().getHeader("X-Okapi-Tenant");
    if (tenant_id == null) {
      responseText(ctx, 403).end("Missing Tenant");
      return;
    }
    Tenant tenant = tenantService.get(tenant_id);
    if (tenant == null) {
      responseText(ctx, 400).end("No such Tenant " + tenant_id);
      return;
    }

    String metricKey = "proxy." + tenant_id + "." + ctx.request().method() + "." + ctx.normalisedPath() ;
    DropwizardHelper.markEvent(metricKey);

    List<ModuleInstance> l = getModulesForRequest(ctx.request(), tenant);
    resolveUrls(l.iterator(), res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        List<String> traceHeaders = new ArrayList<>();
        ReadStream<Buffer> content = ctx.request();
        content.pause();
        proxyR(ctx, l.iterator(), traceHeaders, content, null);
      }
    });
  }

  private void proxyRequestHttpClient(RoutingContext ctx,
          Iterator<ModuleInstance> it,
          List<String> traceHeaders, Buffer bcontent,
          ModuleInstance mi, Timer.Context timer) {

    HttpClientRequest c_req = httpClient.requestAbs(ctx.request().method(),
            mi.getUrl() + ctx.request().uri(), res -> {
              if (res.statusCode() < 200 || res.statusCode() >= 300) {
                ctx.response().setChunked(true);
                ctx.response().setStatusCode(res.statusCode());
                ctx.response().headers().setAll(res.headers());
                makeTraceHeader(ctx, mi, res.statusCode(), timer, traceHeaders);
                res.handler(data -> {
                  ctx.response().write(data);
                });
                res.endHandler(x -> {
                  timer.close();
                  ctx.response().end();
                });
                res.exceptionHandler(x -> {
                  logger.debug("proxyRequestHttpClient: res exception " + x.getMessage());
                });
              } else if (it.hasNext()) {
                makeTraceHeader(ctx, mi, res.statusCode(), timer, traceHeaders);
                timer.close();
                proxyR(ctx, it, traceHeaders, null, bcontent);
              } else {
                ctx.response().setChunked(true);
                ctx.response().setStatusCode(res.statusCode());
                ctx.response().headers().setAll(res.headers());
                makeTraceHeader(ctx, mi, res.statusCode(), timer, traceHeaders);
                res.endHandler(x -> {
                  timer.close();
                  ctx.response().end(bcontent);
                });
                res.exceptionHandler(x -> {
                  logger.debug("proxyRequestHttpClient: res exception " + x.getMessage());
                });
              }
            });
    c_req.exceptionHandler(res -> {
      logger.debug("proxyRequestHttpClient failure: " + mi.getUrl() + ": " + res.getMessage());
      responseText(ctx, 500)
              .end("connect url " + mi.getUrl() + ": " + res.getMessage());
    });
    c_req.setChunked(true);
    c_req.headers().setAll(ctx.request().headers());
    c_req.end(bcontent);
  }

  private void proxyRequestOnly(RoutingContext ctx,
          Iterator<ModuleInstance> it, List<String> traceHeaders,
          ReadStream<Buffer> content, Buffer bcontent, 
          ModuleInstance mi, Timer.Context timer) {
    if (bcontent != null) {
      proxyRequestHttpClient(ctx, it, traceHeaders, bcontent, mi, timer);
    } else {
      final Buffer incoming = Buffer.buffer();
      content.handler(data -> {
        incoming.appendBuffer(data);
      });
      content.endHandler(v -> {
        proxyRequestHttpClient(ctx, it, traceHeaders, incoming, mi, timer);
        timer.close();
      });
      content.resume();
    }
  }

  private void proxyRequestResponse(RoutingContext ctx,
          Iterator<ModuleInstance> it, List<String> traceHeaders,
          ReadStream<Buffer> content, Buffer bcontent, ModuleInstance mi, Timer.Context timer) {
    HttpClientRequest c_req = httpClient.requestAbs(ctx.request().method(),
            mi.getUrl() + ctx.request().uri(), res -> {
              if (res.statusCode() >= 200 && res.statusCode() < 300
              && it.hasNext()) {
                makeTraceHeader(ctx, mi, res.statusCode(), timer, traceHeaders);
                res.pause();
                proxyR(ctx, it, traceHeaders, res, null);
              } else {
                ctx.response().setChunked(true);
                ctx.response().setStatusCode(res.statusCode());
                ctx.response().headers().setAll(res.headers());
                makeTraceHeader(ctx, mi, res.statusCode(), timer, traceHeaders);
                res.handler(data -> {
                  ctx.response().write(data);
                });
                res.endHandler(v -> {
                  timer.stop();
                  ctx.response().end();
                });
                res.exceptionHandler(v -> {
                  logger.debug("proxyRequestResponse: res exception " + v.getMessage());
                });
              }
            });
    c_req.exceptionHandler(res -> {
      logger.debug("proxyRequestResponse failure: " + mi.getUrl() + ": " + res.getMessage());
      timer.stop();
      responseText(ctx, 500)
              .end("connect url " + mi.getUrl() + ": " + res.getMessage());
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
        logger.debug("proxyRequestResponse: content exception " + v.getMessage());
      });
      content.resume();
    }
  }

  private void proxyHeaders(RoutingContext ctx,
          Iterator<ModuleInstance> it, List<String> traceHeaders,
          ReadStream<Buffer> content, Buffer bcontent, ModuleInstance mi, Timer.Context timer) {
    HttpClientRequest c_req = httpClient.requestAbs(ctx.request().method(),
            mi.getUrl() + ctx.request().uri(), res -> {
              if (res.statusCode() < 200 || res.statusCode() >= 300) {
                ctx.response().setChunked(true);
                ctx.response().setStatusCode(res.statusCode());
                ctx.response().headers().setAll(res.headers());
                makeTraceHeader(ctx, mi, res.statusCode(), timer, traceHeaders);
                res.handler(data -> {
                  ctx.response().write(data);
                });
                res.endHandler(v -> {
                  ctx.response().end();
                });
                res.exceptionHandler(v -> {
                  logger.debug("proxyHeaders: res exception " + v.getMessage());
                });
              } else if (it.hasNext()) {
                for (String s : res.headers().names()) {
                  if (s.startsWith("X-") || s.startsWith("x-")) {
                    final String v = res.headers().get(s);
                    ctx.request().headers().add(s, v);
                  }
                }
                res.endHandler(x -> {
                  proxyR(ctx, it, traceHeaders, content, bcontent);
                });
              } else {
                ctx.response().setChunked(true);
                ctx.response().setStatusCode(res.statusCode());
                ctx.response().headers().setAll(res.headers());
                makeTraceHeader(ctx, mi, res.statusCode(), timer, traceHeaders);
                if (bcontent == null) {
                  content.handler(data -> {
                    ctx.response().write(data);
                  });
                  content.endHandler(v -> {
                    ctx.response().end();
                  });
                  content.exceptionHandler(v -> {
                    logger.debug("proxyHeaders: content exception " + v.getMessage());
                  });
                  content.resume();
                } else {
                  ctx.response().end(bcontent);
                }
              }
            });
    c_req.exceptionHandler(res -> {
      logger.debug("proxyHeaders failure: " + mi.getUrl() + ": " + res.getMessage());
      responseText(ctx, 500)
              .end("connect url " + mi.getUrl() + ": " + res.getMessage());
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
      responseText(ctx, 404).end();
    } else {
      ModuleInstance mi = it.next();
      final long timer2 = System.nanoTime();
      String tenantId = ctx.request().getHeader("X-Okapi-Tenant");
      if ( tenantId == null || tenantId.isEmpty())
        tenantId = "???"; // Should not happen, we have validated earlier
      String metricKey = "proxy." + tenantId + ".module." + mi.getModuleDescriptor().getId();
      Timer.Context timerContext = DropwizardHelper.getTimerContext(metricKey);

      String rtype = mi.getRoutingEntry().getType();
      if ("request-only".equals(rtype)) {
        proxyRequestOnly(ctx, it, traceHeaders, content, bcontent, mi, timerContext);
      } else if ("request-response".equals(rtype)) {
        proxyRequestResponse(ctx, it, traceHeaders, content, bcontent, mi, timerContext);
      } else if ("headers".equals(rtype)) {
        proxyHeaders(ctx, it, traceHeaders, content, bcontent, mi, timerContext);
      } else {
        logger.warn("proxyR: bad rtype: " + rtype);
      }
    }
  }
} // class
