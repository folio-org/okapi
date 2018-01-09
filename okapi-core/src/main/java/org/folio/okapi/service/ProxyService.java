package org.folio.okapi.service;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.Tenant;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
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
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.OkapiToken;
import org.folio.okapi.util.ProxyContext;
import org.folio.okapi.web.InternalModule;

/**
 * Okapi's proxy service. Routes incoming requests to relevant modules, as
 * enabled for the current tenant.
 */
// S1168: Empty arrays and collections should be returned instead of null
// S1192: String literals should not be duplicated
@java.lang.SuppressWarnings({"squid:S1168", "squid:S1192"})
public class ProxyService {

  private final Logger logger = OkapiLogger.get();

  private final ModuleManager moduleManager;
  private final TenantManager tenantManager;
  private final DiscoveryManager discoveryManager;
  private final InternalModule internalModule;
  private final String okapiUrl;
  private final Vertx vertx;
  private final HttpClient httpClient;

  public ProxyService(Vertx vertx, ModuleManager modules, TenantManager tm,
    DiscoveryManager dm, InternalModule im, String okapiUrl) {
    this.vertx = vertx;
    this.moduleManager = modules;
    this.tenantManager = tm;
    this.internalModule = im;
    this.discoveryManager = dm;
    this.okapiUrl = okapiUrl;
    HttpClientOptions opt = new HttpClientOptions();
    opt.setMaxPoolSize(1000);
    httpClient = vertx.createHttpClient(opt);
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
    String url = makeUrl(mi).replaceFirst("[?#].*$", ".."); // rm params
    pc.addTraceHeaderLine(ctx.request().method() + " "
      + mi.getModuleDescriptor().getId() + " "
      + url + " : " + statusCode + pc.timeDiff());
    pc.logResponse(mi.getModuleDescriptor().getId(), url, statusCode);
  }


  private boolean match(RoutingEntry e, HttpServerRequest req) {
    return e.match(req.uri(), req.method().name());
  }

  private boolean resolveRedirects(ProxyContext pc,
    List<ModuleInstance> mods,
    RoutingEntry re,
    List<ModuleDescriptor> enabledModules,
    final String loop, final String uri, final String origMod) {

    RoutingContext ctx = pc.getCtx();
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
            ModuleInstance mi = new ModuleInstance(trymod, tryre, newUri);
            mods.add(mi);
            if (!resolveRedirects(pc, mods, tryre, enabledModules,
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
  private List<ModuleInstance> getModulesForRequest(ProxyContext pc,
                                                    List<ModuleDescriptor> enabledModules) {
    List<ModuleInstance> mods = new ArrayList<>();
    HttpServerRequest req = pc.getCtx().request();
    final String id = req.getHeader(XOkapiHeaders.MODULE_ID);
    pc.debug("getMods: Matching " + req.method() + " " + req.absoluteURI());

    for (ModuleDescriptor md : enabledModules) {
      pc.debug("getMods:  looking at " + md.getId());
      List<RoutingEntry> rr = null;
      if (id == null) {
        rr = md.getProxyRoutingEntries();
      } else if (id.equals(md.getId())) {
        rr = md.getMultiRoutingEntries();
      }
      if (rr != null) {
        for (RoutingEntry re : rr) {
          if (match(re, req)) {
            ModuleInstance mi = new ModuleInstance(md, re, req.uri());
            mods.add(mi);
            if (!resolveRedirects(pc, mods, re, enabledModules, "", req.uri(), "")) {
              return null;
            }
            pc.debug("getMods:   Added " + md.getId() + " "
              + re.getPathPattern() + " " + re.getPath() + " " + re.getPhase() + "/" + re.getLevel());
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
      pc.debug("getMods: Checking " + inst.getRoutingEntry().getPathPattern() + " "
        + "'" + inst.getRoutingEntry().getPhase() + "' "
        + "'" + inst.getRoutingEntry().getLevel() + "' "
      );
      if (inst.getRoutingEntry().getPhase() == null) {
        found = true; // No real handler should have a phase any more.
        // It has been deprecated for a long time, and never made any sense anyway.
        // The auth filter, the only one we have, uses phase 'auth'
      }
    }
    if (!found) {
      if ("-".equals(pc.getTenant()) // If we defaulted to supertenant,
        && !req.path().startsWith("/_/")  ) {  // and not wrong okapi request
           // The /_/ test is to make sure we report same errors as before internalModule stuff
        pc.responseText(403, "Missing Tenant");
        return null;
      } else {
        pc.responseError(404, "No suitable module found for path " + req.path());
        return null;
      }
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
   * @param pc
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
      try {
        tenantId = new OkapiToken(ctx).getTenant();
        if (tenantId != null && !tenantId.isEmpty()) {
          ctx.request().headers().add(XOkapiHeaders.TENANT, tenantId);
          pc.debug("Okapi: Recovered tenant from token: '" + tenantId + "'");
        }
      } catch (IllegalArgumentException e) {
        pc.responseText(400, "Invalid Token: " + e.getMessage());
        return null;
      }
    }
    if (tenantId == null) {
      logger.debug("No tenantId, defaulting to " + XOkapiHeaders.SUPERTENANT_ID);
      return XOkapiHeaders.SUPERTENANT_ID; // without setting it in pc
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
      if (mi.getRoutingEntry().getProxyType() == ProxyType.INTERNAL) {
        mi.setUrl("");
        resolveUrls(it, fut);
        return;
      }
      discoveryManager.get(mi.getModuleDescriptor().getId(), res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          List<DeploymentDescriptor> l = res.result();
          if (l.isEmpty()) {
            fut.handle(new Failure<>(NOT_FOUND,
              "No running module instance found for "
              + mi.getModuleDescriptor().getId()));
            return;
          }
          mi.setUrl(l.get(0).getUrl());
          // Okapi-435 Don't just take the first!
          resolveUrls(it, fut);
        }
      });
    }
  }

  private void relayToResponse(HttpServerResponse hres, HttpClientResponse res) {
    hres.setStatusCode(res.statusCode());
    hres.headers().addAll(res.headers());
    hres.headers().remove("Content-Length");
    hres.headers().remove("Transfer-Encoding");
    hres.setChunked(res.statusCode() != 204);
  }

  /**
   * Process the auth module response. Set tokens for those modules that
   * received one.
   */
  private void authResponse(HttpClientResponse res, ProxyContext pc) {
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

  private void relayToRequest(HttpClientResponse res, ProxyContext pc) {
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
    for (Map.Entry<String, String> next : creq.headers()) {
      pc.debug(" " + next.getKey() + ":" + next.getValue());
    }
  }

  private String makeUrl(ModuleInstance mi) {
    return mi.getUrl() + mi.getUri();
  }


  public void proxy(RoutingContext ctx) {
    ctx.request().pause();
    ReadStream<Buffer> stream = ctx.request();
    // Pause the request data stream before doing any slow ops, otherwise
    // it will get read into a buffer somewhere.

    ProxyContext pc = new ProxyContext(ctx);

    String tenantId = tenantHeader(pc);
    if (tenantId == null) {
      stream.resume();
      return; // Error code already set in ctx
    }

    tenantManager.get(tenantId, gres -> {
      if (gres.failed()) {
        stream.resume();
        pc.responseText(400, "No such Tenant " + tenantId);
        return;
      }
      Tenant tenant = gres.result();
      moduleManager.getEnabledModules(tenant, mres -> {
        if (mres.failed()) {
          stream.resume();
          pc.responseError(mres.getType(), mres.cause());
          return;
        }
        List<ModuleDescriptor> enabledModules = mres.result();

        String metricKey = "proxy." + tenantId + "."
          + ctx.request().method() + "." + ctx.normalisedPath();
        DropwizardHelper.markEvent(metricKey);
        String authToken = ctx.request().getHeader(XOkapiHeaders.TOKEN);

        List<ModuleInstance> l = getModulesForRequest(pc, enabledModules);
        if (l == null) {
          stream.resume();
          return; // ctx already set up
        }
        pc.setModList(l);

        pc.logRequest(ctx, tenantId);

        ctx.request().headers().add(XOkapiHeaders.URL, okapiUrl);
        ctx.request().headers().remove(XOkapiHeaders.MODULE_ID);
        authHeaders(l, ctx.request().headers(), authToken, pc);

        resolveUrls(l.iterator(), res -> {
          if (res.failed()) {
            stream.resume();
            pc.responseError(res.getType(), res.cause());
          } else {
            proxyR(l.iterator(), pc, stream, null);
          }
        });
      });

    });
  }

  private void proxyRequestHttpClient(Iterator<ModuleInstance> it,
    ProxyContext pc, Buffer bcontent, ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    String url = makeUrl(mi);
    HttpMethod meth = ctx.request().method();
    HttpClientRequest cReq = httpClient.requestAbs(meth, url, res -> {
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
          res.exceptionHandler(e
            -> pc.warn("proxyRequestHttpClient: res exception (a)", e));
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
          res.exceptionHandler(e
            -> pc.warn("proxyRequestHttpClient: res exception (b)", e));
        }
      });
    cReq.exceptionHandler(e -> {
      pc.warn("proxyRequestHttpClient failure: " + url, e);
      pc.responseText(500, "proxyRequestHttpClient failure: "
        + mi.getModuleDescriptor().getId() + " " + mi.getUrl() + ": "
        + e + " " + e.getMessage());
    });
    cReq.headers().setAll(ctx.request().headers());
    cReq.headers().remove("Content-Length");
    pc.trace("ProxyRequestHttpClient request buf '"
      + bcontent + "'");
    cReq.end(bcontent);
    log(pc, cReq);
  }

  private void proxyRequestOnly(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    ModuleInstance mi) {

    if (bcontent != null) {
      proxyRequestHttpClient(it, pc, bcontent, mi);
    } else {
      final Buffer incoming = Buffer.buffer();
      stream.handler(data -> {
        incoming.appendBuffer(data);
        pc.trace("ProxyRequestOnly request chunk '"
          + data.toString() + "'");
      });
      stream.endHandler(v -> {
        pc.trace("ProxyRequestOnly request end");
        proxyRequestHttpClient(it, pc, incoming, mi);
      });
      stream.resume();
    }
  }

  private void proxyRequestResponse10(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    ModuleInstance mi) {

    if (bcontent != null) {
      proxyRequestResponse(it, pc, null, bcontent, mi);
    } else {
      final Buffer incoming = Buffer.buffer();
      stream.handler(data -> {
        incoming.appendBuffer(data);
        pc.trace("ProxyRequestBlock request chunk '"
          + data.toString() + "'");
      });
      stream.endHandler(v -> {
        pc.trace("ProxyRequestBlock request end");
        proxyRequestResponse(it, pc, null, incoming, mi);
      });
      stream.resume();
    }
  }

  private void proxyRequestResponse(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    HttpClientRequest cReq = httpClient.requestAbs(ctx.request().method(),
      makeUrl(mi), res -> {
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
          res.exceptionHandler(e
            -> pc.warn("proxyRequestResponse: res exception ", e));
        }
      });
    cReq.exceptionHandler(e -> {
      pc.warn("proxyRequestResponse failure: ", e);
      pc.responseText(500, "proxyRequestResponse failure: "
        + mi.getModuleDescriptor().getId() + " " + mi.getUrl() + ": "
        + e + " " + e.getMessage());
    });
    cReq.headers().setAll(ctx.request().headers());
    cReq.headers().remove("Content-Length");
    if (bcontent != null) {
      pc.trace("proxyRequestResponse request buf '" + bcontent + "'");
      cReq.end(bcontent);
    } else {
      cReq.setChunked(true);
      stream.handler(data -> {
        pc.trace("proxyRequestResponse request chunk '"
          + data.toString() + "'");
        cReq.write(data);
      });
      stream.endHandler(v -> {
        pc.trace("proxyRequestResponse request complete");
        cReq.end();
      });
      stream.exceptionHandler(e
        -> pc.warn("proxyRequestResponse: content exception ", e));
      stream.resume();
    }
    log(pc, cReq);
  }

  private void proxyHeaders(Iterator<ModuleInstance> it, ProxyContext pc,
    ReadStream<Buffer> stream, Buffer bcontent, ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    HttpClientRequest cReq = httpClient.requestAbs(ctx.request().method(),
      makeUrl(mi), res -> {
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
          res.exceptionHandler(e
            -> pc.warn("proxyHeaders: res exception ", e));
          if (bcontent == null) {
            stream.resume();
          }
        } else if (it.hasNext()) {
          relayToRequest(res, pc);
          makeTraceHeader(mi, res.statusCode(), pc);
          res.endHandler(x
            -> proxyR(it, pc, stream, bcontent));
        } else {
          relayToResponse(ctx.response(), res);
          makeTraceHeader(mi, res.statusCode(), pc);
          if (bcontent == null) {
            stream.handler(data -> {
              ctx.response().write(data);
              pc.trace("ProxyHeaders request chunk '"
                + data.toString() + "'");
            });
            stream.endHandler(v -> {
              ctx.response().end();
              pc.trace("ProxyHeaders request end");
            });
            stream.exceptionHandler(e
              -> pc.warn("proxyHeaders: content exception ", e));
            stream.resume();
          } else {
            pc.trace("ProxyHeaders request buf '" + bcontent + "'");
            ctx.response().end(bcontent);
          }
        }
      });
    cReq.exceptionHandler(e -> {
      pc.warn("proxyHeaders failure: " + mi.getUrl() + ": ", e);
      pc.responseText(500, "proxyHeaders failure: "
        + mi.getModuleDescriptor().getId() + " " + mi.getUrl() + ": "
        + e + " " + e.getMessage());
    });
    cReq.headers().setAll(ctx.request().headers());
    cReq.headers().remove("Content-Length");
    cReq.end();
    log(pc, cReq);
  }

  private void proxyRedirect(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    pc.trace("ProxyNull " + mi.getModuleDescriptor().getId());
    pc.closeTimer();
    // if nore more intries in it, proxyR will return 404
    proxyR(it, pc, stream, bcontent);
  }

  private void proxyInternal(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    ModuleInstance mi) {

    pc.debug("proxyInternal " + mi.getModuleDescriptor().getId());
    if (bcontent != null) {
      proxyInternalBuffer(it, pc, bcontent, mi);
    } else { // read the whole request into a buffer
      final Buffer incoming = Buffer.buffer();
      stream.handler(data -> {
        incoming.appendBuffer(data);
        pc.trace("proxyInternal request chunk '"
          + data.toString() + "'");
      });
      stream.endHandler(v -> {
        pc.trace("proxyInternal request end");
        proxyInternalBuffer(it, pc, incoming, mi);
      });
      stream.resume();
    }
  }

  private void proxyInternalBuffer(Iterator<ModuleInstance> it,
    ProxyContext pc, Buffer bcontent, ModuleInstance mi) {

    String req = bcontent.toString();
    pc.debug("proxyInternalBuffer " + req);
    RoutingContext ctx = pc.getCtx();
    internalModule.internalService(req, pc, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
        return;
      }
      String resp = res.result();
      int statusCode = pc.getCtx().response().getStatusCode();
      if (statusCode == 200 && resp.isEmpty()) {
        // Say "no content", if there isn't any
        pc.getCtx().response().setStatusCode(204);
      }
      Buffer respBuf = Buffer.buffer(resp);
      if (it.hasNext()) { // carry on with the pipeline
        pc.closeTimer();
        proxyR(it, pc, null, respBuf);
      } else { // produce a result
        makeTraceHeader(mi, statusCode, pc);
        ctx.response().setChunked(false);
        ctx.response().end(respBuf);
      }
    });
  }


  private void proxyR(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent) {

    RoutingContext ctx = pc.getCtx();
    if (!it.hasNext()) {
      stream.resume();
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

      // Pass the right token
      ctx.request().headers().remove(XOkapiHeaders.TOKEN);
      String token = mi.getAuthToken();
      if (token != null && !token.isEmpty()) {
        ctx.request().headers().add(XOkapiHeaders.TOKEN, token);
      }

      // Pass the X-Okapi-Filter header for filters (only)
      ctx.request().headers().remove(XOkapiHeaders.FILTER);
      if (mi.getRoutingEntry().getPhase() != null) {
        String pth = mi.getRoutingEntry().getPathPattern();
        if (pth == null) {
          pth = mi.getRoutingEntry().getPath();
        }
        String filt = mi.getRoutingEntry().getPhase() + " " + pth;
        pc.debug("Adding " + XOkapiHeaders.FILTER + ": " + filt);
        ctx.request().headers().add(XOkapiHeaders.FILTER, filt);
      }

      ProxyType pType = mi.getRoutingEntry().getProxyType();
      if (pType != ProxyType.REDIRECT) {
        pc.debug("Invoking module " + mi.getModuleDescriptor().getId()
          + " type " + pType
          + " level " + mi.getRoutingEntry().getPhaseLevel()
          + " path " + mi.getUri()
          + " url " + mi.getUrl());
      }
      switch (pType) {
        case REQUEST_ONLY:
          proxyRequestOnly(it, pc, stream, bcontent, mi);
          break;
        case REQUEST_RESPONSE:
          proxyRequestResponse(it, pc, stream, bcontent, mi);
          break;
        case HEADERS:
          proxyHeaders(it, pc, stream, bcontent, mi);
          break;
        case REDIRECT:
          proxyRedirect(it, pc, stream, bcontent, mi);
          break;
        case INTERNAL:
          proxyInternal(it, pc, stream, bcontent, mi);
          break;
        case REQUEST_RESPONSE_1_0:
          proxyRequestResponse10(it, pc, stream, bcontent, mi);
          break;
        default:
          // Should not happen
          pc.responseText(500, "Bad proxy type '" + pType
            + "' in module " + mi.getModuleDescriptor().getId());
          break;
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
      // Okapi-435 - Don't just take the first. Pick one by random
      String baseurl = instances.get(0).getUrl();
      pc.debug("callSystemInterface Url: " + baseurl + " and " + path);
      Map<String, String> headers = sysReqHeaders(pc.getCtx(), tenantId);
      OkapiClient cli = new OkapiClient(baseurl, vertx, headers);
      cli.newReqId("tenant");
      cli.enableInfoLog();
      cli.request(HttpMethod.POST, path, request, cres -> {
        cli.close();
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
        pc.passOkapiTraceHeaders(cli);
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
   * a request to something like /_/proxy/tenant/{tid}/mod-something.
   * Rewrites that to /mod-something, with the tenantId passed in the proper
   * header. As there is no authtoken, this will not work for many things, but
   * is needed for callbacks in the SSO systems, and who knows what else.
   *
   * @param ctx
   */
  public void redirectProxy(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx);
    final String origPath = ctx.request().path();
    String qry = ctx.request().query();
    String tid = origPath
      .replaceFirst("^/_/invoke/tenant/([^/ ]+)/.*$", "$1");
    String newPath = origPath
      .replaceFirst("^/_/invoke/tenant/[^/ ]+(/.*$)", "$1");
    if (qry != null && !qry.isEmpty()) {
      newPath += "?" + qry;
    }
    ctx.request().headers().add(XOkapiHeaders.TENANT, tid);
    pc.debug("redirectProxy: '" + tid + "' '" + newPath + "'");
    ctx.reroute(newPath);
    logger.debug("redirectProxy: After rerouting: "
      + ctx.request().path() + " " + ctx.request().query());
  }

  public void autoDeploy(ModuleDescriptor md,
    Handler<ExtendedAsyncResult<Void>> fut) {

    discoveryManager.autoDeploy(md, fut);
  }

  public void autoUndeploy(ModuleDescriptor md,
    Handler<ExtendedAsyncResult<Void>> fut) {

    discoveryManager.autoUndeploy(md, fut);
  }


} // class
