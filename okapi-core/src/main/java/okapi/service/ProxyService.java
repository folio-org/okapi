/*
 * Copyright (C) 2015 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
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

  private final ModuleManager modules;
  private final HttpClient httpClient;
  //private TenantWebService tenantService;
  private final TenantManager tenantManager;
  private final DiscoveryManager discoveryManager;

  final private Vertx vertx;

  public ProxyService(Vertx vertx, ModuleManager modules, TenantManager tm, DiscoveryManager dm) {
    this.vertx = vertx;
    this.modules = modules;
    this.tenantManager = tm;
    this.discoveryManager = dm;
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
      for (String method : methods) {
        if (method.equals("*") || method.equals(req.method().name())) {
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
        for (RoutingEntry rr1 : rr) {
          if (match(rr1, hreq)) {
            ModuleInstance mi = new ModuleInstance(modules.get(s), rr1);
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

  // Get the auth bits from the module list into
  // X-Okapi-Auth-Required and X-Okapi-Auth-Wanted headers
  private void authHeaders(List<ModuleInstance> modlist, Map<String, String> extraReqHeaders) {
    Set<String> req = new HashSet<>();
    Set<String> want = new HashSet<>();
    for (ModuleInstance mod : modlist) {
      RoutingEntry re = mod.getRoutingEntry();
      String[] reqp = re.getRequiredPermissions();
      if (reqp != null) {
        req.addAll(Arrays.asList(reqp));
      }
      String[] wap = re.getWantedPermissions();
      if (wap != null) {
        want.addAll(Arrays.asList(wap));
      }
    }
    if (!req.isEmpty()) {
      logger.debug("authHeaders:X-Okapi-Auth-Required: " + String.join(",", req));
      extraReqHeaders.put("X-Okapi-Auth-Required", String.join(",", req));
    }
    if (!want.isEmpty()) {
      logger.debug("authHeaders:X-Okapi-Auth-Wanted: " + String.join(",", want));
      extraReqHeaders.put("X-Okapi-Auth-Wanted", String.join(",", want));
    }
  }

  private void resolveUrls(Iterator<ModuleInstance> it, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>());
    } else {
      ModuleInstance mi = it.next();
      discoveryManager.get(mi.getModuleDescriptor().getId(), res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          List<DeploymentDescriptor> l = res.result();
          if (l.size() < 1) {
            fut.handle(new Failure<>(NOT_FOUND, mi.getModuleDescriptor().getId()));
            return;
          }
          mi.setUrl(l.get(0).getUrl());
          resolveUrls(it, fut);
        }
      });
    }
  }

  static void relayToResponse(HttpServerResponse hres, HttpClientResponse res) {
    hres.setChunked(true);
    hres.setStatusCode(res.statusCode());
    hres.headers().setAll(res.headers());
    hres.headers().remove("Content-Length");
  }

  static void relayToRequest(RoutingContext ctx, HttpClientResponse res) {
    for (String s : res.headers().names()) {
      if (s.startsWith("X-") || s.startsWith("x-")) {
        final String v = res.headers().get(s);
        ctx.request().headers().set(s, v);
      }
    }
  }

  private void log(HttpClientRequest creq) {
    logger.debug(creq.method().name() + " " + creq.uri());
    Iterator<Map.Entry<String, String>> iterator = creq.headers().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, String> next = iterator.next();
      logger.debug(" " + next.getKey() + ":" + next.getValue());
    }
  }

  public void proxy(RoutingContext ctx) {
    String tenant_id = ctx.request().getHeader("X-Okapi-Tenant");
    if (tenant_id == null) {
      responseText(ctx, 403).end("Missing Tenant");
      return;
    }
    ReadStream<Buffer> content = ctx.request();
    Tenant tenant = tenantManager.get(tenant_id);
    if (tenant == null) {
      responseText(ctx, 400).end("No such Tenant " + tenant_id);
      return;
    }
    // Pause the request data stream before doing any slow ops, otherwise
    // it will get read into a buffer somewhere...
    content.pause();
    String metricKey = "proxy." + tenant_id + "." + ctx.request().method() + "." + ctx.normalisedPath();
    DropwizardHelper.markEvent(metricKey);

    List<ModuleInstance> l = getModulesForRequest(ctx.request(), tenant);
    Map<String,String> extraReqHeaders = new HashMap<>() ;
    authHeaders(l,extraReqHeaders);

    resolveUrls(l.iterator(), res -> {
      if (res.failed()) {
        responseError(ctx, res.getType(), res.cause());
      } else {
        List<String> traceHeaders = new ArrayList<>();
        proxyR(ctx, l.iterator(), traceHeaders, extraReqHeaders, content, null);
      }
    });
  }

  private void proxyRequestHttpClient(RoutingContext ctx,
          Iterator<ModuleInstance> it,
          List<String> traceHeaders, Map<String,String> extraReqHeaders,
          Buffer bcontent,   ModuleInstance mi, Timer.Context timer) {
    HttpClientRequest c_req = httpClient.requestAbs(ctx.request().method(),
            mi.getUrl() + ctx.request().uri(), res -> {
              if (res.statusCode() < 200 || res.statusCode() >= 300) {
                relayToResponse(ctx.response(), res);
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
                relayToRequest(ctx, res);
                proxyR(ctx, it, traceHeaders, extraReqHeaders, null, bcontent);
              } else {
                relayToResponse(ctx.response(), res);
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
    c_req.headers().setAll(extraReqHeaders);
    c_req.end(bcontent);
    log(c_req);
  }

  private void proxyRequestOnly(RoutingContext ctx, Iterator<ModuleInstance> it,
          List<String> traceHeaders, Map<String,String> extraReqHeaders,
          ReadStream<Buffer> content, Buffer bcontent,
          ModuleInstance mi, Timer.Context timer) {
    if (bcontent != null) {
      proxyRequestHttpClient(ctx, it, traceHeaders, extraReqHeaders,
        bcontent, mi, timer);
    } else {
      final Buffer incoming = Buffer.buffer();
      content.handler(data -> {
        incoming.appendBuffer(data);
      });
      content.endHandler(v -> {
        proxyRequestHttpClient(ctx, it, traceHeaders, extraReqHeaders,
          incoming, mi, timer);
        timer.close();
      });
      content.resume();
    }
  }

  private void proxyRequestResponse(RoutingContext ctx,
          Iterator<ModuleInstance> it,
          List<String> traceHeaders, Map<String,String> extraReqHeaders,
          ReadStream<Buffer> content, Buffer bcontent,
          ModuleInstance mi, Timer.Context timer) {
    HttpClientRequest c_req = httpClient.requestAbs(ctx.request().method(),
            mi.getUrl() + ctx.request().uri(), res -> {
              if (res.statusCode() >= 200 && res.statusCode() < 300
              && res.getHeader("X-Okapi-stop") == null
              && it.hasNext()) {
                makeTraceHeader(ctx, mi, res.statusCode(), timer, traceHeaders);
                relayToRequest(ctx, res);
                res.pause();
                proxyR(ctx, it, traceHeaders, extraReqHeaders, res, null);
              } else {
                relayToResponse(ctx.response(), res);
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
    c_req.headers().setAll(extraReqHeaders);
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
    log(c_req);
  }

  private void proxyHeaders(RoutingContext ctx,   Iterator<ModuleInstance> it,
          List<String> traceHeaders, Map<String,String> extraReqHeaders,  // TODO extras!
          ReadStream<Buffer> content, Buffer bcontent, ModuleInstance mi, Timer.Context timer) {
    HttpClientRequest c_req = httpClient.requestAbs(ctx.request().method(),
            mi.getUrl() + ctx.request().uri(), res -> {
              if (res.statusCode() < 200 || res.statusCode() >= 300) {
                relayToResponse(ctx.response(), res);
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
                relayToRequest(ctx, res);
                res.endHandler(x -> {
                  proxyR(ctx, it, traceHeaders,extraReqHeaders, content, bcontent);
                });
              } else {
                relayToResponse(ctx.response(), res);
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
    c_req.headers().setAll(extraReqHeaders);
    c_req.headers().setAll(ctx.request().headers());
    c_req.headers().remove("Content-Length");
    c_req.end();
    log(c_req);
  }

  private void proxyR(RoutingContext ctx,
          Iterator<ModuleInstance> it,
          List<String> traceHeaders, Map<String,String> extraReqHeaders,
          ReadStream<Buffer> content, Buffer bcontent) {
    if (!it.hasNext()) {
      content.resume();
      addTraceHeaders(ctx, traceHeaders);
      responseText(ctx, 404).end();
    } else {
      ModuleInstance mi = it.next();
      String tenantId = ctx.request().getHeader("X-Okapi-Tenant");
      if (tenantId == null || tenantId.isEmpty()) {
        tenantId = "???"; // Should not happen, we have validated earlier
      }
      String metricKey = "proxy." + tenantId + ".module." + mi.getModuleDescriptor().getId();
      Timer.Context timerContext = DropwizardHelper.getTimerContext(metricKey);

      String rtype = mi.getRoutingEntry().getType();
      logger.debug("Invoking module " + mi.getModuleDescriptor().getName()
              + " type " + rtype
              + " level " + mi.getRoutingEntry().getLevel()
              + " path " + mi.getRoutingEntry().getPath());
      if ("request-only".equals(rtype)) {
        proxyRequestOnly(ctx, it, traceHeaders, extraReqHeaders,
          content, bcontent, mi, timerContext);
      } else if ("request-response".equals(rtype)) {
        proxyRequestResponse(ctx, it, traceHeaders, extraReqHeaders,
          content, bcontent, mi, timerContext);
      } else if ("headers".equals(rtype)) {
        proxyHeaders(ctx, it, traceHeaders, extraReqHeaders,
          content, bcontent, mi, timerContext);
      } else {
        logger.warn("proxyR: bad rtype: " + rtype);
        responseText(ctx, 500).end(); // Should not happen
      }
    }
  }

} // class
