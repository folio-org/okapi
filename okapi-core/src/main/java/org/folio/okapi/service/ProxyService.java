package org.folio.okapi.service;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.Tenant;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
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
import static org.folio.okapi.common.ErrorType.INTERNAL;
import org.folio.okapi.discovery.DiscoveryManager;
import org.folio.okapi.util.DropwizardHelper;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import static org.folio.okapi.common.ErrorType.USER;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.OkapiClient;
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
   * @param mi
   * @param statusCode
   * @param pc
   */
  private void makeTraceHeader(ModuleInstance mi, int statusCode,
    ProxyContext pc) {
    RoutingContext ctx = pc.getCtx();
    String url = makeUrl(ctx, mi).replaceFirst("[?#].*$", ".."); // rm params
    pc.addTraceHeaderLine(ctx.request().method() + " "
      + mi.getModuleDescriptor().getNameOrId() + " "
      + url + " : " + statusCode + pc.timeDiff());
    pc.addTraceHeaders(ctx);
    pc.logResponse(mi.getModuleDescriptor().getNameOrId(), url, statusCode);
  }


  private boolean match(RoutingEntry e, HttpServerRequest req) {
    return e.match(req.uri(), req.method().name());
  }

  private boolean resolveRedirects(ProxyContext pc,
    List<ModuleInstance> mods,
    RoutingEntry re,
    ModuleDescriptor md,
    List<ModuleDescriptor> enabledModules,
    final String loop, final String uri, final String origMod) {
    RoutingContext ctx = pc.getCtx();
    // add the module to the pipeline in any case
    ModuleInstance mi = new ModuleInstance(md, re, uri);
    mods.add(mi);
    if (re.getProxyType() == ProxyType.REDIRECT) { // resolve redirects
      boolean found = false;
      final String redirectPath = re.getRedirectPath();
      for (ModuleDescriptor trymod : enabledModules) {
        List<RoutingEntry> rr = trymod.getProxyRoutingEntries();
        for (RoutingEntry tryre : rr) {
          if (tryre.match(redirectPath, ctx.request().method().name())) {
            final String newUri = re.getRedirectUri(uri);
            found = true;
            pc.debug("resolveRedirects: "
              + ctx.request().method() + " " + uri
              + " => " + trymod + " " + newUri);
            if (loop.contains(redirectPath + " ")) {
              pc.responseError(500, "Redirect loop: " + loop + " -> " + redirectPath);
              return false;
            }
            if (!resolveRedirects(pc, mods, tryre, trymod, enabledModules,
              loop + " -> " + redirectPath, newUri, origMod)) {
              return false;
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
   * @param enabledModules modules enabled for the current tenant
   * @return a list of ModuleInstances. In case of error, sets up ctx and
   * returns null.
   */
  public List<ModuleInstance> getModulesForRequest(ProxyContext pc,
    List<ModuleDescriptor> enabledModules) {
    List<ModuleInstance> mods = new ArrayList<>();
    HttpServerRequest req = pc.getCtx().request();
    final String id = req.getHeader(XOkapiHeaders.MODULE_ID);
    pc.debug("getMods: Matching " + req.method() + " " + req.absoluteURI());

    for (ModuleDescriptor md : enabledModules) {
      pc.debug("getMods:  looking at " + md.getNameOrId());
      List<RoutingEntry> rr = md.getProxyRoutingEntries();
      if (id == null) {
        for (RoutingEntry re : rr) {
          if (match(re, req)) {
            if (!resolveRedirects(pc, mods, re, md, enabledModules, "", req.uri(), "")) {
              return null;
            }
            pc.debug("getMods:   Added " + md.getId() + " "
              + re.getPathPattern() + " " + re.getPath());
          }
        }
      } else if (id.equals(md.getId())) {
        List<RoutingEntry> rr1 = md.getMultiRoutingEntries();
        for (RoutingEntry re : rr1) {
          if (match(re, req)) {
            if (!resolveRedirects(pc, mods, re, md, enabledModules, "", req.uri(), "")) {
              return null;
            }
            pc.debug("getMods:   Added " + md.getId() + " "
              + re.getPathPattern() + " " + re.getPath());
          }
        }
      }
    }
    Comparator<ModuleInstance> cmp = (ModuleInstance a, ModuleInstance b)
      -> a.getRoutingEntry().getPhaseLevel().compareTo(b.getRoutingEntry().getPhaseLevel());
    mods.sort(cmp);

    // Check that our pipeline has a real module in it, not just filters,
    // so that we can return a proper 404 for requests that only hit auth
    pc.debug("Checking filters for " + req.absoluteURI());
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
      pc.debug("Okapi: Moved Authorization header to X-Okapi-Token");
    }

    String tenantId = ctx.request().getHeader(XOkapiHeaders.TENANT);
    if (tenantId == null) {
      tenantId = new OkapiToken(ctx).getTenant();
      if (tenantId != null && !tenantId.isEmpty()) {
        ctx.request().headers().add(XOkapiHeaders.TENANT, tenantId);
        pc.debug("Okapi: Recovered tenant from token: '" + tenantId + "'");
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
    MultiMap requestHeaders, String defaultToken, ProxyContext pc) {
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
      pc.debug("authHeaders: " + XOkapiHeaders.PERMISSIONS_REQUIRED + " " + String.join(",", req));
      requestHeaders.add(XOkapiHeaders.PERMISSIONS_REQUIRED, String.join(",", req));
    }
    if (!want.isEmpty()) {
      pc.debug("authHeaders: " + XOkapiHeaders.PERMISSIONS_DESIRED + " " + String.join(",", want));
      requestHeaders.add(XOkapiHeaders.PERMISSIONS_DESIRED, String.join(",", want));
    }
    // Add the X-Okapi-Module-Permissions even if empty. That causes auth to return
    // an empty X-Okapi-Module-Token, which will tell us that we have done the mod
    // perms, and no other module should be allowed to do the same.
    String mpj = Json.encode(modperms);
    pc.debug("authHeaders: " + XOkapiHeaders.MODULE_PERMISSIONS + " " + mpj);
    requestHeaders.add(XOkapiHeaders.MODULE_PERMISSIONS, mpj);
    if (!extraperms.isEmpty()) {
      String epj = Json.encode(extraperms);
      pc.debug("authHeaders: " + XOkapiHeaders.EXTRA_PERMISSIONS + " " + epj);
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
          pc.debug("authResponse: token for " + id + ": " + tok);
        } else if (jo.containsKey("_")) {
          String tok = jo.getString("_");
          mi.setAuthToken(tok);
          pc.debug("authResponse: Default (_) token for " + id + ": " + tok);
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

  private void log(ProxyContext pc, HttpClientRequest creq) {
    pc.debug(creq.method().name() + " " + creq.absoluteURI());
    Iterator<Map.Entry<String, String>> iterator = creq.headers().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, String> next = iterator.next();
      pc.debug(" " + next.getKey() + ":" + next.getValue());
    }
  }

  private String makeUrl(RoutingContext ctx, ModuleInstance mi) {
    return mi.getUrl() + mi.getUri();
  }


  public void proxy(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(vertx, ctx);
    String tenant_id = tenantHeader(pc);
    if (tenant_id == null) {
      return; // Error code already set in ctx
    }
    ReadStream<Buffer> content = ctx.request();
    // Pause the request data stream before doing any slow ops, otherwise
    // it will get read into a buffer somewhere.
    content.pause();

    tenantManager.get(tenant_id, gres -> {
      if (gres.failed()) {
        content.resume();
        pc.responseText(400, "No such Tenant " + tenant_id);
        return;
      }
      Tenant tenant = gres.result();
      modules.getEnabledModules(tenant, mres -> {
        if (mres.failed()) {
          content.resume();
          pc.responseError(mres.getType(), mres.cause());
          return;
        }
        List<ModuleDescriptor> enabledModules = mres.result();

        String metricKey = "proxy." + tenant_id + "."
          + ctx.request().method() + "." + ctx.normalisedPath();
        DropwizardHelper.markEvent(metricKey);
        String authToken = ctx.request().getHeader(XOkapiHeaders.TOKEN);

        List<ModuleInstance> l = getModulesForRequest(pc, enabledModules);
        if (l == null) {
            content.resume();
          return; // ctx already set up
        }
        pc.setModList(l);

        pc.logRequest(ctx, tenant_id);

        ctx.request().headers().add(XOkapiHeaders.URL, okapiUrl);
        authHeaders(l, ctx.request().headers(), authToken, pc);

        resolveUrls(l.iterator(), res -> {
          if (res.failed()) {
            content.resume();
            pc.responseError(res.getType(), res.cause());
          } else {
            proxyR(l.iterator(), pc, content, null);
          }
        });
      });

    });
  }

  private void proxyRequestHttpClient( Iterator<ModuleInstance> it,
    ProxyContext pc, Buffer bcontent, ModuleInstance mi) {
    RoutingContext ctx = pc.getCtx();
    String url = makeUrl(ctx, mi);
    HttpMethod meth = ctx.request().method();
    HttpClientRequest c_req = pc.getHttpClient().requestAbs(meth, url, res -> {
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), pc);
          res.handler(data -> {
            ctx.response().write(data);
            pc.trace("ProxyRequestHttpClient response chunk '"
              + data.toString() + "'");
          });
          res.endHandler(x -> {
            pc.closeTimer();
            ctx.response().end();
            pc.trace("ProxyRequestHttpClient response end");
          });
          res.exceptionHandler(e -> {
            pc.warn("proxyRequestHttpClient: res exception (a)", e);
          });
        } else if (it.hasNext()) {
          makeTraceHeader(mi, res.statusCode(), pc);
          pc.closeTimer();
          relayToRequest(res, pc);
          proxyR(it, pc, null, bcontent);
        } else {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), pc);
          res.endHandler(x -> {
            pc.closeTimer();
            pc.trace("ProxyRequestHttpClient final response buf '"
              + bcontent + "'");
            ctx.response().end(bcontent);
          });
          res.exceptionHandler(e -> {
            pc.warn("proxyRequestHttpClient: res exception (b)", e);
          });
        }
      });
    c_req.exceptionHandler(e -> {
      pc.warn("proxyRequestHttpClient failure: " + url, e);
      pc.responseText(500, "proxyRequestHttpClient failure: "
        + mi.getModuleDescriptor().getNameOrId() + " "
        + meth + " " + url + ": " + e + " " + e.getMessage());
    });
    c_req.setChunked(true);
    c_req.headers().setAll(ctx.request().headers());
    pc.trace("ProxyRequestHttpClient request buf '"
      + bcontent + "'");
    c_req.end(bcontent);
    log(pc, c_req);
  }

  private void proxyRequestOnly( Iterator<ModuleInstance> it,
    ProxyContext pc,
    ReadStream<Buffer> content, Buffer bcontent,
    ModuleInstance mi) {
    if (bcontent != null) {
      proxyRequestHttpClient(it, pc, bcontent, mi);
    } else {
      final Buffer incoming = Buffer.buffer();
      content.handler(data -> {
        incoming.appendBuffer(data);
        pc.trace("ProxyRequestOnly request chunk '"
          + data.toString() + "'");
      });
      content.endHandler(v -> {
        pc.trace("ProxyRequestOnly request end");
        proxyRequestHttpClient( it, pc, incoming, mi);
        pc.closeTimer();
      });
      content.resume();
    }
  }

  private void proxyRequestResponse( Iterator<ModuleInstance> it,
    ProxyContext pc,
    ReadStream<Buffer> content, Buffer bcontent,
    ModuleInstance mi) {
    RoutingContext ctx = pc.getCtx();
    HttpClientRequest c_req = pc.getHttpClient().requestAbs(ctx.request().method(),
      makeUrl(ctx, mi), res -> {
        if (res.statusCode() >= 200 && res.statusCode() < 300
        && res.getHeader(XOkapiHeaders.STOP) == null
        && it.hasNext()) {
          makeTraceHeader(mi, res.statusCode(), pc);
          relayToRequest(res, pc);
          res.pause();
          proxyR(it, pc, res, null);
        } else {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), pc);
          res.handler(data -> {
            ctx.response().write(data);
            pc.trace("ProxyRequestResponse response chunk '"
              + data.toString() + "'");
          });
          res.endHandler(v -> {
            pc.closeTimer();
            ctx.response().end();
            pc.trace("ProxyRequestResponse response end");
          });
          res.exceptionHandler(e -> {
            pc.warn("proxyRequestResponse: res exception ", e);
          });
        }
      });
    c_req.exceptionHandler(e -> {
      pc.warn("proxyRequestResponse failure: ", e);
      pc.responseText(500, "proxyRequestResponse failure: " + mi.getUrl() + ": "
        + e + " " + e.getMessage());
    });
    c_req.setChunked(true);
    c_req.headers().setAll(ctx.request().headers());
    if (bcontent != null) {
      pc.trace("proxyRequestResponse request buf '" + bcontent + "'");
      c_req.end(bcontent);
    } else {
      content.handler(data -> {
        pc.trace("proxyRequestResponse request chunk '"
          + data.toString() + "'");
        c_req.write(data);
      });
      content.endHandler(v -> {
        pc.trace("proxyRequestResponse request complete");
        c_req.end();
      });
      content.exceptionHandler(e -> {
        pc.warn("proxyRequestResponse: content exception ", e);
      });
      content.resume();
    }
    log(pc, c_req);
  }

  private void proxyHeaders( Iterator<ModuleInstance> it,
    ProxyContext pc,
    ReadStream<Buffer> content, Buffer bcontent,
    ModuleInstance mi) {
    RoutingContext ctx = pc.getCtx();
    HttpClientRequest c_req = pc.getHttpClient().requestAbs(ctx.request().method(),
      makeUrl(ctx, mi), res -> {
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), pc);
          res.handler(data -> {
            ctx.response().write(data);
            pc.trace("ProxyHeaders response chunk '"
              + data.toString() + "'");
          });
          res.endHandler(v -> {
            ctx.response().end();
            pc.trace("ProxyHeaders response end");
          });
          res.exceptionHandler(e -> {
            pc.warn("proxyHeaders: res exception ", e);
          });
        } else if (it.hasNext()) {
          relayToRequest(res, pc);
          makeTraceHeader(mi, res.statusCode(), pc);
          res.endHandler(x -> {
            proxyR(it, pc, content, bcontent);
          });
        } else {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), pc);
          if (bcontent == null) {
            content.handler(data -> {
              ctx.response().write(data);
              pc.trace("ProxyHeaders request chunk '"
                + data.toString() + "'");
            });
            content.endHandler(v -> {
              ctx.response().end();
              pc.trace("ProxyHeaders request end");
            });
            content.exceptionHandler(e -> {
              pc.warn("proxyHeaders: content exception ", e);
            });
            content.resume();
          } else {
            pc.trace("ProxyHeaders request buf '" + bcontent + "'");
            ctx.response().end(bcontent);
          }
        }
      });
    c_req.exceptionHandler(e -> {
      pc.warn("proxyHeaders failure: " + mi.getUrl() + ": ", e);
      pc.responseText(500, "proxyHeaders failure. connect url "
        + mi.getUrl() + ": " + e + " " + e.getMessage());
    });
    c_req.headers().setAll(ctx.request().headers());
    c_req.headers().remove("Content-Length");
    c_req.end();
    log(pc, c_req);
  }

  private void proxyNull(Iterator<ModuleInstance> it,
    ProxyContext pc,
    ReadStream<Buffer> content, Buffer bcontent,
    ModuleInstance mi) {
    RoutingContext ctx = pc.getCtx();
    if (it.hasNext()) {
      pc.closeTimer();
      proxyR(it, pc, content, bcontent);
    } else {
      ctx.response().setChunked(true);

      makeTraceHeader(mi, 999, pc);  // !!!
      if (bcontent == null) {
        content.handler(data -> {
          pc.trace("ProxyNull response chunk '"
            + data.toString() + "'");
          ctx.response().write(data);
        });
        content.endHandler(v -> {
          pc.closeTimer();
          pc.trace("ProxyNull response end");
          ctx.response().end();
        });
        content.exceptionHandler(e -> {
          pc.warn("proxyNull: content exception ", e);
        });
        content.resume();
      } else {
        pc.closeTimer();
        pc.trace("ProxyNull response buf '" + bcontent + "'");
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
      pc.debug("proxyR: Not found");
      pc.responseText(404, ""); // Should have been caught earlier
    } else {
      ModuleInstance mi = it.next();
      String tenantId = ctx.request().getHeader(XOkapiHeaders.TENANT);
      if (tenantId == null || tenantId.isEmpty()) {
        tenantId = "???"; // Should not happen, we have validated earlier
      }
      String metricKey = "proxy." + tenantId
        + ".module." + mi.getModuleDescriptor().getId();
      pc.startTimer(metricKey);

      ctx.request().headers().remove(XOkapiHeaders.TOKEN);
      String token = mi.getAuthToken();
      if (token != null && !token.isEmpty()) {
        ctx.request().headers().add(XOkapiHeaders.TOKEN, token);
      }
      ProxyType pType = mi.getRoutingEntry().getProxyType();
      if (pType != ProxyType.REDIRECT) {
        pc.debug("Invoking module " + mi.getModuleDescriptor().getNameOrId()
          + " type " + pType
          + " level " + mi.getRoutingEntry().getPhaseLevel()
          + " path " + mi.getUri()
          + " url " + mi.getUrl());
      }
      if (pType == ProxyType.REQUEST_ONLY) {
        proxyRequestOnly(it, pc,
          content, bcontent, mi);
      } else if (pType == ProxyType.REQUEST_RESPONSE) {
        proxyRequestResponse( it, pc,
          content, bcontent, mi);
      } else if (pType == ProxyType.HEADERS) {
        proxyHeaders(it, pc,
          content, bcontent, mi);
      } else if (pType == ProxyType.REDIRECT) {
        proxyNull(it, pc,
          content, bcontent, mi);
      } else {
        pc.warn("proxyR: Module " + mi.getModuleDescriptor().getNameOrId()
          + " has bad request type: '" + pType + "'");
        pc.responseText(500, ""); // Should not happen
      }
    }
  }

  /**
   * Make a request to a system interface, like _tenant.
   *
   * @param tenantId to make the request for
   * @param module id of the module to invoke
   * @param path of the system service
   * @param request body to send in the request
   * @param pc ProxyContext for logging, and returning resp headers
   * @param fut Callback with the response body, or various errors
   */
  public void callSystemInterface(String tenantId, String module, String path,
    String request, ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {

    discoveryManager.get(module, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      List<DeploymentDescriptor> instances = gres.result();
      if (instances.isEmpty()) {
        fut.handle(new Failure<>(USER, "No running instances for module "
          + module + ". Can not invoke " + path));
        return;
      }
      // TODO - Don't just take the first. Pick one by random.
      String baseurl = instances.get(0).getUrl();
      pc.debug("callSystemInterface Url: " + baseurl + " and " + path);
      Map<String, String> headers = sysReqHeaders(pc.getCtx(), tenantId);
      OkapiClient cli = new OkapiClient(baseurl, vertx, headers);
      cli.newReqId("tenant");
      cli.enableInfoLog();
      cli.request(HttpMethod.POST, path, request, cres -> {
        if (cres.failed()) {
          String msg = "Request for "
            + module + " " + path
            + " failed with " + cres.cause().getMessage();
          pc.warn(msg);
          fut.handle(new Failure<>(INTERNAL, msg));
          return;
        }
        // Pass response headers - needed for unit test, if nothing else
        pc.debug("Request for " + module + " " + path + " ok");
        if (pc.getCtx() != null) {
          MultiMap respHeaders = cli.getRespHeaders();
          if (respHeaders != null) {
            for (String hdr : respHeaders.names()) {
              if (hdr.matches("^X-.*$")) {
                pc.getCtx().response().headers().add(hdr, respHeaders.get(hdr));
                pc.debug("callSystemInterface: response header "
                  + hdr + " " + respHeaders.get(hdr));
              }
            }
          }
        }
        fut.handle(new Success<>(cli.getResponsebody()));
      });
    });
  }

  /**
   * Helper to make request headers for the system requests we make.
   */
  private Map<String, String> sysReqHeaders(RoutingContext ctx, String tenantId) {
    Map<String, String> headers = new HashMap<>();
    for (String hdr : ctx.request().headers().names()) {
      if (hdr.matches("^X-.*$")) {
        headers.put(hdr, ctx.request().headers().get(hdr));
      }
    }
    if (!headers.containsKey(XOkapiHeaders.TENANT)) {
      headers.put(XOkapiHeaders.TENANT, tenantId);
      logger.debug("Added " + XOkapiHeaders.TENANT + " : " + tenantId);
    }
    headers.put("Accept", "*/*");
    headers.put("Content-Type", "application/json; charset=UTF-8");
    return headers;
  }

  /**
   * Extract tenantId from the request, rewrite the path, and proxy it. Expects
   * a request to something like /_/proxy/tenant/{tid}/service/mod-something.
   * Rewrites that to /mod-something, with the tenantId passed in the proper
   * header. As there is no authtoken, this will not work for many things, but
   * is needed for callbacks in the SSO systems, and who knows what else.
   *
   * @param ctx
   */
  public void redirectProxy(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(vertx, ctx);
    final String origPath = ctx.request().path();
    String tid = origPath
      .replaceFirst("^/_/invoke/tenant/([^/ ]+)/.*$", "$1");
    String newPath = origPath
      .replaceFirst("^/_/invoke/tenant/[^/ ]+(/.*$)", "$1");
    ctx.request().headers().add(XOkapiHeaders.TENANT, tid);
    pc.debug("redirectProxy: '" + tid + "' '" + newPath + "'");
    ctx.reroute(newPath);
  }

} // class
