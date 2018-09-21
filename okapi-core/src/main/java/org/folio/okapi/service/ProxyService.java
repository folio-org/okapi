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
import java.util.Random;
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
import org.folio.okapi.common.Messages;

/**
 * Okapi's proxy service. Routes incoming requests to relevant modules, as
 * enabled for the current tenant.
 */
// S1168: Empty arrays and collections should be returned instead of null
// S1192: String literals should not be duplicated
// S2245: Using pseudorandom number generators (PRNGs) is security-sensitive
@java.lang.SuppressWarnings({"squid:S1168", "squid:S1192", "squid:S2245"})
public class ProxyService {

  private final Logger logger = OkapiLogger.get();

  private final ModuleManager moduleManager;
  private final TenantManager tenantManager;
  private final DiscoveryManager discoveryManager;
  private final InternalModule internalModule;
  private final String okapiUrl;
  private final Vertx vertx;
  private final HttpClient httpClient;
  // for load balancing, so security is not an issue
  private static Random random = new Random();
  private final int waitMs;
  private static final String REDIRECTQUERY = "redirect-query"; // See redirectProxy below
  private Messages messages = Messages.getInstance();

  public ProxyService(Vertx vertx, ModuleManager modules, TenantManager tm,
    DiscoveryManager dm, InternalModule im, String okapiUrl, int waitMs) {
    this.vertx = vertx;
    this.moduleManager = modules;
    this.tenantManager = tm;
    this.internalModule = im;
    this.discoveryManager = dm;
    this.okapiUrl = okapiUrl;
    this.waitMs = waitMs;
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
    String url = makeUrl(mi, ctx).replaceFirst("[?#].*$", ".."); // rm params
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
        List<RoutingEntry> rr = trymod.getFilterRoutingEntries();
        rr.addAll(trymod.getProxyRoutingEntries());
        for (RoutingEntry tryre : rr) {
          if (tryre.match(redirectPath, ctx.request().method().name())) {
            final String newUri = re.getRedirectUri(uri);
            found = true;
            pc.debug("resolveRedirects: "
              + ctx.request().method() + " " + uri
              + " => " + trymod + " " + newUri);
            if (loop.contains(redirectPath + " ")) {
              pc.responseError(500, messages.getMessage("10100", loop, redirectPath));
              return false;
            }
            ModuleInstance mi = new ModuleInstance(trymod, tryre, newUri, ctx.request().method());
            mods.add(mi);
            if (!resolveRedirects(pc, mods, tryre, enabledModules,
              loop + " -> " + redirectPath, newUri, origMod)) {
              return false;
            }
          }
        }
      }
      if (!found) {
        pc.responseError(500, messages.getMessage("10101", uri, redirectPath));
      }
      return found;
    }
    return true;
  }

  /**
   * Builds the pipeline of modules to be invoked for a request.
   * Sets the
   * default authToken for each ModuleInstance. Later, these can be overwritten
   * by the ModuleTokens from the auth, if needed.
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
    pc.debug("getMods: Matching " + req.method() + " " + req.uri());

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
            ModuleInstance mi = new ModuleInstance(md, re, req.uri(), req.method());
            mi.setAuthToken(pc.getCtx().request().headers().get(XOkapiHeaders.TOKEN));
            mods.add(mi);
            pc.debug("getMods:   Added " + md.getId() + " "
              + re.getPathPattern() + " " + re.getPath() + " " + re.getPhase() + "/" + re.getLevel());
            break;
          }
        }
      }
      rr = md.getFilterRoutingEntries();
      for (RoutingEntry re : rr) {
        if (match(re, req)) {
          ModuleInstance mi = new ModuleInstance(md, re, req.uri(), req.method());
          mi.setAuthToken(pc.getCtx().request().headers().get(XOkapiHeaders.TOKEN));
          mods.add(mi);
          if (!resolveRedirects(pc, mods, re, enabledModules, "", req.uri(), "")) {
            return null;
          }
          pc.debug("getMods:   Added " + md.getId() + " "
            + re.getPathPattern() + " " + re.getPath() + " " + re.getPhase() + "/" + re.getLevel());
        }
      }
    }
    Comparator<ModuleInstance> cmp = (ModuleInstance a, ModuleInstance b)
      -> a.getRoutingEntry().getPhaseLevel().compareTo(b.getRoutingEntry().getPhaseLevel());
    mods.sort(cmp);

    // Check that our pipeline has a real module in it, not just filters,
    // so that we can return a proper 404 for requests that only hit auth
    pc.debug("Checking filters for " + req.uri());
    boolean found = false;
    for (ModuleInstance inst : mods) {
      pc.debug("getMods: Checking " + inst.getRoutingEntry().getPathPattern() + " "
        + "'" + inst.getRoutingEntry().getPhase() + "' "
        + "'" + inst.getRoutingEntry().getLevel() + "' "
      );
      if (inst.getRoutingEntry().getPhase() == null) {
        found = true; // No real handler should have a phase any more.
        // It has been deprecated for a long time, and never made any sense anyway.
        // The auth filter, uses phase 'auth'. We also have 'pre' and 'post'
      }
    }
    if (!found) {
      if ("-".equals(pc.getTenant()) // If we defaulted to supertenant,
        && !req.path().startsWith("/_/")  ) {  // and not wrong okapi request
           // The /_/ test is to make sure we report same errors as before internalModule stuff
        pc.responseError(403, messages.getMessage("10102"));
        return null;
      } else {
        pc.responseError(404, messages.getMessage("10103", req.path()));
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
      pc.responseError(400, messages.getMessage("10104"));
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
        pc.responseError(400, messages.getMessage("10105", e.getMessage()));
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
   * Set up special auth headers. Get the auth bits from the module list into
   * X-Okapi-Permissions-Required and X-Okapi-Permissions-Desired headers. Also
   * X-Okapi-Module-Permissions for each module that has such.
   */
  private void authHeaders(List<ModuleInstance> modlist,
    MultiMap requestHeaders, ProxyContext pc) {
    // Sanitize important headers from the incoming request
    sanitizeAuthHeaders(requestHeaders);
    Set<String> req = new HashSet<>();
    Set<String> want = new HashSet<>();
    Set<String> extraperms = new HashSet<>();

    Map<String, String[]> modperms = new HashMap<>(modlist.size()); //!!

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
          DeploymentDescriptor instance = pickInstance(res.result());
          if (instance == null) {
            fut.handle(new Failure<>(NOT_FOUND,
              "No running module instance found for "
              + mi.getModuleDescriptor().getId()));
            return;
          }
          mi.setUrl(instance.getUrl());
          resolveUrls(it, fut);
        }
      });
    }
  }

  private void relayToResponse(HttpServerResponse hres,
    HttpClientResponse res, ProxyContext pc) {
    if (pc.getHandlerRes() != 0) {
      hres.setStatusCode(pc.getHandlerRes());
      hres.headers().addAll(pc.getHandlerHeaders());
      logger.debug("relayToResponse: Reusing handler response "
        + pc.getHandlerRes() + " (instead of direct " + res.statusCode() + ")");
    } else if (pc.getAuthRes() != 0 && (pc.getAuthRes() < 200 || pc.getAuthRes() >= 300)) {
      hres.setStatusCode(pc.getAuthRes());
      hres.headers().addAll(pc.getAuthHeaders());
      logger.debug("relayToResponse: Reusing auth response "
        + pc.getAuthRes() + " (instead of direct " + res.statusCode() + ")");
    } else {
      logger.debug("relayToResponse: Returning direct response " + res.statusCode());
      hres.setStatusCode(res.statusCode());
      hres.headers().addAll(res.headers());
    }
    hres.headers().remove("Content-Length");
    hres.headers().remove("Transfer-Encoding");
    hres.setChunked(hres.getStatusCode() != 204);
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
  }

  /**
   * Remove all headers that are only used between Okapi and mod-authtoken
   *
   * @param headers
   */
  private void sanitizeAuthHeaders(MultiMap headers) {
    headers.remove(XOkapiHeaders.MODULE_TOKENS);
    headers.remove(XOkapiHeaders.MODULE_PERMISSIONS);
    headers.remove(XOkapiHeaders.PERMISSIONS_REQUIRED);
    headers.remove(XOkapiHeaders.PERMISSIONS_DESIRED);
    headers.remove(XOkapiHeaders.EXTRA_PERMISSIONS);
    headers.remove(XOkapiHeaders.FILTER);
  }

  /**
   * Pass the X-headers from a response to the next request. Catches the auth
   * response headers too.
   */
  private void relayToRequest(HttpClientResponse res, ProxyContext pc,
    ModuleInstance mi) {
    if ("auth".equals(mi.getRoutingEntry().getPhase())
      && res.headers().contains(XOkapiHeaders.MODULE_TOKENS)) {
      authResponse(res, pc);
    }
    // Sanitize both request headers (to remove the auth stuff we may have added)
    // and response headers (to remove stuff the auth module may have added)
    sanitizeAuthHeaders(res.headers());
    sanitizeAuthHeaders(pc.getCtx().request().headers());
    for (String s : res.headers().names()) {
      if (s.startsWith("X-") || s.startsWith("x-")) {
        final String v = res.headers().get(s);
        pc.getCtx().request().headers().set(s, v);
      }
    }
  }

  private void log(ProxyContext pc, HttpClientRequest creq) {
    pc.debug(creq.method().name() + " " + creq.uri());
    for (Map.Entry<String, String> next : creq.headers()) {
      pc.debug(" " + next.getKey() + ":" + next.getValue());
    }
  }

  private String makeUrl(ModuleInstance mi, RoutingContext ctx) {
    String url = mi.getUrl() + mi.getPath();
    String rdq = (String) ctx.data().get(REDIRECTQUERY);
    if (rdq != null) { // Parameters smuggled in from redirectProxy
      url += "?" + rdq;
      logger.debug("Recovering hidden parameters from ctx " + url);
    }
    return url;
  }


  public void proxy(RoutingContext ctx) {
    ctx.request().pause();
    ReadStream<Buffer> stream = ctx.request();
    // Pause the request data stream before doing any slow ops, otherwise
    // it will get read into a buffer somewhere.

    ProxyContext pc = new ProxyContext(ctx, waitMs);

    // Store request IP, timestamp, and method
    pc.setReqIp(ctx.request().remoteAddress().host());
    pc.setReqTimestamp(System.currentTimeMillis());
    pc.setReqMethod(ctx.request().rawMethod());

    // It would be nice to pass the request-id to the client, so it knows what
    // to look for in Okapi logs. But that breaks the schemas, and RMB-based
    // modules will not accept the response. Maybe later...
    String tenantId = tenantHeader(pc);
    if (tenantId == null) {
      stream.resume();
      return; // Error code already set in ctx
    }

    sanitizeAuthHeaders(ctx.request().headers());
    tenantManager.get(tenantId, gres -> {
      if (gres.failed()) {
        stream.resume();
        pc.responseError(400, messages.getMessage("10106", tenantId));
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

        List<ModuleInstance> l = getModulesForRequest(pc, enabledModules);
        if (l == null) {
          stream.resume();
          return; // ctx already set up
        }
        pc.setModList(l);

        pc.logRequest(ctx, tenantId);

        ctx.request().headers().add(XOkapiHeaders.URL, okapiUrl);
        ctx.request().headers().remove(XOkapiHeaders.MODULE_ID);

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

  private void proxyResponseImmediate(ProxyContext pc, HttpClientResponse res,
    ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    relayToResponse(ctx.response(), res, pc);
    makeTraceHeader(mi, res.statusCode(), pc);
    res.handler(data -> {
      ctx.response().write(data);
      pc.trace("ProxyRequestImmediate response chunk '"
        + data.toString() + "'");
    });
    res.endHandler(v -> {
      pc.closeTimer();
      ctx.response().end();
      pc.trace("ProxyRequestImmediate response end");
    });
    res.exceptionHandler(e
      -> pc.warn("proxyRequestImmediate res exception ", e));
  }

  private void proxyRequestHttpClient(Iterator<ModuleInstance> it,
    ProxyContext pc, Buffer bcontent, ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    String url = makeUrl(mi, ctx);
    HttpMethod meth = ctx.request().method();
    HttpClientRequest cReq = httpClient.requestAbs(meth, url, res -> {
      Iterator<ModuleInstance> newIt;
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        newIt = getNewIterator(it, mi);
      } else {
        newIt = it;
      }
      if (newIt.hasNext()) {
        makeTraceHeader(mi, res.statusCode(), pc);
        pc.closeTimer();
        relayToRequest(res, pc, mi);
        proxyR(newIt, pc, null, bcontent);
      } else {
        relayToResponse(ctx.response(), res, pc);
        makeTraceHeader(mi, res.statusCode(), pc);
        res.endHandler(x -> {
          pc.closeTimer();
          pc.trace("ProxyRequestHttpClient final response buf '"
            + bcontent + "'");
          if (pc.getAuthRes() != 0 && (pc.getAuthRes() < 200 || pc.getAuthRes() >= 300)) {
            ctx.response().end(pc.getAuthResBody());
          } else {
            ctx.response().end(bcontent);
          }
        });
        res.exceptionHandler(e
          -> pc.warn("proxyRequestHttpClient: res exception (b)", e));
      }
    });
    cReq.exceptionHandler(e -> {
      pc.warn("proxyRequestHttpClient failure: " + url, e);
      pc.responseError(500, messages.getMessage("10107", mi.getModuleDescriptor().getId(), mi.getUrl(), e,e.getMessage()));
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
      makeUrl(mi, ctx), res -> {
        Iterator<ModuleInstance> newIt;
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
          newIt = getNewIterator(it, mi);
        } else {
          newIt = it;
        }
        if (res.getHeader(XOkapiHeaders.STOP) == null && newIt.hasNext()) {
          makeTraceHeader(mi, res.statusCode(), pc);
          relayToRequest(res, pc, mi);
          storeResponseInfo(pc, mi, res);
          res.pause();
          proxyR(newIt, pc, res, null);
        } else {
          proxyResponseImmediate(pc, res, mi);
        }
      });
    cReq.exceptionHandler(e -> {
      pc.warn("proxyRequestResponse failure: ", e);
      pc.responseError(500, messages.getMessage("10108", mi.getModuleDescriptor().getId(), mi.getUrl(), e, e.getMessage()));
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
      makeUrl(mi, ctx), res -> {
      Iterator<ModuleInstance> newIt;
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        newIt = getNewIterator(it, mi);
        if (!newIt.hasNext() && XOkapiHeaders.FILTER_AUTH.equalsIgnoreCase(mi.getRoutingEntry().getPhase())) {
          proxyResponseImmediate(pc, res, mi);
          if (bcontent == null) {
            stream.resume();
          }
          return;
        }
      } else {
        newIt = it;
      }
      if (newIt.hasNext()) {
        relayToRequest(res, pc, mi);
        storeResponseInfo(pc, mi, res);
        makeTraceHeader(mi, res.statusCode(), pc);
        res.endHandler(x
          -> proxyR(newIt, pc, stream, bcontent));
      } else {
        relayToResponse(ctx.response(), res, pc);
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
      pc.responseError(500, messages.getMessage("10109", mi.getModuleDescriptor().getId(), mi.getUrl(), e, e.getMessage()));
    });
    cReq.headers().setAll(ctx.request().headers());
    cReq.headers().remove("Content-Length");
    cReq.end();
    log(pc, cReq);
  }

  private void proxyRedirect(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    ModuleInstance mi) {

    pc.trace("ProxyNull " + mi.getModuleDescriptor().getId());
    pc.closeTimer();
    // if no more entries in it, proxyR will return 404
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
      pc.setHandlerRes(statusCode);
      if (statusCode == 200 && resp.isEmpty()) {
        // Say "no content", if there isn't any
        statusCode = 204;
        pc.getCtx().response().setStatusCode(statusCode);
      }
      Buffer respBuf = Buffer.buffer(resp);
      if (it.hasNext()) { // carry on with the pipeline
        proxyR(it, pc, null, respBuf);
      } else { // produce a result
        makeTraceHeader(mi, statusCode, pc);
        pc.closeTimer();
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
      pc.responseError(404, ""); // Should have been caught earlier
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

      // Pass headers for filters
      passFilterHeaders(ctx, pc, mi);

      // Do proxy work
      ProxyType pType = mi.getRoutingEntry().getProxyType();
      if (pType != ProxyType.REDIRECT) {
        pc.debug("Invoking module " + mi.getModuleDescriptor().getId()
          + " type " + pType
          + " level " + mi.getRoutingEntry().getPhaseLevel()
          + " path " + mi.getPath()
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
          pc.responseError(500, messages.getMessage("10110", pType, mi.getModuleDescriptor().getId()));
          break;
      }
    }
  }

  private void passFilterHeaders(RoutingContext ctx, ProxyContext pc, ModuleInstance mi) {
    // Pass the X-Okapi-Filter header for filters (only)
    // And all kind of things for the auth filter
    ctx.request().headers().remove(XOkapiHeaders.FILTER);
    if (mi.getRoutingEntry().getPhase() != null) {
      String pth = mi.getRoutingEntry().getPathPattern();
      if (pth == null) {
        pth = mi.getRoutingEntry().getPath();
      }
      String filt = mi.getRoutingEntry().getPhase() + " " + pth;
      pc.debug("Adding " + XOkapiHeaders.FILTER + ": " + filt);
      // The auth filter needs all kinds of special headers
      ctx.request().headers().add(XOkapiHeaders.FILTER, filt);

      String phase = mi.getRoutingEntry().getPhase();
      boolean badAuth = pc.getAuthRes() != 0 && (pc.getAuthRes() < 200 || pc.getAuthRes() >= 300);
      switch (phase) {
        case XOkapiHeaders.FILTER_AUTH:
          authHeaders(pc.getModList(), ctx.request().headers(), pc);
          break;
        case XOkapiHeaders.FILTER_PRE:
          // pass request headers and failed auth result
          passRequestInfo(ctx, pc);
          if (badAuth) {
            ctx.request().headers().add(XOkapiHeaders.AUTH_RESULT, "" + pc.getAuthRes());
          }
          break;
        case XOkapiHeaders.FILTER_POST:
          // pass request headers and failed handler/auth result
          passRequestInfo(ctx, pc);
          if (pc.getHandlerRes() > 0) {
            String hresult = String.valueOf(pc.getHandlerRes());
            logger.debug("proxyR: postHeader: Setting " + XOkapiHeaders.HANDLER_RESULT + " to '" + hresult + "'");
            ctx.request().headers().add(XOkapiHeaders.HANDLER_RESULT, hresult);
          } else if (badAuth) {
            ctx.request().headers().add(XOkapiHeaders.AUTH_RESULT, "" + pc.getAuthRes());
          } else {
            logger.warn("proxyR: postHeader: Oops, no result to pass to post handler");
          }
          break;
        default:
          logger.error("Not supported phase: " + phase);
          break;
      }
    }
  }

  private void passRequestInfo(RoutingContext ctx, ProxyContext pc) {
    ctx.request().headers().add(XOkapiHeaders.REQUEST_IP, pc.getReqIp());
    ctx.request().headers().add(XOkapiHeaders.REQUEST_TIMESTAMP, "" + pc.getReqTimestamp());
    ctx.request().headers().add(XOkapiHeaders.REQUEST_METHOD, pc.getReqMethod());
  }

  private DeploymentDescriptor pickInstance(List<DeploymentDescriptor> instances) {
    int sz = instances.size();
    return sz > 0 ? instances.get(random.nextInt(sz)) : null;
  }

  /**
   * Make a request to a system interface, like _tenant. Part 1: Check that we
   * are working as the right tenant, and if not so, change identity to the
   * correct one.
   *
   * @param tenant to make the request for
   * @param inst carries the moduleDescriptor, RoutingEntry, and getPath to be
   * called
   * @param request body to send in the request
   * @param pc ProxyContext for logging, and returning resp headers
   * @param fut Callback with the OkapiClient that contains the body, headers,
   * and/or errors
   */
  public void callSystemInterface(Tenant tenant, ModuleInstance inst,
    String request, ProxyContext pc,
    Handler<ExtendedAsyncResult<OkapiClient>> fut) {
    String tenantId = tenant.getId(); // the tenant we are about to enable
    String curTenantId = pc.getTenant(); // is often the supertenant
    String authToken = pc.getCtx().request().headers().get(XOkapiHeaders.TOKEN);
    pc.debug("callSystemInterface on " + Json.encode(inst)
      + " for " + tenantId + " as " + curTenantId + " with authToken " + authToken);
    if (tenantId.equals(curTenantId)) {
      pc.debug("callSystemInterface: Same tenant, no need for trickery");
      doCallSystemInterface(tenantId, authToken, inst, null, request, pc, fut);
      return;
    }
    // Check if the actual tenant has auth enabled. If yes, get a token for it.
    // If we have auth for current (super)tenant is irrelevant here!
    pc.debug("callSystemInterface: Checking if " + tenantId + " has auth");

    moduleManager.getEnabledModules(tenant, mres -> {
      if (mres.failed()) { // Should not happen
        pc.warn("callSystemInterface: getEnabledModules failed: ", mres.cause());
        fut.handle(new Failure<>(mres.getType(), mres.cause()));
        return;
      }
      List<ModuleDescriptor> enabledModules = mres.result();
      for (ModuleDescriptor md : enabledModules) {
        RoutingEntry[] filters = md.getFilters();
        if (filters != null) {
          for (RoutingEntry filt : filters) {
            if ("auth".equals(filt.getPhase())) {
              pc.debug("callSystemInterface: Found auth filter in " + md.getId());
              authForSystemInterface(md, filt, tenantId, inst, request, pc, fut);
              return;
            }
          }
        }
      }
      pc.debug("callSystemInterface: No auth for " + tenantId
        + " calling with tenant header only");
      doCallSystemInterface(tenantId, null, inst, null, request, pc, fut);
    });
  }

  /**
   * Helper to get a new authtoken before invoking doCallSystemInterface.
   *
   */
  private void authForSystemInterface(ModuleDescriptor authMod, RoutingEntry filt,
    String tenantId, ModuleInstance inst,
    String request, ProxyContext pc,
    Handler<ExtendedAsyncResult<OkapiClient>> fut) {
    pc.debug("Calling doCallSystemInterface to get auth token");
    RoutingEntry re = inst.getRoutingEntry();
    String modPerms = "";

    if (re != null) {
      String[] modulePermissions = re.getModulePermissions();
      Map<String, String[]> mpMap = new HashMap<>();
      if (modulePermissions != null) {
        mpMap.put(inst.getModuleDescriptor().getId(), modulePermissions);
        logger.debug("authForSystemInterface: Found modPerms:" + modPerms);
      } else {
        logger.debug("authForSystemInterface: Got RoutingEntry, but null modulePermissions");
      }
      modPerms = Json.encode(mpMap);
    } else {
      logger.debug("authForSystemInterface: re is null, can't find modPerms");
    }
    ModuleInstance authInst = new ModuleInstance(authMod, filt, inst.getPath(), HttpMethod.HEAD);
    doCallSystemInterface(tenantId, null, authInst, modPerms, "", pc, res -> {
      if (res.failed()) {
        pc.warn("Auth check for systemInterface failed!");
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      OkapiClient cli = res.result();
      String deftok = cli.getRespHeaders().get(XOkapiHeaders.TOKEN);
      logger.debug("authForSystemInterface:"
        + Json.encode(cli.getRespHeaders().entries()));
      String modTok = cli.getRespHeaders().get(XOkapiHeaders.MODULE_TOKENS);
      JsonObject jo = new JsonObject(modTok);
      String token = jo.getString(inst.getModuleDescriptor().getId(), deftok);
      logger.debug("authForSystemInterface: Got token " + token);
      doCallSystemInterface(tenantId, token, inst, null, request, pc, fut);
    });
  }

  /**
   * Actually make a request to a system interface, like _tenant. Assumes we are
   * operating as the correct tenant.
   */
  private void doCallSystemInterface(String tenantId, String authToken,
    ModuleInstance inst, String modPerms,
    String request, ProxyContext pc,
    Handler<ExtendedAsyncResult<OkapiClient>> fut) {
    String curTenant = pc.getTenant();
    pc.debug("doCallSystemInterface on " + Json.encode(inst)
      + " for " + tenantId + " as " + curTenant + " with token " + authToken);

    discoveryManager.get(inst.getModuleDescriptor().getId(), gres -> {
      if (gres.failed()) {
        pc.warn("doCallSystemInterface on " + inst.getModuleDescriptor().getId() + " "
          + inst.getPath()
          + " failed. Could not find the module in discovery", gres.cause());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      DeploymentDescriptor instance = pickInstance(gres.result());
      if (instance == null) {
        fut.handle(new Failure<>(USER, messages.getMessage("11100",
          inst.getModuleDescriptor().getId(), inst.getPath())));
        return;
      }
      String baseurl = instance.getUrl();
      pc.debug("doCallSystemInterface Url: " + baseurl + " and " + inst.getPath());
      Map<String, String> headers = sysReqHeaders(pc.getCtx(), tenantId, authToken);
      if (modPerms != null) { // We are making an auth call
        RoutingEntry re = inst.getRoutingEntry();
        if (re != null) {
          headers.put(XOkapiHeaders.FILTER, re.getPhase());
        }
        if (!modPerms.isEmpty()) {
          headers.put(XOkapiHeaders.MODULE_PERMISSIONS, modPerms);
        }
        // Clear the permissions-required header that we inherited from the
        // original request (e.g. to tenant-enable), as we do not have those
        // perms set in the target tenant
        headers.put(XOkapiHeaders.PERMISSIONS_REQUIRED, "");
        headers.put(XOkapiHeaders.PERMISSIONS_DESIRED, "");
        logger.debug("Auth call, some tricks with permissions");
      }
      pc.debug("doCallSystemInterface: About to create OkapiClient with headers "
        + Json.encode(headers));
      OkapiClient cli = new OkapiClient(baseurl, vertx, headers);
      String reqId = inst.getPath().replaceFirst("^[/_]*([^/]+).*", "$1");
      cli.newReqId(reqId); // "tenant" or "tenantpermissions"
      cli.enableInfoLog();
      cli.setClosedRetry(15000);
      cli.request(inst.getMethod(), inst.getPath(), request, cres -> {
        cli.close();
        if (cres.failed()) {
          String msg = messages.getMessage("11101", inst.getMethod(),
            inst.getModuleDescriptor().getId(), inst.getPath(), cres.cause().getMessage());
          pc.warn(msg);
          fut.handle(new Failure<>(INTERNAL, msg));
          return;
        }
        // Pass response headers - needed for unit test, if nothing else
        String body = cres.result();
        pc.debug("doCallSystemInterface response: " + body);
        pc.debug("doCallSystemInterface ret "
          + " hdrs: " + Json.encode(cli.getRespHeaders().entries()));
        pc.passOkapiTraceHeaders(cli);
        fut.handle(new Success<>(cli));
      });
    });
  }

  /**
   * Helper to make request headers for the system requests we make. Copies all
   * X- headers over. Adds a tenant, and a token, if we have one.
   */
  private Map<String, String> sysReqHeaders(RoutingContext ctx,
    String tenantId, String authToken) {
    Map<String, String> headers = new HashMap<>();
    for (String hdr : ctx.request().headers().names()) {
      if (hdr.matches("^X-.*$")) {
        headers.put(hdr, ctx.request().headers().get(hdr));
      }
    }
    headers.put(XOkapiHeaders.TENANT, tenantId);
    logger.debug("Added " + XOkapiHeaders.TENANT + " : " + tenantId);
    if (authToken == null) {
      headers.remove(XOkapiHeaders.TOKEN);
    } else {
      headers.put(XOkapiHeaders.TOKEN, authToken);
    }
    headers.put("Accept", "*/*");
    headers.put("Content-Type", "application/json; charset=UTF-8");
    return headers;
  }

  /**
   * Extract tenantId from the request, rewrite the getPath, and proxy it.
   * Expects a request to something like /_/proxy/tenant/{tid}/mod-something.
   * Rewrites that to /mod-something, with the tenantId passed in the proper
   * header. As there is no authtoken, this will not work for many things, but
   * is needed for callbacks in the SSO systems, and who knows what else.
   *
   * @param ctx
   */
  public void redirectProxy(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, waitMs);
    final String origPath = ctx.request().path();
    String qry = ctx.request().query();
    String tid = origPath
      .replaceFirst("^/_/invoke/tenant/([^/ ]+)/.*$", "$1");
    String newPath = origPath
      .replaceFirst("^/_/invoke/tenant/[^/ ]+(/.*$)", "$1");
    if (qry != null && !qry.isEmpty()) {
      // vert.x 3.5 clears the parameters on reroute, so we pass them in ctx
      ctx.data().put(REDIRECTQUERY, qry);
      logger.debug("Hiding parameters into ctx " + qry);
    }
    ctx.request().headers().add(XOkapiHeaders.TENANT, tid);
    pc.debug("redirectProxy: '" + tid + "' '" + newPath + "'");
    ctx.reroute(newPath);
    logger.debug("redirectProxy: After rerouting: "
      + ctx.request().path() + " " + qry);
  }

  public void autoDeploy(ModuleDescriptor md, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {

    discoveryManager.autoDeploy(md, pc, fut);
  }

  public void autoUndeploy(ModuleDescriptor md, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {

    discoveryManager.autoUndeploy(md, pc, fut);
  }

  // store Auth/Handler response, and pass header as needed
  private void storeResponseInfo(ProxyContext pc, ModuleInstance mi, HttpClientResponse res) {
    String phase = mi.getRoutingEntry().getPhase();
    boolean passHeaders = false;
    // It was a real handler, remember the response code and headers
    if (phase == null) {
      logger.debug("proxyRequestResponse: Remembering result " + res.statusCode());
      pc.setHandlerRes(res.statusCode());
      pc.getHandlerHeaders().clear().addAll(res.headers());
      passHeaders = true;
    } else if (XOkapiHeaders.FILTER_AUTH.equalsIgnoreCase(phase)) {
      logger.debug("proxyAuth: Remembering result " + res.statusCode());
      pc.setAuthRes(res.statusCode());
      pc.getAuthHeaders().clear().addAll(res.headers());
      pc.setAuthResBody(Buffer.buffer());
      res.handler(data -> pc.getAuthResBody().appendBuffer(data));
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        passHeaders = true;
      }
    }
    if (passHeaders) {
      // Pass along response headers to Post filter for logging
      // Note: relayToRequest() already took care of X- headers
      res.headers().entries().stream()
        .filter(e -> !e.getKey().toLowerCase().startsWith("x-"))
        .forEach(e -> pc.getCtx().request().headers().add(e.getKey(), e.getValue()));
    }
  }

  // skip handler, but not if at pre/post filter phase
  private Iterator<ModuleInstance> getNewIterator(Iterator<ModuleInstance> it, ModuleInstance mi) {
    String phase = mi.getRoutingEntry().getPhase();
    if (XOkapiHeaders.FILTER_PRE.equals(phase) || XOkapiHeaders.FILTER_POST.equals(phase)) {
      return it;
    }
    List<ModuleInstance> list = new ArrayList<>();
    it.forEachRemaining(m -> {
      if (m.getRoutingEntry().getPhase() != null) {
        list.add(m);
      }
    });
    return list.iterator();
  }


} // class
