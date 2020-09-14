package org.folio.okapi.managers;

import io.micrometer.core.instrument.Timer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.RoutingEntry.ProxyType;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.OkapiToken;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.logging.FolioLoggingContext;
import org.folio.okapi.util.CorsHelper;
import org.folio.okapi.util.MetricsHelper;
import org.folio.okapi.util.ProxyContext;
import org.folio.okapi.util.TokenCache;
import org.folio.okapi.util.TokenCache.CacheEntry;


/**
 * Okapi's proxy service. Routes incoming requests to relevant modules, as
 * enabled for the current tenant.
 */
// S1168: Empty arrays and collections should be returned instead of null
// S1192: String literals should not be duplicated
// S2245: Using pseudorandom number generators (PRNGs) is security-sensitive
@java.lang.SuppressWarnings({"squid:S1168", "squid:S1192", "squid:S2245"})
public class ProxyService {

  private static final Logger logger = OkapiLogger.get();

  private final ModuleManager moduleManager;
  private final TenantManager tenantManager;
  private final DiscoveryManager discoveryManager;
  private final InternalModule internalModule;
  private final String okapiUrl;
  private final Vertx vertx;
  private final HttpClient httpClient;
  // for load balancing, so security is not an issue
  private static final Random random = new Random();
  private final int waitMs;
  private static final String REDIRECTQUERY = "redirect-query"; // See redirectProxy below
  private final Messages messages = Messages.getInstance();

  private TokenCache tokenCache = new TokenCache();

  /**
   * Construct Proxy service.
   * @param vertx Vert.x handle
   * @param modules module manager
   * @param tm tenant manager
   * @param dm discovery manager
   * @param im internal module
   * @param okapiUrl Okapi URL
   * @param config configuration
   */
  public ProxyService(Vertx vertx, ModuleManager modules, TenantManager tm,
                      DiscoveryManager dm, InternalModule im, String okapiUrl, JsonObject config) {
    this.vertx = vertx;
    this.moduleManager = modules;
    this.tenantManager = tm;
    this.internalModule = im;
    this.discoveryManager = dm;
    this.okapiUrl = okapiUrl;
    this.waitMs = config.getInteger("logWaitMs", 0);
    HttpClientOptions opt = new HttpClientOptions();
    opt.setMaxPoolSize(1000);
    httpClient = vertx.createHttpClient(opt);
  }

  /**
   * Make a trace header. Also writes a log entry for the response.
   *
   * @param mi module instance
   * @param statusCode status code for the response
   * @param pc ProxyContext
   */
  private void makeTraceHeader(ModuleInstance mi, int statusCode,
                               ProxyContext pc) {

    RoutingContext ctx = pc.getCtx();
    String url = makeUrl(mi, ctx);
    pc.addTraceHeaderLine(ctx.request().method() + " "
        + mi.getModuleDescriptor().getId() + " "
        + url.replaceFirst("[?#].*$", "..") // remove params
        + " : " + statusCode + " " + pc.timeDiff());
    pc.logResponse(mi.getModuleDescriptor().getId(), url, statusCode);
  }

  private boolean match(RoutingEntry e, HttpServerRequest req) {
    return e.match(req.uri(), req.method().name());
  }

  private boolean resolveRedirects(ProxyContext pc,
                                   List<ModuleInstance> mods, RoutingEntry re,
                                   List<ModuleDescriptor> enabledModules,
                                   final String loop, final String uri) {

    RoutingContext ctx = pc.getCtx();
    if (re.getProxyType() == ProxyType.REDIRECT) { // resolve redirects
      boolean found = false;
      final String redirectPath = re.getRedirectPath();
      for (ModuleDescriptor trymod : enabledModules) {
        for (RoutingEntry tryre : trymod.getFilterRoutingEntries()) {
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
            ModuleInstance mi = new ModuleInstance(trymod, tryre, newUri,
                ctx.request().method(), false);
            mods.add(mi);
            if (!resolveRedirects(pc, mods, tryre, enabledModules,
                loop + " -> " + redirectPath, newUri)) {
              return false;
            }
          }
        }
        for (RoutingEntry tryre : trymod.getProxyRoutingEntries()) {
          if (tryre.match(redirectPath, ctx.request().method().name())) {
            final String newUri = re.getRedirectUri(uri);
            found = true;
            pc.debug("resolveRedirects: "
                + ctx.request().method() + " " + uri
                + " => " + trymod + " " + newUri);
            ModuleInstance mi = new ModuleInstance(trymod, tryre,
                newUri, ctx.request().method(), true);
            mods.add(mi);
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

  private boolean checkTokenCache(ProxyContext pc, HttpServerRequest req, RoutingEntry re, ModuleInstance mi) {
    boolean skipAuth = false;
    String pathPattern = re.getPathPattern();

    CacheEntry cached = tokenCache.get(pc.getTenant(), req.method().name(),
        pathPattern == null ? req.path() : pathPattern, req.headers().get(XOkapiHeaders.TOKEN),
        req.getHeader(XOkapiHeaders.USER_ID));

    if (cached != null) {
      mi.setAuthToken(cached.token);
      mi.setxOkapiUserId(cached.xokapiUserid);
      mi.setxOkapiPermissions(cached.xokapiPermissions);

      skipAuth = true;
    } else {
      mi.setAuthToken(req.headers().get(XOkapiHeaders.TOKEN));
    }
    return skipAuth;
  }

  /**
   * Builds the pipeline of modules to be invoked for a request. Sets the
   * default authToken for each ModuleInstance. Later, these can be overwritten
   * by the ModuleTokens from the auth, if needed.
   *
   * @param pc ProxyContext
   * @param enabledModules modules enabled for the current tenant
   * @return a list of ModuleInstances. In case of error, sets up ctx and returns null.
   */
  private List<ModuleInstance> getModulesForRequest(ProxyContext pc,
                                                    List<ModuleDescriptor> enabledModules) {

    List<ModuleInstance> mods = new ArrayList<>();
    HttpServerRequest req = pc.getCtx().request();
    final String id = req.getHeader(XOkapiHeaders.MODULE_ID);
    pc.debug("getMods: Matching " + req.method() + " " + req.uri());

    boolean skipAuth = false;

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
            ModuleInstance mi = new ModuleInstance(md, re, req.uri(), req.method(), true);

            skipAuth = checkTokenCache(pc, req, re, mi);
            mods.add(mi);
            pc.debug("getMods:   Added " + md.getId() + " " + re.getPathPattern() + " "
                + re.getPath() + " " + re.getPhase() + "/" + re.getLevel());
            break;
          }
        }
      }
      rr = md.getFilterRoutingEntries();
      for (RoutingEntry re : rr) {
        if (match(re, req)) {
          ModuleInstance mi = new ModuleInstance(md, re, req.uri(), req.method(), false);
          mi.setAuthToken(req.headers().get(XOkapiHeaders.TOKEN));
          mods.add(mi);
          if (!resolveRedirects(pc, mods, re, enabledModules, "", req.uri())) {
            return null;
          }
          pc.debug("getMods:   Added " + md.getId() + " "
              + re.getPathPattern() + " " + re.getPath() + " "
              + re.getPhase() + "/" + re.getLevel());
        }
      }
    }
    Comparator<ModuleInstance> cmp = (ModuleInstance a, ModuleInstance b)
        -> a.getRoutingEntry().getPhaseLevel().compareTo(b.getRoutingEntry().getPhaseLevel());
    mods.sort(cmp);

    if (skipAuth) {
      pc.debug("Skipping auth, have cached token.");
      mods.remove(0);
    }

    // Check that our pipeline has a real module in it, not just filters,
    // so that we can return a proper 404 for requests that only hit auth
    pc.debug("Checking filters for " + req.uri());
    boolean found = false;
    for (ModuleInstance inst : mods) {
      pc.debug("getMods: Checking " + inst.getRoutingEntry().getPathPattern() + " "
          + "'" + inst.getRoutingEntry().getPhase() + "' "
          + "'" + inst.getRoutingEntry().getLevel() + "' "
      );
      if (inst.isHandler()) {
        found = true;
      }
    }
    if (!found) {
      pc.responseError(404, messages.getMessage("10103", req.path(), pc.getTenant()));
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
   * @param pc ProxyContext
   * @return null in case of errors, with the response already set in ctx. If
   *     all went well, returns the tenantId for further processing.
   */
  private void parseTokenAndPopulateContext(ProxyContext pc) {
    RoutingContext ctx = pc.getCtx();
    String auth = ctx.request().getHeader(XOkapiHeaders.AUTHORIZATION);
    String tok = ctx.request().getHeader(XOkapiHeaders.TOKEN);
    if (auth != null) {
      if (auth.startsWith("Bearer ")) {
        auth = auth.substring(6).trim();
      }
      if (tok != null && !auth.equals(tok)) {
        pc.responseError(400, messages.getMessage("10104"));
        throw new IllegalArgumentException("X-Okapi-Token is not equal to Authorization token");
      }
      ctx.request().headers().set(XOkapiHeaders.TOKEN, auth);
      ctx.request().headers().remove(XOkapiHeaders.AUTHORIZATION);
      pc.debug("Okapi: Moved Authorization header to X-Okapi-Token");
    }
    String tenantId = ctx.request().getHeader(XOkapiHeaders.TENANT);
    String userId = ctx.request().getHeader(XOkapiHeaders.USER_ID);

    OkapiToken okapiToken = null;

    if (tenantId == null) {
      try {
        okapiToken = new OkapiToken(ctx.request().getHeader(XOkapiHeaders.TOKEN));
      } catch (IllegalArgumentException e) {
        pc.responseError(400, messages.getMessage("10105", e.getMessage()));
        throw new IllegalArgumentException(e);
      }
    }

    // userId does not exist all the time
    if (userId == null) {
      if (okapiToken == null) {
        try {
          okapiToken = new OkapiToken(ctx.request().getHeader(XOkapiHeaders.TOKEN));
        } catch (IllegalArgumentException e) {
          okapiToken = null;
        }
      }
      if (okapiToken != null) {
        pc.setUserId(okapiToken.getUserIdWithoutValidation());
      }
    }

    if (tenantId == null) {
      tenantId = okapiToken.getTenantWithoutValidation();
      if (tenantId != null) {
        ctx.request().headers().add(XOkapiHeaders.TENANT, tenantId);
        pc.debug("Okapi: Recovered tenant from token: '" + tenantId + "'");
      }
      if (tenantId == null) {
        logger.debug("No tenantId, defaulting to " + XOkapiHeaders.SUPERTENANT_ID);
        tenantId = XOkapiHeaders.SUPERTENANT_ID;
        ctx.request().headers().add(XOkapiHeaders.TENANT, tenantId);
      }
    }

    pc.setTenant(tenantId);
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
        // replace module permissions with auto generated permission set id
        if (moduleManager.getExpandedPermModuleTenants().contains(pc.getTenant())) {
          modp = new String[] {re.generateSystemId(mod.getModuleDescriptor().getId())};
        }
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
            fut.handle(new Failure<>(ErrorType.NOT_FOUND,
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
    } else if (pc.getAuthRes() != 0 && (pc.getAuthRes() < 200 || pc.getAuthRes() >= 300)) {
      hres.setStatusCode(pc.getAuthRes());
      hres.headers().addAll(pc.getAuthHeaders());
    } else {
      if (res != null) {
        hres.setStatusCode(res.statusCode());
        hres.headers().addAll(res.headers());
      }
    }
    sanitizeAuthHeaders(hres.headers());
    hres.headers().remove("Content-Length");
    hres.headers().remove("Transfer-Encoding");
    if (hres.getStatusCode() != 204) {
      hres.setChunked(true);
    }
  }

  /**
   * Process the auth module response. Set tokens for those modules that
   * received one.
   */
  private void authResponse(HttpClientResponse res, ProxyContext pc) {
    String modTok = res.headers().get(XOkapiHeaders.MODULE_TOKENS);
    HttpServerRequest req = pc.getCtx().request();
    if (modTok != null && !modTok.isEmpty()) {
      JsonObject jo = new JsonObject(modTok);
      for (ModuleInstance mi : pc.getModList()) {
        String id = mi.getModuleDescriptor().getId();
        String pathPattern = mi.getRoutingEntry().getPathPattern();

        if (jo.containsKey(id)) {
          String tok = jo.getString(id);
          mi.setAuthToken(tok);
          pc.debug("authResponse: token for " + id + ": " + tok);

          tokenCache.put(pc.getTenant(),
              req.method().name(),
              pathPattern == null ? req.path() : pathPattern,
              res.getHeader(XOkapiHeaders.USER_ID),
              res.getHeader(XOkapiHeaders.PERMISSIONS),
              req.getHeader(XOkapiHeaders.TOKEN),
              tok);
        } else if (jo.containsKey("_")) {
          String tok = jo.getString("_");
          mi.setAuthToken(tok);
          pc.debug("authResponse: Default (_) token for " + id + ": " + tok);

          tokenCache.put(pc.getTenant(),
              req.method().name(),
              pathPattern == null ? req.path() : pathPattern,
              res.getHeader(XOkapiHeaders.USER_ID),
              res.getHeader(XOkapiHeaders.PERMISSIONS),
              req.getHeader(XOkapiHeaders.TOKEN),
              tok);
        }
      }
    }
  }

  /**
   * Remove all headers that are only used between Okapi and mod-authtoken.
   *
   * @param headers request or response HTTP headers
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
    if (XOkapiHeaders.FILTER_AUTH.equals(mi.getRoutingEntry().getPhase())
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

  private String getPath(ModuleInstance mi, RoutingContext ctx) {
    String path = mi.getPath();
    String rdq = (String) ctx.data().get(REDIRECTQUERY);
    if (rdq != null) { // Parameters smuggled in from redirectProxy
      path += "?" + rdq;
      logger.debug("Recovering hidden parameters from ctx {}", path);
    }
    return path;
  }

  private String makeUrl(ModuleInstance mi, RoutingContext ctx) {
    return mi.getUrl() + getPath(mi, ctx);
  }

  /**
   * Routing context hander (handling all requests for Okapi).
   * @param ctx routing context
   */
  public void proxy(RoutingContext ctx) {
    ctx.request().pause();
    ReadStream<Buffer> stream = ctx.request();
    // Pause the request data stream before doing any slow ops, otherwise
    // it will get read into a buffer somewhere.

    ProxyContext pc = new ProxyContext(ctx, waitMs);

    // It would be nice to pass the request-id to the client, so it knows what
    // to look for in Okapi logs. But that breaks the schemas, and RMB-based
    // modules will not accept the response. Maybe later...
    try {
      parseTokenAndPopulateContext(pc);
    } catch (IllegalArgumentException e) {
      stream.resume();
      return; // Error code already set in ctx
    }
    String tenantId = pc.getTenant();

    final MultiMap headers = ctx.request().headers();

    FolioLoggingContext.put(FolioLoggingContext.TENANT_ID_LOGGING_VAR_NAME,
        tenantId);
    FolioLoggingContext.put(FolioLoggingContext.REQUEST_ID_LOGGING_VAR_NAME,
        headers.get(XOkapiHeaders.REQUEST_ID));
    FolioLoggingContext.put(FolioLoggingContext.MODULE_ID_LOGGING_VAR_NAME,
        headers.get(XOkapiHeaders.MODULE_ID));
    FolioLoggingContext.put(FolioLoggingContext.USER_ID_LOGGING_VAR_NAME,
        pc.getUserId());

    sanitizeAuthHeaders(headers);
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

        List<ModuleInstance> l = getModulesForRequest(pc, enabledModules);
        if (l == null) {
          stream.resume();
          return; // ctx already set up
        }

        // check delegate CORS and reroute if necessary
        if (CorsHelper.checkCorsDelegate(ctx, l)) {
          // HTTP code 100 is chosen purely as metrics tag placeholder
          MetricsHelper.recordHttpServerProcessingTime(pc.getSample(), pc.getTenant(), 100,
              pc.getCtx().request().method().name(), pc.getHandlerModuleInstance());
          stream.resume();
          ctx.reroute(ctx.request().path());
          return;
        }

        pc.setModList(l);

        pc.logRequest(ctx, tenantId);

        headers.set(XOkapiHeaders.URL, okapiUrl);
        headers.remove(XOkapiHeaders.MODULE_ID);
        headers.set(XOkapiHeaders.REQUEST_IP, ctx.request().remoteAddress().host());
        headers.set(XOkapiHeaders.REQUEST_TIMESTAMP, "" + System.currentTimeMillis());
        headers.set(XOkapiHeaders.REQUEST_METHOD, ctx.request().method().name());

        resolveUrls(l.iterator(), res -> {
          if (res.failed()) {
            stream.resume();
            pc.responseError(res.getType(), res.cause());
          } else {
            List<HttpClientRequest> clientRequest = new LinkedList<>();
            proxyR(l.iterator(), pc, stream, null, clientRequest);
          }
        });
      });

    });
  }

  private static void clientsEnd(Buffer bcontent, List<HttpClientRequest> clientRequestList) {
    for (HttpClientRequest r : clientRequestList) {
      r.end(bcontent);
    }
  }

  private void proxyResponseImmediate(ProxyContext pc, ReadStream<Buffer> readStream,
                                      Buffer bcontent, List<HttpClientRequest> clientRequestList) {

    RoutingContext ctx = pc.getCtx();
    if (pc.getAuthRes() != 0 && (pc.getAuthRes() < 200 || pc.getAuthRes() >= 300)) {
      if (bcontent == null) {
        readStream.resume();
      }
      bcontent = pc.getAuthResBody();
    }
    if (bcontent != null) {
      pc.closeTimer();
      clientsEnd(bcontent, clientRequestList);
      ctx.response().end(bcontent);
    } else {
      streamHandle(pc, readStream, ctx.response(), clientRequestList);
    }
    MetricsHelper.recordHttpServerProcessingTime(pc.getSample(), pc.getTenant(),
        ctx.response().getStatusCode(), ctx.request().method().name(),
        pc.getHandlerModuleInstance());
  }

  private void proxyClientFailure(ProxyContext pc, ModuleInstance mi, Throwable res) {
    String e = res.getMessage();
    pc.warn("proxyRequest failure: " + mi.getUrl() + ": " + e);
    MetricsHelper.recordHttpClientError(pc.getTenant(), mi.getMethod().name(),
        mi.getRoutingEntry().getStaticPath());
    pc.responseError(500, messages.getMessage("10107",
        mi.getModuleDescriptor().getId(), mi.getUrl(), e));
  }

  private void proxyRequestHttpClient(
      Iterator<ModuleInstance> it,
      ProxyContext pc, Buffer bcontent, List<HttpClientRequest> clientRequestList,
      ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    String url = makeUrl(mi, ctx);
    HttpMethod meth = ctx.request().method();
    Future<HttpClientRequest> fut = httpClient.request(
        new RequestOptions().setMethod(meth).setAbsoluteURI(url));
    fut.onFailure(res -> proxyClientFailure(pc, mi, res));
    fut.onSuccess(clientRequest -> {
      final Timer.Sample sample = MetricsHelper.getTimerSample();
      copyHeaders(clientRequest, ctx, mi);
      pc.trace("ProxyRequestHttpClient request buf '"
          + bcontent + "'");
      clientsEnd(bcontent, clientRequestList);
      clientRequest.end(bcontent);
      log(pc, clientRequest);
      clientRequest.onFailure(res -> proxyClientFailure(pc, mi, res));
      clientRequest.onSuccess(res -> {
        MetricsHelper.recordHttpClientResponse(sample, pc.getTenant(), res.statusCode(),
            meth.name(), mi);
        Iterator<ModuleInstance> newIt = getNewIterator(it, mi, res.statusCode());
        if (newIt.hasNext()) {
          makeTraceHeader(mi, res.statusCode(), pc);
          pc.closeTimer();
          relayToRequest(res, pc, mi);
          proxyR(newIt, pc, null, bcontent, new LinkedList<>());
        } else {
          relayToResponse(ctx.response(), res, pc);
          makeTraceHeader(mi, res.statusCode(), pc);
          res.endHandler(x -> proxyResponseImmediate(pc, null, bcontent, clientRequestList));
          res.exceptionHandler(e
              -> pc.warn("proxyRequestHttpClient: res exception (b)", e));
        }
      });
    });
  }

  private void proxyRequestLog(Iterator<ModuleInstance> it,
                               ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
                               List<HttpClientRequest> clientRequestList, ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    Future<HttpClientRequest> fut = httpClient.request(
        new RequestOptions().setMethod(ctx.request().method()).setAbsoluteURI(makeUrl(mi, ctx)));
    fut.onSuccess(clientRequest -> {
      clientRequestList.add(clientRequest);
      clientRequest.setChunked(true);
      String method = ctx.request().method().name();
      String path = mi.getRoutingEntry().getStaticPath();
      final Timer.Sample sample = MetricsHelper.getTimerSample();
      clientRequest.onFailure(e -> MetricsHelper.recordHttpClientError(pc.getTenant(),
          method, path));
      clientRequest.onSuccess(res -> MetricsHelper.recordHttpClientResponse(sample,
          pc.getTenant(), res.statusCode(), method, mi));
      if (!it.hasNext()) {
        relayToResponse(ctx.response(), null, pc);
        copyHeaders(clientRequest, ctx, mi);
        proxyResponseImmediate(pc, stream, bcontent, clientRequestList);
      } else {
        copyHeaders(clientRequest, ctx, mi);
        proxyR(it, pc, stream, bcontent, clientRequestList);
      }
      log(pc, clientRequest);
    });
  }

  private void proxyStreamToBuffer(ReadStream<Buffer> stream, Buffer bcontent,
                                   Handler<Buffer> handle) {
    if (bcontent != null) {
      handle.handle(bcontent);
    } else {
      final Buffer incoming = Buffer.buffer();
      stream.handler(incoming::appendBuffer);
      stream.endHandler(v -> handle.handle(incoming));
      stream.resume();
    }
  }

  private void proxyRequestOnly(Iterator<ModuleInstance> it,
                                ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
                                List<HttpClientRequest> clientRequestList, ModuleInstance mi) {

    proxyStreamToBuffer(stream, bcontent, res
        -> proxyRequestHttpClient(it, pc, res, clientRequestList, mi)
    );
  }

  private void proxyRequestResponse10(
      Iterator<ModuleInstance> it,
      ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
      List<HttpClientRequest> clientRequestList, ModuleInstance mi) {

    proxyStreamToBuffer(stream, bcontent, res
        -> proxyRequestResponse(it, pc, null, res, clientRequestList, mi)
    );
  }

  private void copyHeaders(HttpClientRequest clientRequest, RoutingContext ctx, ModuleInstance mi) {
    int sz = 0;
    int limit = 2000; // all headers dumped
    for (String name : ctx.request().headers().names()) {
      List<String> values = ctx.request().headers().getAll(name);
      if (values.size() > 1) {
        logger.warn("dup HTTP header {}: {}", name, values);
      }
      for (String value : values) {
        sz += name.length() + 4 + value.length(); // 4 for colon blank cr lf
      }
    }
    if (sz > limit && logger.isInfoEnabled()) {
      logger.info("Request headers size={}", sz);
      dumpHeaders(ctx.request().headers());
    }
    clientRequest.headers().setAll(ctx.request().headers());
    clientRequest.headers().remove("Content-Length");
    final String phase = mi.getRoutingEntry().getPhase();
    if (!XOkapiHeaders.FILTER_AUTH.equals(phase)) {
      clientRequest.headers().remove(XOkapiHeaders.ADDITIONAL_TOKEN);
    }
  }

  private static void dumpHeaders(MultiMap headers) {
    for (String name : headers.names()) {
      List<String> values = headers.getAll(name);
      for (String value : values) {
        logger.info("{}: {}", name, value);
      }
    }
  }

  private void fixupXOkapiToken(ModuleDescriptor md, MultiMap reqHeaders, MultiMap resHeaders) {
    String reqToken = reqHeaders.get(XOkapiHeaders.TOKEN);
    String resToken = resHeaders.get(XOkapiHeaders.TOKEN);
    if (resToken != null) {
      if (resToken.equals(reqToken)) {
        logger.warn("Removing X-Okapi-Token returned by module {} (RMB-478)", md.getId());
        resHeaders.remove(XOkapiHeaders.TOKEN);
      } else {
        logger.info("New X-Okapi-Token returned by module {}", md.getId());
      }
    }
  }

  private static void streamHandle(ProxyContext pc, ReadStream<Buffer> readStream,
                                   WriteStream<Buffer> mainWriteStream,
                                   List<HttpClientRequest> logWriteStreams) {
    List<WriteStream<Buffer>> writeStreams = new LinkedList<>();
    writeStreams.add(mainWriteStream);
    for (WriteStream<Buffer> w : logWriteStreams) {
      writeStreams.add(w);
    }
    pumpOneToMany(readStream, writeStreams);
    readStream.exceptionHandler(e
        -> pc.warn("streamHandle: content exception ", e));
    readStream.resume();
  }

  private static void pauseAndResume(ReadStream<Buffer> readStream,
                                     List<WriteStream<Buffer>> writeStreams) {
    boolean pause = false;

    for (WriteStream<Buffer> w : writeStreams) {
      if (w.writeQueueFull()) {
        w.drainHandler(handler -> pauseAndResume(readStream, writeStreams));
        pause = true;
      } else {
        w.drainHandler(null);
      }
    }
    if (pause) {
      readStream.pause();
    } else {
      readStream.resume();
    }
  }

  static void pumpOneToMany(ReadStream<Buffer> readStream,
                            List<WriteStream<Buffer>> writeStreams) {
    readStream.handler(data -> {
      for (WriteStream<Buffer> w : writeStreams) {
        w.write(data);
      }
      pauseAndResume(readStream, writeStreams);
    });
    readStream.endHandler(v -> {
      for (WriteStream<Buffer> w : writeStreams) {
        w.end();
      }
    });
  }

  private void proxyRequestResponse(Iterator<ModuleInstance> it,
                                    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
                                    List<HttpClientRequest> clientRequestList, ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    Future<HttpClientRequest> fut = httpClient.request(
        new RequestOptions().setMethod(ctx.request().method()).setAbsoluteURI(makeUrl(mi, ctx)));
    fut.onFailure(res -> proxyClientFailure(pc, mi, res));
    fut.onSuccess(clientRequest -> {
      final Timer.Sample sample = MetricsHelper.getTimerSample();
      copyHeaders(clientRequest, ctx, mi);
      if (bcontent != null) {
        pc.trace("proxyRequestResponse request buf '" + bcontent + "'");
        clientsEnd(bcontent, clientRequestList);
        clientRequest.end(bcontent);
      } else {
        clientRequest.setChunked(true);
        for (HttpClientRequest r : clientRequestList) {
          r.setChunked(true);
        }
        streamHandle(pc, stream, clientRequest, clientRequestList);
      }
      log(pc, clientRequest);
      clientRequest.onFailure(res -> proxyClientFailure(pc, mi, res));
      clientRequest.onSuccess(res -> {
        MetricsHelper.recordHttpClientResponse(sample, pc.getTenant(), res.statusCode(),
            ctx.request().method().name(), mi);
        fixupXOkapiToken(mi.getModuleDescriptor(), ctx.request().headers(), res.headers());
        Iterator<ModuleInstance> newIt = getNewIterator(it, mi, res.statusCode());
        if (res.getHeader(XOkapiHeaders.STOP) == null && newIt.hasNext()) {
          makeTraceHeader(mi, res.statusCode(), pc);
          relayToRequest(res, pc, mi);
          final String ct = res.getHeader("Content-Type");
          if (ct != null) {
            ctx.request().headers().set("Content-Type", ct);
          }
          storeResponseInfo(pc, mi, res);
          res.pause();
          proxyR(newIt, pc, res, null, new LinkedList<>());
        } else {
          relayToResponse(ctx.response(), res, pc);
          makeTraceHeader(mi, res.statusCode(), pc);
          proxyResponseImmediate(pc, res, null, new LinkedList<>());
        }
      });
    });
  }

  private void proxyHeaders(Iterator<ModuleInstance> it, ProxyContext pc,
                            ReadStream<Buffer> stream, Buffer bcontent,
                            List<HttpClientRequest> clientRequestList, ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    Future<HttpClientRequest> fut = httpClient.request(
        new RequestOptions().setMethod(ctx.request().method()).setAbsoluteURI(makeUrl(mi, ctx)));
    fut.onFailure(res -> proxyClientFailure(pc, mi, res));
    fut.onSuccess(clientRequest -> {
      final Timer.Sample sample = MetricsHelper.getTimerSample();
      copyHeaders(clientRequest, ctx, mi);
      clientRequest.end();
      log(pc, clientRequest);
      clientRequest.onFailure(res -> proxyClientFailure(pc, mi, res));
      clientRequest.onSuccess(res -> {
        MetricsHelper.recordHttpClientResponse(sample, pc.getTenant(), res.statusCode(),
            ctx.request().method().name(), mi);
        Iterator<ModuleInstance> newIt = getNewIterator(it, mi, res.statusCode());
        if (newIt.hasNext()) {
          relayToRequest(res, pc, mi);
          storeResponseInfo(pc, mi, res);
          makeTraceHeader(mi, res.statusCode(), pc);
          res.endHandler(x
              -> proxyR(newIt, pc, stream, bcontent, clientRequestList));
        } else {
          relayToResponse(ctx.response(), res, pc);
          makeTraceHeader(mi, res.statusCode(), pc);
          if (res.statusCode() >= 200 && res.statusCode() <= 299) {
            proxyResponseImmediate(pc, stream, bcontent, clientRequestList);
          } else {
            proxyResponseImmediate(pc, res, null, clientRequestList);
            if (bcontent == null) {
              stream.resume();
            }
          }
        }
      });
    });
  }

  private void proxyRedirect(Iterator<ModuleInstance> it,
                             ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
                             List<HttpClientRequest> clientRequestList, ModuleInstance mi) {

    pc.trace("ProxyNull " + mi.getModuleDescriptor().getId());
    pc.closeTimer();
    // if no more entries in it, proxyR will return 404
    proxyR(it, pc, stream, bcontent, clientRequestList);
  }

  private void proxyInternal(Iterator<ModuleInstance> it,
                             ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
                             List<HttpClientRequest> clientRequestList, ModuleInstance mi) {

    proxyStreamToBuffer(stream, bcontent, res
        -> proxyInternalBuffer(it, pc, res, clientRequestList, mi)
    );
  }

  private void proxyInternalBuffer(
      Iterator<ModuleInstance> it,
      ProxyContext pc, Buffer bcontent, List<HttpClientRequest> clientRequestList,
      ModuleInstance mi) {
    String req = bcontent.toString();
    pc.debug("proxyInternalBuffer " + req);
    RoutingContext ctx = pc.getCtx();

    clientsEnd(bcontent, clientRequestList);
    internalModule.internalService(req, pc, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
        return;
      }
      String resp = res.result();
      int statusCode = pc.getCtx().response().getStatusCode();
      if (statusCode == 200 && resp.isEmpty()) {
        // Say "no content", if there isn't any
        statusCode = 204;
        pc.getCtx().response().setStatusCode(statusCode);
      }
      Buffer respBuf = Buffer.buffer(resp);
      pc.setHandlerRes(statusCode);
      makeTraceHeader(mi, statusCode, pc);
      if (it.hasNext()) { // carry on with the pipeline
        proxyR(it, pc, null, respBuf, new LinkedList<>());
      } else { // produce a result
        pc.closeTimer();
        ctx.response().end(respBuf);
      }
    });
  }

  private void proxyR(Iterator<ModuleInstance> it,
                      ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
                      List<HttpClientRequest> clientRequestList) {

    RoutingContext ctx = pc.getCtx();
    if (!it.hasNext()) {
      stream.resume();
      pc.debug("proxyR: Not found");
      pc.responseError(404, ""); // Should have been caught earlier
    } else {
      ModuleInstance mi = it.next();
      pc.startTimer();

      // Pass the right token
      ctx.request().headers().remove(XOkapiHeaders.TOKEN);
      String token = mi.getAuthToken();
      if (token != null && !token.isEmpty()) {
        ctx.request().headers().add(XOkapiHeaders.TOKEN, token);
      }

      String userId = mi.getxOkapiUserId();
      if (userId != null) {
        ctx.request().headers().remove(XOkapiHeaders.USER_ID);
        ctx.request().headers().add(XOkapiHeaders.USER_ID, userId);
        pc.debug("Using X-Okapi-User-Id: " + userId);
      }

      String perms = mi.getxOkapiPermissions();
      if (perms != null) {
        ctx.request()
            .headers()
            .remove(XOkapiHeaders.PERMISSIONS);
        ctx.request()
            .headers()
            .add(XOkapiHeaders.PERMISSIONS, perms);
        pc.debug("Using X-Okapi-Permissions: " + perms);
      }

      // Pass headers for filters
      passFilterHeaders(ctx, pc, mi);

      // Do proxy work
      ProxyType proxyType = mi.getRoutingEntry().getProxyType();
      if (proxyType != ProxyType.REDIRECT) {
        pc.debug("Invoking module " + mi.getModuleDescriptor().getId()
            + " type " + proxyType
            + " level " + mi.getRoutingEntry().getPhaseLevel()
            + " path " + mi.getPath()
            + " url " + mi.getUrl());
      }
      final String pathPattern = mi.getRoutingEntry().getPathPattern();
      if (pathPattern != null) {
        ctx.request().headers().set(XOkapiHeaders.MATCH_PATH_PATTERN, pathPattern);
      }
      switch (proxyType) {
        case REQUEST_ONLY:
          proxyRequestOnly(it, pc, stream, bcontent, clientRequestList, mi);
          break;
        case REQUEST_RESPONSE:
          proxyRequestResponse(it, pc, stream, bcontent, clientRequestList, mi);
          break;
        case HEADERS:
          proxyHeaders(it, pc, stream, bcontent, clientRequestList, mi);
          break;
        case REDIRECT:
          proxyRedirect(it, pc, stream, bcontent, clientRequestList, mi);
          break;
        case INTERNAL:
          proxyInternal(it, pc, stream, bcontent, clientRequestList, mi);
          break;
        case REQUEST_RESPONSE_1_0:
          proxyRequestResponse10(it, pc, stream, bcontent, clientRequestList, mi);
          break;
        case REQUEST_LOG:
          proxyRequestLog(it, pc, stream, bcontent, clientRequestList, mi);
          break;
        default:
          // Should not happen
          pc.responseError(500, messages.getMessage("10110",
              proxyType, mi.getModuleDescriptor().getId()));
          break;
      }
    }
  }

  private void passFilterHeaders(RoutingContext ctx, ProxyContext pc, ModuleInstance mi) {
    // Pass the X-Okapi-Filter header for filters (only)
    // And all kind of things for the auth filter
    MultiMap headers = ctx.request().headers();
    headers.remove(XOkapiHeaders.FILTER);

    final String phase = mi.getRoutingEntry().getPhase();
    if (phase != null) {
      String pth = mi.getRoutingEntry().getPathPattern();
      if (pth == null) {
        pth = mi.getRoutingEntry().getPath();
      }
      String filt = mi.getRoutingEntry().getPhase() + " " + pth;
      pc.debug("Adding " + XOkapiHeaders.FILTER + ": " + filt);
      // The auth filter needs all kinds of special headers
      headers.add(XOkapiHeaders.FILTER, filt);

      boolean badAuth = pc.getAuthRes() != 0 && (pc.getAuthRes() < 200 || pc.getAuthRes() >= 300);
      switch (phase) {
        case XOkapiHeaders.FILTER_AUTH:
          authHeaders(pc.getModList(), headers, pc);
          break;
        case XOkapiHeaders.FILTER_PRE:
          // pass request headers and failed auth result
          if (badAuth) {
            headers.add(XOkapiHeaders.AUTH_RESULT, "" + pc.getAuthRes());
          }
          break;
        case XOkapiHeaders.FILTER_POST:
          // pass request headers and failed handler/auth result
          if (pc.getHandlerRes() > 0) {
            String hresult = String.valueOf(pc.getHandlerRes());
            headers.set(XOkapiHeaders.HANDLER_RESULT, hresult);
            headers.set(XOkapiHeaders.HANDLER_HEADERS, Json.encode(pc.getHandlerHeaders()));
          } else if (badAuth) {
            headers.set(XOkapiHeaders.AUTH_RESULT, "" + pc.getAuthRes());
            headers.set(XOkapiHeaders.AUTH_HEADERS, Json.encode(pc.getAuthHeaders()));
          } else {
            logger.warn("proxyR: postHeader: Oops, no result to pass to post handler");
          }
          break;
        default:
          logger.error("Not supported phase: {}", phase);
          break;
      }
    }
  }

  private static DeploymentDescriptor pickInstance(List<DeploymentDescriptor> instances) {
    int sz = instances.size();
    return sz > 0 ? instances.get(random.nextInt(sz)) : null;
  }

  /**
   * Make a request to a system interface, like _tenant. Part 1: Check that we
   * are working as the right tenant, and if not so, change identity to the
   * correct one.
   *
   * @param tenant to make the request for
   * @param inst carries the moduleDescriptor, RoutingEntry, and getPath to be called
   * @param request body to send in the request
   * @param pc ProxyContext for logging, and returning resp headers
   * @param fut Callback with the OkapiClient that contains the body, headers,
   *     and/or errors
   */
  public void callSystemInterface(Tenant tenant, ModuleInstance inst,
                                  String request, ProxyContext pc,
                                  Handler<AsyncResult<OkapiClient>> fut) {

    MultiMap headersIn = pc.getCtx().request().headers();
    callSystemInterface(headersIn, tenant, inst, request, fut);
  }

  void callSystemInterface(MultiMap headersIn,
                           Tenant tenant, ModuleInstance inst,
                           String request, Handler<AsyncResult<OkapiClient>> fut) {

    if (!headersIn.contains(XOkapiHeaders.URL)) {
      headersIn.set(XOkapiHeaders.URL, okapiUrl);
    }
    String tenantId = tenant.getId(); // the tenant we are about to enable
    // Check if the actual tenant has auth enabled. If yes, get a token for it.
    // If we have auth for current (super)tenant is irrelevant here!
    logger.debug("callSystemInterface: Checking if {} has auth", tenantId);

    moduleManager.getEnabledModules(tenant, mres -> {
      if (mres.failed()) { // Should not happen
        fut.handle(new Failure<>(mres.getType(), mres.cause()));
        return;
      }
      List<ModuleDescriptor> enabledModules = mres.result();
      for (ModuleDescriptor md : enabledModules) {
        RoutingEntry[] filters = md.getFilters();
        if (filters != null) {
          for (RoutingEntry filt : filters) {
            if (XOkapiHeaders.FILTER_AUTH.equals(filt.getPhase())) {
              logger.debug("callSystemInterface: Found auth filter in {}", md.getId());
              authForSystemInterface(md, filt, tenantId, inst, request, headersIn, fut);
              return;
            }
          }
        }
      }
      logger.debug("callSystemInterface: No auth for {} calling with "
          + "tenant header only", tenantId);
      doCallSystemInterface(headersIn, tenantId, null, inst, null, request, fut);
    });
  }

  /**
   * Helper to get a new authtoken before invoking doCallSystemInterface.
   *
   */
  private void authForSystemInterface(
      ModuleDescriptor authMod, RoutingEntry filt,
      String tenantId, ModuleInstance inst, String request, MultiMap headers,
      Handler<AsyncResult<OkapiClient>> fut) {
    logger.debug("Calling doCallSystemInterface to get auth token");
    RoutingEntry re = inst.getRoutingEntry();
    String modPerms = "";
    String modId = inst.getModuleDescriptor().getId();
    if (re != null) {
      String[] modulePermissions = re.getModulePermissions();
      Map<String, String[]> mpMap = new HashMap<>();
      if (modulePermissions != null) {
        // replace module permissions with auto generated permission set id
        if (moduleManager.getExpandedPermModuleTenants().contains(tenantId)) {
          modulePermissions = new String[] {re.generateSystemId(modId)};
        }
        mpMap.put(modId, modulePermissions);
        logger.debug("authForSystemInterface: Found modPerms: {}", modPerms);
      } else {
        logger.debug("authForSystemInterface: Got RoutingEntry, but null modulePermissions");
      }
      modPerms = Json.encode(mpMap);
    } else {
      logger.debug("authForSystemInterface: re is null, can't find modPerms");
    }
    ModuleInstance authInst = new ModuleInstance(authMod, filt, inst.getPath(),
        inst.getMethod(), inst.isHandler());
    doCallSystemInterface(headers, tenantId, null, authInst, modPerms, "", res -> {
      if (res.failed()) {
        fut.handle(res);
        return;
      }
      OkapiClient cli = res.result();
      String deftok = cli.getRespHeaders().get(XOkapiHeaders.TOKEN);
      logger.debug("authForSystemInterface: {}",
          () -> Json.encode(cli.getRespHeaders().entries()));
      String modTok = cli.getRespHeaders().get(XOkapiHeaders.MODULE_TOKENS);
      JsonObject jo = new JsonObject(modTok);
      String token = jo.getString(modId, deftok);
      logger.debug("authForSystemInterface: Got token {}", token);
      doCallSystemInterface(headers, tenantId, token, inst, null, request, fut);
    });
  }

  /**
   * Actually make a request to a system interface, like _tenant. Assumes we are
   * operating as the correct tenant.
   */
  private void doCallSystemInterface(
      MultiMap headersIn,
      String tenantId, String authToken, ModuleInstance inst, String modPerms,
      String request, Handler<AsyncResult<OkapiClient>> fut) {
    discoveryManager.getNonEmpty(inst.getModuleDescriptor().getId(), gres -> {
      DeploymentDescriptor instance = null;
      if (gres.succeeded()) {
        instance = pickInstance(gres.result());
      }
      if (instance == null) {
        fut.handle(Future.failedFuture(messages.getMessage("11100",
            inst.getModuleDescriptor().getId(), inst.getPath())));
        return;
      }
      String baseurl = instance.getUrl();
      Map<String, String> headers = sysReqHeaders(headersIn, tenantId, authToken, inst, modPerms);
      headers.put(XOkapiHeaders.URL_TO, baseurl);
      logger.info("syscall begin {} {}{}", inst.getMethod(), baseurl, inst.getPath());
      OkapiClient cli = new OkapiClient(this.httpClient, baseurl, vertx, headers);
      String reqId = inst.getPath().replaceFirst("^[/_]*([^/]+).*", "$1");
      cli.newReqId(reqId); // "tenant" or "tenantpermissions"
      cli.enableInfoLog();
      if (inst.isWithRetry()) {
        cli.setClosedRetry(40000);
      }
      final Timer.Sample sample = MetricsHelper.getTimerSample();
      cli.request(inst.getMethod(), inst.getPath(), request, cres -> {
        logger.info("syscall return {} {}{}", inst.getMethod(), baseurl, inst.getPath());
        if (cres.failed()) {
          String msg = messages.getMessage("11101", inst.getMethod(),
              inst.getModuleDescriptor().getId(), inst.getPath(), cres.cause().getMessage());
          logger.warn(msg);
          MetricsHelper.recordHttpClientError(tenantId, inst.getMethod().name(), inst.getPath());
          fut.handle(Future.failedFuture(msg));
          return;
        }
        MetricsHelper.recordHttpClientResponse(sample, tenantId, cli.getStatusCode(),
            inst.getMethod().name(), inst);
        // Pass response headers - needed for unit test, if nothing else
        fut.handle(Future.succeededFuture(cli));
      });
    });
  }

  /**
   * Helper to make request headers for the system requests we make. Copies all
   * X- headers over. Adds a tenant, and a token, if we have one.
   */
  private static Map<String, String> sysReqHeaders(
      MultiMap headersIn, String tenantId, String authToken,
      ModuleInstance inst, String modPerms) {
    Map<String, String> headersOut = new HashMap<>();
    for (String hdr : headersIn.names()) {
      if (hdr.matches("^X-.*$")) {
        headersOut.put(hdr, headersIn.get(hdr));
      }
    }
    headersOut.put(XOkapiHeaders.TENANT, tenantId);
    logger.debug("Added {} : {}", XOkapiHeaders.TENANT, tenantId);
    if (authToken == null) {
      headersOut.remove(XOkapiHeaders.TOKEN);
    } else {
      headersOut.put(XOkapiHeaders.TOKEN, authToken);
    }
    headersOut.put("Accept", "*/*");
    headersOut.put("Content-Type", "application/json; charset=UTF-8");
    if (modPerms != null) { // We are making an auth call
      RoutingEntry re = inst.getRoutingEntry();
      if (re != null) {
        headersOut.put(XOkapiHeaders.FILTER, re.getPhase());
      }
      if (!modPerms.isEmpty()) {
        headersOut.put(XOkapiHeaders.MODULE_PERMISSIONS, modPerms);
      }
      // Clear the permissions-required header that we inherited from the
      // original request (e.g. to tenant-enable), as we do not have those
      // perms set in the target tenant
      headersOut.remove(XOkapiHeaders.PERMISSIONS_REQUIRED);
      headersOut.remove(XOkapiHeaders.PERMISSIONS_DESIRED);
      logger.debug("Auth call, some tricks with permissions");
    }
    return headersOut;
  }

  /**
   * Extract tenantId from the request, rewrite the getPath, and proxy it.
   * Expects a request to something like /_/proxy/tenant/{tid}/mod-something.
   * Rewrites that to /mod-something, with the tenantId passed in the proper
   * header. As there is no authtoken, this will not work for many things, but
   * is needed for callbacks in the SSO systems, and who knows what else.
   *
   * @param ctx Routing Context
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
    }
    ctx.request().headers().add(XOkapiHeaders.TENANT, tid);
    pc.debug("redirectProxy: '" + tid + "' '" + newPath + "'");
    ctx.reroute(newPath);
  }

  public void autoDeploy(ModuleDescriptor md,
                         Handler<ExtendedAsyncResult<Void>> fut) {

    discoveryManager.autoDeploy(md, fut);
  }

  public void autoUndeploy(ModuleDescriptor md,
                           Handler<ExtendedAsyncResult<Void>> fut) {

    discoveryManager.autoUndeploy(md, fut);
  }

  // store Auth/Handler response, and pass header as needed
  private static void storeResponseInfo(ProxyContext pc, ModuleInstance mi,
                                        HttpClientResponse res) {
    String phase = mi.getRoutingEntry().getPhase();
    // It was a real handler, remember the response code and headers
    if (mi.isHandler()) {
      pc.setHandlerRes(res.statusCode());
      pc.getHandlerHeaders().setAll(res.headers());
    } else if (XOkapiHeaders.FILTER_AUTH.equalsIgnoreCase(phase)) {
      pc.setAuthRes(res.statusCode());
      pc.getAuthHeaders().setAll(res.headers());
      pc.setAuthResBody(Buffer.buffer());
      res.handler(data -> pc.getAuthResBody().appendBuffer(data));
    }
  }

  // skip handler, but not if at pre/post filter phase
  private static Iterator<ModuleInstance> getNewIterator(Iterator<ModuleInstance> it,
                                                         ModuleInstance mi, int statusCode) {

    if (statusCode >= 200 && statusCode <= 299) {
      return it;
    }
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
