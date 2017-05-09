package org.folio.okapi.service;

import com.codahale.metrics.Timer;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.Tenant;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.RoutingEntry.ProxyType;
import org.folio.okapi.discovery.DiscoveryManager;
import org.folio.okapi.util.DropwizardHelper;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.OkapiToken;
import org.folio.okapi.util.ProxyContext;

/**
 * Okapi's proxy service. Routes incoming requests to relevant modules, as
 * enabled for the current tenant.
 */
public class ProxyService {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  private final ModuleManager modules;
  private final TenantManager tenantManager;
  private final DiscoveryManager discoveryManager;
  private final String okapiUrl;
  final private Vertx vertx;

  public ProxyService(Vertx vertx, ModuleManager modules, TenantManager tm,
    DiscoveryManager dm, String okapiUrl) {
    this.vertx = vertx;
    this.modules = modules;
    this.tenantManager = tm;
    this.discoveryManager = dm;
    this.okapiUrl = okapiUrl;
  }

  /**
   * Make a trace header. Also writes a log entry for the response.
   *
   * @param ctx
   * @param mi
   * @param statusCode
   * @param timer
   * @param pc
   */
  private void makeTraceHeader(ModuleInstance mi, int statusCode,
    Timer.Context timer, ProxyContext pc) {
    RoutingContext ctx = pc.getCtx();
    long timeDiff = timer.stop() / 1000;
    String url = makeUrl(ctx, mi).replaceFirst("[?#].*$", ".."); // rm params
    pc.addTraceHeaderLine(ctx.request().method() + " "
      + mi.getModuleDescriptor().getNameOrId() + " "
      + url + " : " + statusCode + " " + timeDiff + "us");
    pc.addTraceHeaders(ctx);
    pc.logResponse(mi.getModuleDescriptor().getNameOrId(), url, statusCode, timeDiff);
  }


  private boolean match(RoutingEntry e, HttpServerRequest req) {
    return e.match(req.uri(), req.method().name());
  }

  private boolean resolveRedirects(ProxyContext pc,
    List<ModuleInstance> mods,
    final String mod, RoutingEntry re, Tenant t,
    final String loop, final String uri, final String origMod) {
    RoutingContext ctx = pc.getCtx();
    // add the module to the pipeline in any case
    ModuleInstance mi = new ModuleInstance(modules.get(mod), re, uri);
    mods.add(mi);
    if (re.getProxyType() == ProxyType.REDIRECT) { // resolve redirects
      boolean found = false;
      final String redirectPath = re.getRedirectPath();
      for (String trymod : modules.list()) {
        if (t.isEnabled(trymod)) {
          List<RoutingEntry> rr = modules.get(trymod).getProxyRoutingEntries();
          for (RoutingEntry tryre : rr) {
            if (tryre.match(redirectPath, ctx.request().method().name())) {
              final String newUri = re.getRedirectUri(uri);
              found = true;
              logger.debug("resolveRedirects: "
                + ctx.request().method() + " " + uri
                + " => " + trymod + " " + newUri);
              if (loop.contains(redirectPath + " ")) {
                pc.responseError(500, "Redirect loop: " + loop + " -> " + redirectPath);
                return false;
              }
              if (!resolveRedirects( pc, mods, trymod, tryre, t,
                loop + " -> " + redirectPath, newUri, origMod)) {
                return false;
              }
            }
          }
        }
      }
      if (!found) {
        String msg = "Redirecting " + uri + " to " + redirectPath
          + " FAILED. No suitable module found";
        pc.responseError(500, msg);
      }
      return found;
    }
    return true;
  }

  /**
   * Builds the pipeline of modules to be invoked for a request.
   *
   * @param pc
   * @param t The current tenant
   * @return a list of ModuleInstances. In case of error, sets up ctx and
   * returns null.
   */
  public List<ModuleInstance> getModulesForRequest( ProxyContext pc, Tenant t) {
    List<ModuleInstance> mods = new ArrayList<>();
    HttpServerRequest req = pc.getCtx().request();
    for (String mod : modules.list()) {
      if (t.isEnabled(mod)) {
        List<RoutingEntry> rr = modules.get(mod).getProxyRoutingEntries();
        for (RoutingEntry re : rr) {
          if (match(re, req)) {
            if (!resolveRedirects(pc, mods, mod, re, t, "", req.uri(), "")) {
              return null;
            }
          }
        }
      }
    }
    Comparator<ModuleInstance> cmp = (ModuleInstance a, ModuleInstance b)
      -> a.getRoutingEntry().getPhaseLevel().compareTo(b.getRoutingEntry().getPhaseLevel());
    mods.sort(cmp);

    // Check that our pipeline has a real module in it, not just filters,
    // so that we can return a proper 404 for requests that only hit auth
    logger.debug("Checking filters for " + req.absoluteURI());
    boolean found = false;
    for (ModuleInstance inst : mods) {
      if (!inst.getRoutingEntry().match("/", null)) {
        found = true;  // Dirty heuristic: Any path longer than '/' is a real handler
      } // Works for auth, but may fail later.
    }
    if (!found) {
      pc.responseError(404, "No suitable module found for "
        + req.absoluteURI());
      return null;
    }
    return mods;
  }

  /**
   * Extract the tenant. Fix header to standard. Normalizes the Authorization
   * header to X-Okapi-Token, checks that both are not present. Checks if we
   * have X-Okapi-Tenant header, and if not, extracts from the X-Okapi-Token.
   * The tenant will be needed to find the pipeline to route to, and in most
   * cases the first thing that happens is that the auth module will verify the
   * tenant against what it has in the token, so even if a client puts up a bad
   * tenant, we should be safe.
   *
   * @param ctx
   * @return null in case of errors, with the response already set in ctx. If
   * all went well, returns the tenantId for further processing.
   */
  private String tenantHeader(ProxyContext pc) {
    RoutingContext ctx = pc.getCtx();
    String auth = ctx.request().getHeader(XOkapiHeaders.AUTHORIZATION);
    String tok = ctx.request().getHeader(XOkapiHeaders.TOKEN);

    if (auth != null) {
      Pattern pattern = Pattern.compile("Bearer\\s+(.+)"); // Grab anything after 'Bearer' and whitespace
      Matcher matcher = pattern.matcher(auth);
      if (matcher.find() && matcher.groupCount() > 0) {
        auth = matcher.group(1);
      }
    }
    if (auth != null && tok != null && !auth.equals(tok)) {
      pc.responseText(400, "Different tokens in Authentication and X-Okapi-Token. "
        + "Use only one of them");
      return null;
    }
    if (tok == null && auth != null) {
      ctx.request().headers().add(XOkapiHeaders.TOKEN, auth);
      ctx.request().headers().remove(XOkapiHeaders.AUTHORIZATION);
      logger.debug("Okapi: Moved Authorization header to X-Okapi-Token");
    }

    String tenantId = ctx.request().getHeader(XOkapiHeaders.TENANT);
    if (tenantId == null) {
      tenantId = new OkapiToken(ctx).getTenant();
      if (tenantId != null && !tenantId.isEmpty()) {
        ctx.request().headers().add(XOkapiHeaders.TENANT, tenantId);
        logger.debug("Okapi: Recovered tenant from token: '" + tenantId + "'");
      }
    }

    if (tenantId == null) {
      pc.responseText(403, "Missing Tenant");
      return null;
    }
    pc.setTenant(tenantId);
    return tenantId;
  }


  /**
   * Get the auth bits from the module list into X-Okapi-Permissions-Required
   * and X-Okapi-Permissions-Desired headers. Also X-Okapi-Module-Permissions
   * for each module that has such. At the same time, sets the authToken to
   * default for each module. Some of these will be overwritten once the auth
   * module returns with dedicated tokens, but by default we use the one given
   * to us by the client.
   *
   */
  private void authHeaders(List<ModuleInstance> modlist,
    MultiMap requestHeaders, String defaultToken) {
    // Sanitize important headers from the incoming request
    requestHeaders.remove(XOkapiHeaders.PERMISSIONS_REQUIRED);
    requestHeaders.remove(XOkapiHeaders.PERMISSIONS_DESIRED);
    requestHeaders.remove(XOkapiHeaders.MODULE_PERMISSIONS);
    requestHeaders.remove(XOkapiHeaders.EXTRA_PERMISSIONS);
    requestHeaders.remove(XOkapiHeaders.MODULE_TOKENS);
    Set<String> req = new HashSet<>();
    Set<String> want = new HashSet<>();
    Set<String> extraperms = new HashSet<>();
    Map<String, String[]> modperms = new HashMap<>(modlist.size());
    for (ModuleInstance mod : modlist) {
      RoutingEntry re = mod.getRoutingEntry();
      String[] reqp = re.getPermissionsRequired();
      if (reqp != null) {
        req.addAll(Arrays.asList(reqp));
      }
      String[] wap = re.getPermissionsDesired();
      if (wap != null) {
        want.addAll(Arrays.asList(wap));
      }
      String[] modp = re.getModulePermissions();
      if (modp != null) {
        if (re.getProxyType() == ProxyType.REDIRECT) {
          extraperms.addAll(Arrays.asList(modp));
        } else {
          modperms.put(mod.getModuleDescriptor().getId(), modp);
        }
      }

      ModuleDescriptor md = mod.getModuleDescriptor();
      modp = md.getModulePermissions();
      if (modp != null && modp.length > 0) {
        // TODO - The general modperms are DEPRECATED, use the ones in the re.
        if (mod.getRoutingEntry().getProxyType() == ProxyType.REDIRECT) {
          extraperms.addAll(Arrays.asList(modp));
        } else {
          modperms.put(md.getId(), modp);
        }
      }
      mod.setAuthToken(defaultToken);
    } // mod loop
    if (!req.isEmpty()) {
      logger.debug("authHeaders: " + XOkapiHeaders.PERMISSIONS_REQUIRED + " " + String.join(",", req));
      requestHeaders.add(XOkapiHeaders.PERMISSIONS_REQUIRED, String.join(",", req));
    }
    if (!want.isEmpty()) {
      logger.debug("authHeaders: " + XOkapiHeaders.PERMISSIONS_DESIRED + " " + String.join(",", want));
      requestHeaders.add(XOkapiHeaders.PERMISSIONS_DESIRED, String.join(",", want));
    }
    // Add the X-Okapi-Module-Permissions even if empty. That causes auth to return
    // an empty X-Okapi-Module-Token, which will tell us that we have done the mod
    // perms, and no other module should be allowed to do the same.
    String mpj = Json.encode(modperms);
    logger.debug("authHeaders: " + XOkapiHeaders.MODULE_PERMISSIONS + " " + mpj);
    requestHeaders.add(XOkapiHeaders.MODULE_PERMISSIONS, mpj);
    if (!extraperms.isEmpty()) {
      String epj = Json.encode(extraperms);
      logger.debug("authHeaders: " + XOkapiHeaders.EXTRA_PERMISSIONS + " " + epj);
      requestHeaders.add(XOkapiHeaders.EXTRA_PERMISSIONS, epj);
    }
  }

  private void resolveUrls(Iterator<ModuleInstance> it,
    Handler<ExtendedAsyncResult<Void>> fut) {
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
            fut.handle(new Failure<>(NOT_FOUND,
              "No running module instance found for "
              + mi.getModuleDescriptor().getNameOrId()));
            return;
          }
          mi.setUrl(l.get(0).getUrl());
          resolveUrls(it, fut);
        }
      });
    }
  }

  void relayToResponse(HttpServerResponse hres, HttpClientResponse res) {
    hres.setChunked(true);
    hres.setStatusCode(res.statusCode());
    hres.headers().addAll(res.headers());
    hres.headers().remove("Content-Length");
  }

  /**
   * Process the auth module response. Set tokens for those modules that
   * received one.
   */
  void authResponse( HttpClientResponse res, ProxyContext pc) {
    String modTok = res.headers().get(XOkapiHeaders.MODULE_TOKENS);
    if (modTok != null && !modTok.isEmpty()) {
      JsonObject jo = new JsonObject(modTok);
      for (ModuleInstance mi : pc.getModList()) {
        String id = mi.getModuleDescriptor().getId();
        if (jo.containsKey(id)) {
          String tok = jo.getString(id);
          mi.setAuthToken(tok);
          logger.debug("authResponse: token for " + id + ": " + tok);
        } else if (jo.containsKey("_")) {
          String tok = jo.getString("_");
          mi.setAuthToken(tok);
          logger.debug("authResponse: Default (_) token for " + id + ": " + tok);
        }
      }
    }
    res.headers().remove(XOkapiHeaders.MODULE_TOKENS); // nobody else should see them
    res.headers().remove(XOkapiHeaders.MODULE_PERMISSIONS); // They have served their purpose
  }

  void relayToRequest(HttpClientResponse res, ProxyContext pc) {
    if (res.headers().contains(XOkapiHeaders.MODULE_TOKENS)) {
      authResponse(res, pc);
    }
    for (String s : res.headers().names()) {
      if (s.startsWith("X-") || s.startsWith("x-")) {
        final String v = res.headers().get(s);
        pc.getCtx().request().headers().set(s, v);
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

  private String makeUrl(RoutingContext ctx, ModuleInstance mi) {
    logger.debug("makeUrl " + mi.getUrl() + " orig=" + ctx.request().uri()
      + "  to=" + mi.getUri());
    return mi.getUrl() + mi.getUri();
  }

  public void proxy(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(vertx, ctx);
    String tenant_id = tenantHeader(pc);
    if (tenant_id == null) {
      return; // Error code already set in ctx
    }
    ReadStream<Buffer> content = ctx.request();
    Tenant tenant = tenantManager.get(tenant_id);
    if (tenant == null) {
      pc.responseText(400, "No such Tenant " + tenant_id);
      return;
    }
    // Pause the request data stream before doing any slow ops, otherwise
    // it will get read into a buffer somewhere.
    content.pause();
    String metricKey = "proxy." + tenant_id + "." + ctx.request().method() + "." + ctx.normalisedPath();
    DropwizardHelper.markEvent(metricKey);


    String authToken = ctx.request().getHeader(XOkapiHeaders.TOKEN);
    List<ModuleInstance> l = getModulesForRequest( pc, tenant);
    if (l == null) {
      content.resume();
      return; // error already in ctx
    }
    pc.setModList(l);

    ctx.request().headers().add(XOkapiHeaders.URL, okapiUrl);
    authHeaders(l, ctx.request().headers(), authToken);

    resolveUrls(l.iterator(), res -> {
      if (res.failed()) {
        content.resume();
        pc.responseError(res.getType(), res.cause());
      } else {
        proxyR(l.iterator(), pc, content, null);
      }
    });
  }

  private void proxyRequestHttpClient( Iterator<ModuleInstance> it,
    ProxyContext pc, Buffer bcontent, ModuleInstance mi, Timer.Context timer) {
    RoutingContext ctx = pc.getCtx();
    HttpClientRequest c_req = pc.getHttpClient().requestAbs(ctx.request().method(),
      makeUrl(ctx, mi), res -> {
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), timer, pc);
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
          makeTraceHeader(mi, res.statusCode(), timer, pc);
          timer.close();
          relayToRequest(res, pc);
          proxyR(it, pc, null, bcontent);
        } else {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), timer, pc);
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
      pc.responseText(500, "connect url " + mi.getUrl() + ": " + res.getMessage());
    });
    c_req.setChunked(true);
    c_req.headers().setAll(ctx.request().headers());
    c_req.end(bcontent);
    log(c_req);
  }

  private void proxyRequestOnly( Iterator<ModuleInstance> it,
    ProxyContext pc,
    ReadStream<Buffer> content, Buffer bcontent,
    ModuleInstance mi, Timer.Context timer) {
    if (bcontent != null) {
      proxyRequestHttpClient(it, pc, bcontent, mi, timer);
    } else {
      final Buffer incoming = Buffer.buffer();
      content.handler(data -> {
        incoming.appendBuffer(data);
      });
      content.endHandler(v -> {
        proxyRequestHttpClient( it, pc, incoming, mi, timer);
        timer.close();
      });
      content.resume();
    }
  }

  private void proxyRequestResponse( Iterator<ModuleInstance> it,
    ProxyContext pc,
    ReadStream<Buffer> content, Buffer bcontent,
    ModuleInstance mi, Timer.Context timer) {
    RoutingContext ctx = pc.getCtx();
    HttpClientRequest c_req = pc.getHttpClient().requestAbs(ctx.request().method(),
      makeUrl(ctx, mi), res -> {
        if (res.statusCode() >= 200 && res.statusCode() < 300
        && res.getHeader(XOkapiHeaders.STOP) == null
        && it.hasNext()) {
          makeTraceHeader(mi, res.statusCode(), timer, pc);
          relayToRequest(res, pc);
          res.pause();
          proxyR(it, pc, res, null);
        } else {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), timer, pc);
          res.handler(data -> {
            ctx.response().write(data);
          });
          res.endHandler(v -> {
            timer.close();
            ctx.response().end();
          });
          res.exceptionHandler(v -> {
            logger.debug("proxyRequestResponse: res exception " + v.getMessage());
          });
        }
      });
    c_req.exceptionHandler(res -> {
      logger.debug("proxyRequestResponse failure: " + mi.getUrl() + ": " + res.getMessage());
      timer.close();
      pc.responseText(500, "connect url " + mi.getUrl() + ": " + res.getMessage());
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
    log(c_req);
  }

  private void proxyHeaders( Iterator<ModuleInstance> it,
    ProxyContext pc,
    ReadStream<Buffer> content, Buffer bcontent,
    ModuleInstance mi, Timer.Context timer) {
    RoutingContext ctx = pc.getCtx();
    HttpClientRequest c_req = pc.getHttpClient().requestAbs(ctx.request().method(),
      makeUrl(ctx, mi), res -> {
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), timer, pc);
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
          relayToRequest(res, pc);
          res.endHandler(x -> {
            proxyR(it, pc, content, bcontent);
          });
        } else {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), timer, pc);
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
      pc.responseText(500, "connect url " + mi.getUrl() + ": " + res.getMessage());
    });
    c_req.headers().setAll(ctx.request().headers());
    c_req.headers().remove("Content-Length");
    c_req.end();
    log(c_req);
  }

  private void proxyNull(Iterator<ModuleInstance> it,
    ProxyContext pc,
    ReadStream<Buffer> content, Buffer bcontent,
    ModuleInstance mi, Timer.Context timer) {
    RoutingContext ctx = pc.getCtx();
    if (it.hasNext()) {
      timer.close();
      proxyR(it, pc, content, bcontent);
    } else {
      ctx.response().setChunked(true);

      makeTraceHeader(mi, 999, timer, pc);  // !!!
      if (bcontent == null) {
        content.handler(data -> {
          ctx.response().write(data);
        });
        content.endHandler(v -> {
          timer.close();
          ctx.response().end();
        });
        content.exceptionHandler(v -> {
          logger.debug("proxyNull: content exception " + v.getMessage());
        });
        content.resume();
      } else {
        timer.close();
        ctx.response().end(bcontent);
      }
    }
  }

  private void proxyR( Iterator<ModuleInstance> it,
    ProxyContext pc,
    ReadStream<Buffer> content, Buffer bcontent) {
    RoutingContext ctx = pc.getCtx();
    if (!it.hasNext()) {
      content.resume();
      pc.addTraceHeaders(ctx);
      logger.debug("proxyR: Not found");
      pc.responseText(404, ""); // Should have been caught earlier
    } else {
      ModuleInstance mi = it.next();
      String tenantId = ctx.request().getHeader(XOkapiHeaders.TENANT);
      if (tenantId == null || tenantId.isEmpty()) {
        tenantId = "???"; // Should not happen, we have validated earlier
      }
      String metricKey = "proxy." + tenantId + ".module." + mi.getModuleDescriptor().getId();
      Timer.Context timerContext = DropwizardHelper.getTimerContext(metricKey);

      ctx.request().headers().remove(XOkapiHeaders.TOKEN);
      String token = mi.getAuthToken();
      if (token != null && !token.isEmpty()) {
        ctx.request().headers().add(XOkapiHeaders.TOKEN, token);
      }
      ProxyType pType = mi.getRoutingEntry().getProxyType();
      if (pType != ProxyType.REDIRECT) {
        logger.debug("Invoking module " + mi.getModuleDescriptor().getNameOrId()
          + " type " + pType
          + " level " + mi.getRoutingEntry().getPhaseLevel()
          + " path " + mi.getUri()
          + " url " + mi.getUrl());
      }
      if (pType == ProxyType.REQUEST_ONLY) {
        proxyRequestOnly(it, pc,
          content, bcontent, mi, timerContext);
      } else if (pType == ProxyType.REQUEST_RESPONSE) {
        proxyRequestResponse( it, pc,
          content, bcontent, mi, timerContext);
      } else if (pType == ProxyType.HEADERS) {
        proxyHeaders(it, pc,
          content, bcontent, mi, timerContext);
      } else if (pType == ProxyType.REDIRECT) {
        proxyNull(it, pc,
          content, bcontent, mi, timerContext);
      } else {
        logger.warn("proxyR: Module " + mi.getModuleDescriptor().getNameOrId()
          + " has bad request type: '" + pType + "'");
        timerContext.close();
        pc.responseText(500, ""); // Should not happen
      }
    }
  }

} // class
