package org.folio.okapi.managers;

import io.micrometer.core.instrument.Timer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
import org.folio.okapi.ConfNames;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.RoutingEntry.ProxyType;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.OkapiToken;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.logging.FolioLoggingContext;
import org.folio.okapi.util.CorsHelper;
import org.folio.okapi.util.FuturisedHttpClient;
import org.folio.okapi.util.MetricsHelper;
import org.folio.okapi.util.ModuleCache;
import org.folio.okapi.util.OkapiError;
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

  private final TenantManager tenantManager;
  private final DiscoveryManager discoveryManager;
  private final InternalModule internalModule;
  private final String okapiUrl;
  private final Vertx vertx;
  private final FuturisedHttpClient httpClient;
  // for load balancing, so security is not an issue
  private static final Random random = new Random();
  private final int waitMs;
  private final boolean enableSystemAuth;
  private final boolean enableTraceHeaders;
  private static final String REDIRECTQUERY = "redirect-query"; // See redirectProxy below
  private static final String TOKEN_CACHE_MAX_SIZE = "token_cache_max_size";
  private static final String TOKEN_CACHE_TTL_MS = "token_cache_ttl_ms";
  private static final Messages messages = Messages.getInstance();
  private static final Comparator<ModuleInstance> compareInstanceLevel =
      Comparator.comparing((ModuleInstance a) -> a.getRoutingEntry().getPhaseLevel());
  private final TokenCache tokenCache;

  // request + response HTTP headers that are forwarded in the pipeline
  private static final String [] FORWARD_HEADERS =
      new String [] { "Content-Type", "Content-Encoding"};

  /**
   * Construct Proxy service.
   * @param vertx Vert.x handle
   * @param tm tenant manager
   * @param dm discovery manager
   * @param im internal module
   * @param okapiUrl Okapi URL
   * @param config configuration
   */
  public ProxyService(Vertx vertx, TenantManager tm, DiscoveryManager dm, InternalModule im,
                      String okapiUrl, JsonObject config) {
    this.vertx = vertx;
    this.tenantManager = tm;
    this.internalModule = im;
    this.discoveryManager = dm;
    this.okapiUrl = okapiUrl;
    waitMs = config.getInteger("logWaitMs", 0);
    enableSystemAuth = Config.getSysConfBoolean(ConfNames.ENABLE_SYSTEM_AUTH, true, config);
    enableTraceHeaders = Config.getSysConfBoolean(ConfNames.ENABLE_TRACE_HEADERS, false, config);
    HttpClientOptions opt = new HttpClientOptions();
    opt.setMaxPoolSize(1000);
    httpClient = new FuturisedHttpClient(vertx, opt);

    String tcTtlMs = Config.getSysConf(TOKEN_CACHE_TTL_MS, null, config);
    String tcMaxSize = Config.getSysConf(TOKEN_CACHE_MAX_SIZE, null, config);
    tokenCache = TokenCache.builder()
        .withTtl(tcTtlMs != null ? Long.parseLong(tcTtlMs) : TokenCache.DEFAULT_TTL)
        .withMaxSize(tcMaxSize != null ? Integer.parseInt(tcMaxSize) : TokenCache.DEFAULT_MAX_SIZE)
        .build();
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
    if (enableTraceHeaders) {
      pc.addTraceHeaderLine(ctx.request().method() + " "
          + mi.getModuleDescriptor().getId() + " "
          + url.replaceFirst("[?#].*$", "..") // remove params
          + " : " + statusCode + " " + pc.timeDiff());
    }
    pc.logResponse(mi.getModuleDescriptor().getId(), url, statusCode);
  }

  /** Get path key for token.
   *
   * @param re routing entry.
   * @param path actual HTTP request path.
   * @return path key for token cache.
   */
  static String getTokenPath(RoutingEntry re, String path) {
    // only use path pattern if that's given and if permissions required do not depend
    // on path itself.
    return re.getPathPattern() != null && re.getPermissionsRequiredTenant() == null
        ? re.getPathPattern()
        : path;
  }

  /**
   * Checks for a cached token, userId, permissions and updates the provided ModuleInstance
   * accordingly.
   *
   * @param tenant The tenant, used to tag cache event metrics
   * @param req Request, used for accessing headers, etc.
   * @param re routing entry.
   * @param mi ModuleInstance to be updated
   * @return <code>true</code> if the ModuleInstance is updated with cached values,
   *         <code>false</code> otherwise.
   */
  private boolean checkTokenCache(String tenant, HttpServerRequest req, RoutingEntry re,
      ModuleInstance mi) {
    String token = req.headers().get(XOkapiHeaders.TOKEN);
    if (token == null) {
      mi.setAuthToken(null);
      mi.setUserId(null);
      mi.setPermissions(null);
      return false;
    }

    String pathComponent = getTokenPath(re, req.path());

    CacheEntry cached = tokenCache.get(tenant, req.method().name(),
            pathComponent, req.getHeader(XOkapiHeaders.USER_ID),
            token);

    if (cached != null) {
      mi.setAuthToken(cached.token);
      mi.setUserId(cached.userId);
      mi.setPermissions(cached.permissions);
      return true;
    } else {
      mi.setAuthToken(req.headers().get(XOkapiHeaders.TOKEN));
      mi.setUserId(null);
      mi.setPermissions(null);
      return false;
    }
  }

  private List<ModuleInstance> getModulesForRequest(ProxyContext pc, ModuleCache moduleCache) {
    HttpServerRequest req = pc.getCtx().request();
    final String id = req.getHeader(XOkapiHeaders.MODULE_ID);
    List<ModuleInstance> mods;
    try {
      mods = moduleCache.lookup(req.uri(), req.method(), id);
    } catch (IllegalArgumentException e) {
      pc.responseError(500, e.getMessage());
      return null;
    }
    boolean skipAuth = false;
    for (ModuleInstance mi : mods) {
      if (mi.isHandler()) {
        pc.setHandlerModuleInstance(mi);
        RoutingEntry re = mi.getRoutingEntry();
        skipAuth = checkTokenCache(pc.getTenant(), req, re, mi);
        logger.debug("getModulesForRequest:  Added {} {} {} {} / {}",
            mi.getModuleDescriptor().getId(),
            re.getPathPattern(), re.getPath(), re.getPhase(), re.getLevel());
      } else {
        mi.setAuthToken(req.headers().get(XOkapiHeaders.TOKEN));
      }
    }
    mods.sort(compareInstanceLevel);
    Iterator<ModuleInstance> iter = mods.iterator();
    boolean found = false;
    while (iter.hasNext()) {
      ModuleInstance inst = iter.next();
      RoutingEntry re = inst.getRoutingEntry();
      String phase = re.getPhase();

      logger.debug("getModulesForRequest: Checking {} '{}' '{}'",
          re.getPathPattern(), phase, re.getLevel());

      if (skipAuth && phase != null && phase.equals(XOkapiHeaders.FILTER_AUTH)) {
        logger.debug("Skipping auth, have cached token.");
        iter.remove();
      }
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
      logger.debug("Moved Authorization header to X-Okapi-Token");
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
          // ignoring bad token
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
        logger.debug("Recovered tenant from token: '{}'", tenantId);
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

      if (re.getPermissionsRequiredTenant() != null
          && mod.getAuthToken() != null
          && re.matchUriTenant(mod.getPath(), pc.getTenant())) {
        req.addAll(Arrays.asList(re.getPermissionsRequiredTenant()));
      } else {
        String[] reqp = re.getPermissionsRequired();
        if (reqp != null) {
          req.addAll(Arrays.asList(reqp));
        }
      }
      String[] wap = re.getPermissionsDesired();
      if (wap != null) {
        want.addAll(Arrays.asList(wap));
      }
      String[] modp = re.getModulePermissions();
      if (modp != null) {
        // replace module permissions with auto generated permission set id
        if (Boolean.TRUE.equals(tenantManager.getExpandModulePermissions(pc.getTenant()))) {
          modp = new String[]{re.generateSystemId(mod.getModuleDescriptor().getId())};
        }
        if (re.getProxyType() == ProxyType.REDIRECT) {
          extraperms.addAll(Arrays.asList(modp));
        } else {
          modperms.put(mod.getModuleDescriptor().getId(), modp);
        }
      }
    } // mod loop
    if (!req.isEmpty()) {
      logger.debug("authHeaders: {} {}",
          () -> XOkapiHeaders.PERMISSIONS_REQUIRED,
          () -> String.join(",", req));
      requestHeaders.add(XOkapiHeaders.PERMISSIONS_REQUIRED, String.join(",", req));
    }
    if (!want.isEmpty()) {
      logger.debug("authHeaders: {} {}",
          () -> XOkapiHeaders.PERMISSIONS_DESIRED,
          () -> String.join(",", want));
      requestHeaders.add(XOkapiHeaders.PERMISSIONS_DESIRED, String.join(",", want));
    }
    // Add the X-Okapi-Module-Permissions even if empty. That causes auth to return
    // an empty X-Okapi-Module-Token, which will tell us that we have done the mod
    // perms, and no other module should be allowed to do the same.
    String mpj = Json.encode(modperms);
    logger.debug("authHeaders: {} {}", XOkapiHeaders.MODULE_PERMISSIONS, mpj);
    requestHeaders.add(XOkapiHeaders.MODULE_PERMISSIONS, mpj);
    if (!extraperms.isEmpty()) {
      String epj = Json.encode(extraperms);
      logger.debug("authHeaders: {} {}", XOkapiHeaders.EXTRA_PERMISSIONS, epj);
      requestHeaders.add(XOkapiHeaders.EXTRA_PERMISSIONS, epj);
    }
  }

  private Future<Void> resolveUrls(List<ModuleInstance> instances) {
    Future<Void> future = Future.succeededFuture();
    for (ModuleInstance instance : instances) {
      if (instance.getRoutingEntry().getProxyType() == ProxyType.INTERNAL) {
        instance.setUrl("");
      } else {
        future = future.compose(x -> discoveryManager.get(instance.getModuleDescriptor().getId())
            .compose(res -> {
              DeploymentDescriptor dd = pickInstance(res);
              if (dd == null) {
                return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND,
                    "No running module instance found for "
                        + instance.getModuleDescriptor().getId()));
              }
              instance.setUrl(dd.getUrl());
              return Future.succeededFuture();
            }));
      }
    }
    return future;
  }

  private void relayToResponse(HttpServerResponse hres,
                               HttpClientResponse res, ProxyContext pc) {
    if (pc.getHandlerRes() != 0) {
      hres.setStatusCode(pc.getHandlerRes());
      hres.headers().addAll(pc.getHandlerHeaders());
    } else if (pc.getAuthRes() != 0 && !statusOk(pc.getAuthRes())) {
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
    Timer.Sample sample = MetricsHelper.getTimerSample();
    String modTok = res.headers().get(XOkapiHeaders.MODULE_TOKENS);
    if (modTok != null) {
      // Have module tokens: save them for this request even in case of error
      HttpServerRequest req = pc.getCtx().request();
      String originalToken = req.getHeader(XOkapiHeaders.TOKEN);
      JsonObject jo = new JsonObject(modTok);
      for (ModuleInstance mi : pc.getModList()) {
        String id = mi.getModuleDescriptor().getId();
        RoutingEntry routingEntry = mi.getRoutingEntry();

        String tok;
        if (jo.containsKey(id)) {
          tok = jo.getString(id);
          mi.setAuthToken(tok);
          logger.debug("authResponse: token for {}: {}", id, tok);
        } else if (jo.containsKey("_")) {
          tok = jo.getString("_");
          logger.debug("authResponse: Default (_) token for {}: {}", id, tok);
        } else {
          continue;
        }

        mi.setAuthToken(tok);

        // Only save in cache if no error from auth and there's a token to save
        if (statusOk(res) && originalToken != null) {
          tokenCache.put(pc.getTenant(),
              req.method().name(),
              getTokenPath(routingEntry, req.path()),
              res.getHeader(XOkapiHeaders.USER_ID),
              res.getHeader(XOkapiHeaders.PERMISSIONS),
              originalToken,
              tok);
        }
      }
    }
    MetricsHelper.recordCodeExecutionTime(sample, "ProxyService.authResponse");
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
    if (XOkapiHeaders.FILTER_AUTH.equals(mi.getRoutingEntry().getPhase())) {
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

  private void log(HttpClientRequest creq) {
    logger.debug("{} {}", creq.getMethod().name(), creq.getURI());
    for (Map.Entry<String, String> next : creq.headers()) {
      logger.debug(" {}:{}", next.getKey(), next.getValue());
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
    final MultiMap headers = ctx.request().headers();

    // It would be nice to pass the request-id to the client, so it knows what
    // to look for in Okapi logs. But that breaks the schemas, and RMB-based
    // modules will not accept the response. Maybe later...
    try {
      parseTokenAndPopulateContext(pc);
      putAndRejectMdcLookups(pc,
          FolioLoggingContext.TENANT_ID_LOGGING_VAR_NAME, pc.getTenant());
      putAndRejectMdcLookups(pc,
          FolioLoggingContext.REQUEST_ID_LOGGING_VAR_NAME, headers.get(XOkapiHeaders.REQUEST_ID));
      putAndRejectMdcLookups(pc,
          FolioLoggingContext.MODULE_ID_LOGGING_VAR_NAME, headers.get(XOkapiHeaders.MODULE_ID));
      putAndRejectMdcLookups(pc,
          FolioLoggingContext.USER_ID_LOGGING_VAR_NAME, pc.getUserId());
    } catch (IllegalArgumentException e) {
      stream.resume();
      return; // Error code already set in ctx
    }
    String tenantId = pc.getTenant();
    sanitizeAuthHeaders(headers);
    tenantManager.get(tenantId)
        .onFailure(cause -> {
          stream.resume();
          pc.responseError(400, messages.getMessage("10106", tenantId));
        })
        .onSuccess(tenant -> {
          final Timer.Sample sample = MetricsHelper.getTimerSample();
          List<ModuleInstance> l = getModulesForRequest(pc,
              tenantManager.getModuleCache(tenant.getId()));
          MetricsHelper.recordCodeExecutionTime(sample,
              "ProxyService.getModulesForRequest");
          if (l == null) {
            stream.resume();
            return; // ctx already set up
          }

          pc.setModList(l);

          pc.logRequest(ctx, tenantId);

          headers.set(XOkapiHeaders.URL, okapiUrl);
          headers.remove(XOkapiHeaders.MODULE_ID);
          headers.set(XOkapiHeaders.REQUEST_IP, ctx.request().remoteAddress().host());
          headers.set(XOkapiHeaders.REQUEST_TIMESTAMP, "" + System.currentTimeMillis());
          headers.set(XOkapiHeaders.REQUEST_METHOD, ctx.request().method().name());

          resolveUrls(l).onFailure(cause -> {
            stream.resume();
            pc.responseError(OkapiError.getType(cause), cause);
          }).onSuccess(res -> {
            List<HttpClientRequest> clientRequest = new LinkedList<>();
            proxyR(l.iterator(), pc, stream, null, clientRequest);
          });
        });
  }

  /**
   * Throw IllegalArgumentException if s contains ${ to disable MDC lookups
   * mitigating any denial of service attack using recursive lookups
   * (CVE-2021-45105, https://logging.apache.org/log4j/2.x/index.html ).
   * Otherwise put (name, s) into FolioLoggingContext.
   */
  private static void putAndRejectMdcLookups(ProxyContext pc, String name, String s) {
    if (s != null && s.contains("${")) {
      var e = new IllegalArgumentException(name + " must not contain ${");
      pc.responseError(400, e.getMessage());
      throw e;
    }
    FolioLoggingContext.put(name, s);
  }

  private static void clientsEnd(Buffer bcontent, List<HttpClientRequest> clientRequestList) {
    for (HttpClientRequest r : clientRequestList) {
      r.end(bcontent);
    }
  }

  private void proxyResponseImmediate(ProxyContext pc, ReadStream<Buffer> readStream,
                                      Buffer bcontent, List<HttpClientRequest> clientRequestList) {

    RoutingContext ctx = pc.getCtx();
    if (pc.getAuthRes() != 0 && !statusOk(pc.getAuthRes())) {
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
      streamHandle(readStream, ctx.response(), clientRequestList);
    }
    MetricsHelper.recordHttpServerProcessingTime(pc.getSample(), pc.getTenant(),
        ctx.response().getStatusCode(), ctx.request().method().name(),
        pc.getHandlerModuleInstance());
  }

  private void proxyClientFailure(
      ProxyContext pc, ModuleInstance mi, RequestOptions options, Throwable res) {

    String msg = res.getMessage() + ": " + options.getMethod() + " " + options.getURI();
    logger.warn("proxyClientFailure: {}: {}", mi.getUrl(), msg);
    MetricsHelper.recordHttpClientError(pc.getTenant(), mi.getMethod().name(),
        mi.getRoutingEntry().getStaticPath());
    pc.responseError(500, messages.getMessage("10107",
        mi.getModuleDescriptor().getId(), mi.getUrl(), msg));
  }

  private void proxyRequestHttpClient(
      Iterator<ModuleInstance> it,
      ProxyContext pc, Buffer bcontent, List<HttpClientRequest> clientRequestList,
      ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    String url = makeUrl(mi, ctx);
    HttpMethod meth = ctx.request().method();
    RequestOptions requestOptions = new RequestOptions().setMethod(meth).setAbsoluteURI(url);
    Future<HttpClientRequest> fut = httpClient.request(requestOptions);
    fut.onFailure(res -> proxyClientFailure(pc, mi, requestOptions, res));
    fut.onSuccess(clientRequest -> {
      final Timer.Sample sample = MetricsHelper.getTimerSample();
      copyHeaders(clientRequest, ctx, mi);
      logger.trace("ProxyRequestHttpClient request buf '{}'", bcontent);
      clientsEnd(bcontent, clientRequestList);
      clientRequest.end(bcontent);
      log(clientRequest);
      clientRequest.response()
          .onFailure(res -> proxyClientFailure(pc, mi, requestOptions, res))
          .onSuccess(res -> {
            MetricsHelper.recordHttpClientResponse(sample, pc.getTenant(), res.statusCode(),
                meth.name(), mi);
            Iterator<ModuleInstance> newIt = getNewIterator(it, mi, res);
            if (newIt.hasNext()) {
              makeTraceHeader(mi, res.statusCode(), pc);
              pc.closeTimer();
              relayToRequest(res, pc, mi);
              proxyR(newIt, pc, null, bcontent, new LinkedList<>());
            } else {
              relayToResponse(ctx.response(), res, pc);
              makeTraceHeader(mi, res.statusCode(), pc);
              res.endHandler(x -> proxyResponseImmediate(pc, null, bcontent, clientRequestList));
              res.exceptionHandler(e -> logger.warn("proxyRequestHttpClient: exception", e));
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
      clientRequest.response()
          .onFailure(e -> MetricsHelper.recordHttpClientError(pc.getTenant(), method, path))
          .onSuccess(res -> MetricsHelper.recordHttpClientResponse(sample,
              pc.getTenant(), res.statusCode(), method, mi));
      if (!it.hasNext()) {
        relayToResponse(ctx.response(), null, pc);
        copyHeaders(clientRequest, ctx, mi);
        proxyResponseImmediate(pc, stream, bcontent, clientRequestList);
      } else {
        copyHeaders(clientRequest, ctx, mi);
        proxyR(it, pc, stream, bcontent, clientRequestList);
      }
      log(clientRequest);
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
    if (sz > limit && logger.isDebugEnabled()) {
      logger.debug("Request headers size={}", sz);
      dumpHeaders(ctx.request().headers());
    }
    clientRequest.headers().setAll(ctx.request().headers());
    clientRequest.headers().remove("Content-Length");
    final String phase = mi == null ? "" : mi.getRoutingEntry().getPhase();
    if (!XOkapiHeaders.FILTER_AUTH.equals(phase)) {
      clientRequest.headers().remove(XOkapiHeaders.ADDITIONAL_TOKEN);
    }
  }

  private static void dumpHeaders(MultiMap headers) {
    for (String name : headers.names()) {
      List<String> values = headers.getAll(name);
      for (String value : values) {
        logger.debug("{}: {}", name, value);
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
        logger.debug("New X-Okapi-Token returned by module {}", md.getId());
      }
    }
  }

  private static void streamHandle(ReadStream<Buffer> readStream,
                                   WriteStream<Buffer> mainWriteStream,
                                   List<HttpClientRequest> logWriteStreams) {
    List<WriteStream<Buffer>> writeStreams = new LinkedList<>();
    writeStreams.add(mainWriteStream);
    writeStreams.addAll(logWriteStreams);
    pumpOneToMany(readStream, writeStreams);
    readStream.exceptionHandler(e -> logger.warn("streamHandle: content exception ", e));
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
    HttpServerRequest request = ctx.request();
    HttpServerResponse response = ctx.response();
    RequestOptions requestOptions =
        new RequestOptions().setMethod(request.method()).setAbsoluteURI(makeUrl(mi, ctx));
    Future<HttpClientRequest> fut = httpClient.request(requestOptions);
    fut.onFailure(res -> proxyClientFailure(pc, mi, requestOptions, res));
    fut.onSuccess(clientRequest -> {
      final Timer.Sample sample = MetricsHelper.getTimerSample();
      copyHeaders(clientRequest, ctx, mi);
      if (bcontent != null) {
        logger.trace("proxyRequestResponse request buf '{}'", bcontent);
        clientsEnd(bcontent, clientRequestList);
        clientRequest.end(bcontent);
      } else {
        clientRequest.setChunked(true);
        for (HttpClientRequest r : clientRequestList) {
          r.setChunked(true);
        }
        streamHandle(stream, clientRequest, clientRequestList);
      }
      log(clientRequest);
      clientRequest.response()
          .onFailure(res -> proxyClientFailure(pc, mi, requestOptions, res))
          .onSuccess(res -> {
            MetricsHelper.recordHttpClientResponse(sample, pc.getTenant(), res.statusCode(),
                request.method().name(), mi);
            fixupXOkapiToken(mi.getModuleDescriptor(), request.headers(), res.headers());
            Iterator<ModuleInstance> newIt = getNewIterator(it, mi, res);
            if (res.getHeader(XOkapiHeaders.STOP) == null && newIt.hasNext()) {
              makeTraceHeader(mi, res.statusCode(), pc);
              relayToRequest(res, pc, mi);
              for (String header : FORWARD_HEADERS) {
                request.headers().set(header, res.getHeader(header));
              }
              storeResponseInfo(pc, mi, res);
              res.pause();
              proxyR(newIt, pc, res, null, new LinkedList<>());
            } else {
              relayToResponse(response, res, pc);
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
    RequestOptions requestOptions =
        new RequestOptions().setMethod(ctx.request().method()).setAbsoluteURI(makeUrl(mi, ctx));
    Future<HttpClientRequest> fut = httpClient.request(requestOptions);
    fut.onFailure(res -> proxyClientFailure(pc, mi, requestOptions, res));
    fut.onSuccess(clientRequest -> {
      final Timer.Sample sample = MetricsHelper.getTimerSample();
      copyHeaders(clientRequest, ctx, mi);
      clientRequest.end();
      log(clientRequest);
      clientRequest.response()
          .onFailure(res -> proxyClientFailure(pc, mi, requestOptions, res))
          .onSuccess(res -> {
            MetricsHelper.recordHttpClientResponse(sample, pc.getTenant(), res.statusCode(),
                ctx.request().method().name(), mi);
            Iterator<ModuleInstance> newIt = getNewIterator(it, mi, res);
            if (newIt.hasNext()) {
              relayToRequest(res, pc, mi);
              storeResponseInfo(pc, mi, res);
              makeTraceHeader(mi, res.statusCode(), pc);
              res.endHandler(x
                  -> proxyR(newIt, pc, stream, bcontent, clientRequestList));
            } else {
              relayToResponse(ctx.response(), res, pc);
              makeTraceHeader(mi, res.statusCode(), pc);
              if (statusOk(res)) {
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

    logger.trace("ProxyRedirect {}", mi.getModuleDescriptor().getId());
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
    logger.debug("proxyInternalBuffer {}", req);
    RoutingContext ctx = pc.getCtx();

    clientsEnd(bcontent, clientRequestList);
    try {
      internalModule.internalService(req, pc)
          .onFailure(cause -> pc.responseError(OkapiError.getType(cause), cause))
          .onSuccess(resp -> {
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
    } catch (Exception e) {
      pc.responseError(ErrorType.INTERNAL, e);
    }
  }

  private void proxyR(Iterator<ModuleInstance> it,
                      ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
                      List<HttpClientRequest> clientRequestList) {

    RoutingContext ctx = pc.getCtx();
    if (!it.hasNext()) {
      stream.resume();
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

      String userId = mi.getUserId();
      if (userId != null) {
        ctx.request().headers().remove(XOkapiHeaders.USER_ID);
        ctx.request().headers().add(XOkapiHeaders.USER_ID, userId);
        logger.debug("Using X-Okapi-User-Id: {}", userId);
      }

      String perms = mi.getPermissions();
      if (perms != null) {
        ctx.request()
            .headers()
            .remove(XOkapiHeaders.PERMISSIONS);
        ctx.request()
            .headers()
            .add(XOkapiHeaders.PERMISSIONS, perms);
        logger.debug("Using X-Okapi-Permissions: {}", perms);
      }

      // Pass headers for filters
      passFilterHeaders(ctx, pc, mi);

      // Do proxy work
      ProxyType proxyType = mi.getRoutingEntry().getProxyType();
      if (proxyType != ProxyType.REDIRECT) {
        logger.debug("Invoking module {} type {} level {} path {} url {}",
            mi.getModuleDescriptor().getId(), proxyType, mi.getRoutingEntry().getPhaseLevel(),
                mi.getPath(), mi.getUrl());
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
      logger.debug("Adding {}: {}", XOkapiHeaders.FILTER, filt);
      // The auth filter needs all kinds of special headers
      headers.add(XOkapiHeaders.FILTER, filt);

      boolean badAuth = pc.getAuthRes() != 0 && !statusOk(pc.getAuthRes());
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
   * @param tenantId tenant identifier to make the request for
   * @param inst carries the moduleDescriptor, RoutingEntry, and getPath to be called
   * @param request body to send in the request
   * @param pc ProxyContext for logging, and returning resp headers
   * @return future with the OkapiClient that contains the body, headers
   */
  public Future<OkapiClient> callSystemInterface(String tenantId, ModuleInstance inst,
                                                 String request, ProxyContext pc) {

    MultiMap headersIn = pc.getCtx().request().headers();
    return callSystemInterface(headersIn, tenantId, inst, request);
  }

  Future<OkapiClient> callSystemInterface(MultiMap headersIn, String tenantId,
                                          ModuleInstance inst, String request) {

    if (!headersIn.contains(XOkapiHeaders.URL)) {
      headersIn.set(XOkapiHeaders.URL, okapiUrl);
    }
    // Check if the actual tenant has auth enabled. If yes, get a token for it.
    // If we have auth for current (super)tenant is irrelevant here!
    logger.debug("callSystemInterface: Checking if {} has auth", tenantId);

    if (!enableSystemAuth) {
      return doCallSystemInterface(headersIn, tenantId, null, inst, null, request);
    }
    return tenantManager.getEnabledModules(tenantId).compose(enabledModules -> {
      for (ModuleDescriptor md : enabledModules) {
        RoutingEntry[] filters = md.getFilters();
        if (filters != null) {
          for (RoutingEntry filt : filters) {
            if (XOkapiHeaders.FILTER_AUTH.equals(filt.getPhase())) {
              logger.debug("callSystemInterface: Found auth filter in {}", md.getId());
              return authForSystemInterface(md, filt, tenantId, inst, request, headersIn);
            }
          }
        }
      }
      logger.debug("callSystemInterface: No auth for {} calling with "
          + "tenant header only", tenantId);
      return doCallSystemInterface(headersIn, tenantId, null, inst, null, request);
    });
  }

  /**
   * Helper to get a new authtoken before invoking doCallSystemInterface.
   *
   */
  private Future<OkapiClient> authForSystemInterface(
      ModuleDescriptor authMod, RoutingEntry filt,
      String tenantId, ModuleInstance inst, String request, MultiMap headers) {

    logger.debug("authForSystemInterface");
    RoutingEntry re = inst.getRoutingEntry();
    String modPerms = "";
    String modId = inst.getModuleDescriptor().getId();
    if (re != null) {
      String[] modulePermissions = re.getModulePermissions();
      Map<String, String[]> mpMap = new HashMap<>();
      if (modulePermissions != null) {
        // replace module permissions with auto generated permission set id
        if (Boolean.TRUE.equals(tenantManager.getExpandModulePermissions(tenantId))) {
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
    return doCallSystemInterface(headers, tenantId, null, authInst, modPerms, "")
        .compose(cli -> {
          String deftok = cli.getRespHeaders().get(XOkapiHeaders.TOKEN);
          logger.debug("authForSystemInterface: {}",
              () -> Json.encode(cli.getRespHeaders().entries()));
          String modTok = cli.getRespHeaders().get(XOkapiHeaders.MODULE_TOKENS);
          String token = null;
          if (modTok != null) {
            JsonObject jo = new JsonObject(modTok);
            token = jo.getString(modId, deftok);
          }
          logger.debug("authForSystemInterface: Got token {}", token);
          return doCallSystemInterface(headers, tenantId, token, inst, null, request);
        });
  }

  private Future<OkapiClient> doCallSystemInterface2(
      MultiMap headersIn, String tenantId, String authToken,
      ModuleInstance inst, String modPerms, String request) {

    Map<String, String> headers = sysReqHeaders(headersIn, tenantId, authToken, inst, modPerms);
    headers.put(XOkapiHeaders.URL_TO, inst.getUrl());
    logger.debug("syscall begin {} {}{}", inst.getMethod(), inst.getUrl(), inst.getPath());
    OkapiClient cli = new OkapiClient(httpClient.getHttpClient(), inst.getUrl(), vertx, headers);
    String reqId = inst.getPath().replaceFirst("^[/_]*([^/]+).*", "$1");
    cli.newReqId(reqId); // "tenant" or "tenantpermissions"
    cli.enableInfoLog();
    if (inst.isWithRetry()) {
      cli.setClosedRetry(40000);
    }
    final Timer.Sample sample = MetricsHelper.getTimerSample();
    Promise<OkapiClient> promise = Promise.promise();
    cli.request(inst.getMethod(), inst.getPath(), request, cres -> {
      logger.debug("syscall return {} {}{}", inst.getMethod(), inst.getUrl(), inst.getPath());
      if (cres.failed()) {
        String msg = messages.getMessage("11101", inst.getMethod(),
            inst.getModuleDescriptor().getId(), inst.getPath(), cres.cause().getMessage());
        logger.warn(msg, cres.cause());
        MetricsHelper.recordHttpClientError(tenantId, inst.getMethod().name(), inst.getPath());
        promise.fail(new OkapiError(ErrorType.USER, msg));
        return;
      }
      MetricsHelper.recordHttpClientResponse(sample, tenantId, cli.getStatusCode(),
          inst.getMethod().name(), inst);
      // Pass response headers - needed for unit test, if nothing else
      promise.complete(cli);
    });
    return promise.future();
  }

  /**
   * Actually make a request to a system interface, like _tenant. Assumes we are
   * operating as the correct tenant.
   */
  Future<OkapiClient> doCallSystemInterface(
      MultiMap headersIn, String tenantId, String authToken,
      ModuleInstance inst, String modPerms, String request) {

    Future<Void> future = Future.succeededFuture();
    if (inst.getUrl() == null) {
      future = discoveryManager.get(inst.getModuleDescriptor().getId())
          .compose(gres -> {
            DeploymentDescriptor instance = pickInstance(gres);
            if (instance == null) {
              return Future.failedFuture(new OkapiError(ErrorType.USER, messages.getMessage("11100",
                  inst.getModuleDescriptor().getId(), inst.getPath())));
            }
            inst.setUrl(instance.getUrl());
            return Future.succeededFuture();
          });
    }
    return future.compose(x -> doCallSystemInterface2(
        headersIn, tenantId, authToken, inst, modPerms, request));
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
    final String origPath = ctx.request().path();
    String qry = ctx.request().query();
    String tid = origPath
        .replaceFirst("^/_/invoke/tenant/([^/ ]+)/.*$", "$1");
    String newPath = origPath
        .replaceFirst("^/_/invoke/tenant/[^/ ]+(/.*$)", "$1");

    // disable MDC lookup to mitigate recursive lookup denial of service attack
    // CVE-2021-45105: https://logging.apache.org/log4j/2.x/index.html
    if (tid.contains("${")) {
      HttpResponse.responseError(ctx, 400, "tenantId must not contain ${");
      return;
    }

    // delegate CORS for preflight request
    if (HttpMethod.OPTIONS.equals(ctx.request().method())
        && ctx.data().containsKey(CorsHelper.DELEGATE_CORS)) {
      ctx.data().remove(CorsHelper.DELEGATE_CORS);
      ModuleInstance mi = (ModuleInstance) ctx.data().get(CorsHelper.DELEGATE_CORS_MODULE_INSTANCE);
      ctx.data().remove(CorsHelper.DELEGATE_CORS_MODULE_INSTANCE);
      resolveUrls(List.of(mi)).compose(unused -> {
        RequestOptions requestOptions = new RequestOptions().setMethod(ctx.request().method())
            .setAbsoluteURI(mi.getUrl() + newPath);
        return httpClient.request(requestOptions)
            .compose(clientRequest -> {
              copyHeaders(clientRequest, ctx, null);
              clientRequest.putHeader(XOkapiHeaders.TENANT, tid);
              clientRequest.end();
              return clientRequest.response();
            })
            .onSuccess(res -> {
              ctx.response().setStatusCode(res.statusCode());
              ctx.response().headers().addAll(res.headers());
              sanitizeAuthHeaders(ctx.response().headers());
              ctx.response().end();
            })
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
            .mapEmpty();
      });
    } else {
      if (qry != null && !qry.isEmpty()) {
        // vert.x 3.5 clears the parameters on reroute, so we pass them in ctx
        ctx.data().put(REDIRECTQUERY, qry);
      }
      ctx.request().headers().add(XOkapiHeaders.TENANT, tid);
      logger.debug("redirectProxy: '{}' '{}'", tid, newPath);
      ctx.reroute(newPath);
    }
  }

  public Future<Void> autoDeploy(ModuleDescriptor md) {
    return discoveryManager.autoDeploy(md);
  }

  public Future<Void> autoUndeploy(ModuleDescriptor md) {
    return discoveryManager.autoUndeploy(md);
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
      ModuleInstance mi, HttpClientResponse res) {

    if (statusOk(res)) {
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

  static boolean statusOk(HttpClientResponse res) {
    return statusOk(res.statusCode());
  }

  static boolean statusOk(int status) {
    return status >= 200 && status <= 299;
  }
} // class
