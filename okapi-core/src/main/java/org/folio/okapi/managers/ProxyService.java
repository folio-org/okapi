package org.folio.okapi.managers;

import io.vertx.core.AsyncResult;
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
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.RoutingEntry.ProxyType;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.OkapiToken;
import org.folio.okapi.util.ProxyContext;
import org.folio.okapi.common.Messages;
import org.folio.okapi.util.DropwizardHelper;

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
            ModuleInstance mi = new ModuleInstance(trymod, tryre, newUri, ctx.request().method(), false);
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
            ModuleInstance mi = new ModuleInstance(trymod, tryre, newUri, ctx.request().method(), true);
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

  /**
   * Builds the pipeline of modules to be invoked for a request. Sets the
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
            ModuleInstance mi = new ModuleInstance(md, re, req.uri(), req.method(), true);
            mi.setAuthToken(req.headers().get(XOkapiHeaders.TOKEN));
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
          ModuleInstance mi = new ModuleInstance(md, re, req.uri(), req.method(), false);
          mi.setAuthToken(req.headers().get(XOkapiHeaders.TOKEN));
          mods.add(mi);
          if (!resolveRedirects(pc, mods, re, enabledModules, "", req.uri())) {
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
      tenantId = XOkapiHeaders.SUPERTENANT_ID;
      ctx.request().headers().add(XOkapiHeaders.TENANT, tenantId);
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

  private String makeUrl(ModuleInstance mi, RoutingContext ctx) {
    String url = mi.getUrl();
    if (mi.getRewritePath() != null) {
      url += mi.getRewritePath() + mi.getPath();
    } else {
      url += mi.getPath();
    }
    String rdq = (String) ctx.data().get(REDIRECTQUERY);
    if (rdq != null) { // Parameters smuggled in from redirectProxy
      url += "?" + rdq;
      logger.debug("Recovering hidden parameters from ctx {}", url);
    }
    return url;
  }

  public void proxy(RoutingContext ctx) {
    ctx.request().pause();
    ReadStream<Buffer> stream = ctx.request();
    // Pause the request data stream before doing any slow ops, otherwise
    // it will get read into a buffer somewhere.

    ProxyContext pc = new ProxyContext(ctx, waitMs);

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

        ctx.request().headers().set(XOkapiHeaders.URL, okapiUrl);
        ctx.request().headers().remove(XOkapiHeaders.MODULE_ID);
        ctx.request().headers().set(XOkapiHeaders.REQUEST_IP, ctx.request().remoteAddress().host());
        ctx.request().headers().set(XOkapiHeaders.REQUEST_TIMESTAMP, "" + System.currentTimeMillis());
        ctx.request().headers().set(XOkapiHeaders.REQUEST_METHOD, ctx.request().rawMethod());

        resolveUrls(l.iterator(), res -> {
          if (res.failed()) {
            stream.resume();
            pc.responseError(res.getType(), res.cause());
          } else {
            List<HttpClientRequest> cReqs = new LinkedList<>();
            proxyR(l.iterator(), pc, stream, null, cReqs);
          }
        });
      });

    });
  }

  private void proxyResponseImmediate(ProxyContext pc, ReadStream<Buffer> res,
    Buffer bcontent, List<HttpClientRequest> cReqs) {

    RoutingContext ctx = pc.getCtx();
    if (pc.getAuthRes() != 0 && (pc.getAuthRes() < 200 || pc.getAuthRes() >= 300)) {
      if (bcontent == null) {
        res.resume();
      }
      bcontent = pc.getAuthResBody();
    }
    if (bcontent != null) {
      pc.closeTimer();
      for (HttpClientRequest r : cReqs) {
        r.end(bcontent);
      }
      ctx.response().end(bcontent);
    } else {
      res.handler(data -> {
        for (HttpClientRequest r : cReqs) {
          r.write(data);
        }
        ctx.response().write(data);
      });
      res.endHandler(v -> {
        pc.closeTimer();
        for (HttpClientRequest r : cReqs) {
          r.end();
        }
        ctx.response().end();
      });
      res.exceptionHandler(e
        -> pc.warn("proxyRequestImmediate res exception ", e));
      res.resume();
    }
  }

  private boolean proxyHttpFail(ProxyContext pc, ModuleInstance mi,
    AsyncResult<HttpClientResponse> res) {

    if (res.succeeded()) {
      return false;
    }
    String e = res.cause().getMessage();
    pc.warn("proxyRequestHttpClient failure: " + mi.getUrl() + ": " + e);
    pc.responseError(500, messages.getMessage("10107",
      mi.getModuleDescriptor().getId(), mi.getUrl(), e));
    return true;
  }

  private void proxyRequestHttpClient(Iterator<ModuleInstance> it,
    ProxyContext pc, Buffer bcontent, List<HttpClientRequest> cReqs,
    ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    String url = makeUrl(mi, ctx);
    HttpMethod meth = ctx.request().method();
    HttpClientRequest cReq = httpClient.requestAbs(meth, url, res1 -> {
      if (proxyHttpFail(pc, mi, res1)) {
        return;
      }
      HttpClientResponse res = res1.result();
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
        proxyR(newIt, pc, null, bcontent, new LinkedList<>());
      } else {
        relayToResponse(ctx.response(), res, pc);
        makeTraceHeader(mi, res.statusCode(), pc);
        res.endHandler(x -> proxyResponseImmediate(pc, null, bcontent, cReqs));
        res.exceptionHandler(e
          -> pc.warn("proxyRequestHttpClient: res exception (b)", e));
      }
    });
    copyHeaders(cReq, ctx, mi);
    pc.trace("ProxyRequestHttpClient request buf '"
      + bcontent + "'");
    for (HttpClientRequest r : cReqs) {
      r.end(bcontent);
    }
    cReq.end(bcontent);
    log(pc, cReq);
  }

  private void proxyRequestLog(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    List<HttpClientRequest> cReqs, ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    HttpClientRequest cReq = httpClient.requestAbs(ctx.request().method(),
      makeUrl(mi, ctx), res -> logger.debug("proxyRequestLog 2"));
    cReqs.add(cReq);
    cReq.setChunked(true);
    if (!it.hasNext()) {
      relayToResponse(ctx.response(), null, pc);
      copyHeaders(cReq, ctx, mi);
      proxyResponseImmediate(pc, stream, bcontent, cReqs);
    } else {
      copyHeaders(cReq, ctx, mi);
      proxyR(it, pc, stream, bcontent, cReqs);
    }
    log(pc, cReq);
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
    List<HttpClientRequest> cReqs, ModuleInstance mi) {

    proxyStreamToBuffer(stream, bcontent, res
      -> proxyRequestHttpClient(it, pc, res, cReqs, mi)
    );
  }

  private void proxyRequestResponse10(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    List<HttpClientRequest> cReqs, ModuleInstance mi) {

    proxyStreamToBuffer(stream, bcontent, res
      -> proxyRequestResponse(it, pc, null, res, cReqs, mi)
    );
  }

  private void copyHeaders(HttpClientRequest cReq, RoutingContext ctx, ModuleInstance mi) {
    int sz = 0;
    int limit = 5; // all headers dumped
    for (String name : ctx.request().headers().names()) {
      List<String> values = ctx.request().headers().getAll(name);
      if (values.size() > 1) {
        logger.warn("dup HTTP header {}: {}", name, values);
      }
      for (String value : values) {
        sz += name.length() + 4 + value.length(); // 4 for colon blank cr lf
      }
    }
    if (sz > limit) {
      logger.info("Request headers size={}", sz);
      dumpHeaders(ctx.request().headers());
    }
    cReq.headers().setAll(ctx.request().headers());
    cReq.headers().remove("Content-Length");
    final String phase = mi.getRoutingEntry().getPhase();
    if (!XOkapiHeaders.FILTER_AUTH.equals(phase)) {
      cReq.headers().remove(XOkapiHeaders.ADDITIONAL_TOKEN);
    }
  }

  private static String dumpHeaders(MultiMap headers) {
    StringBuilder h = new StringBuilder();
    h.append("Headers:\n");
    for (String name : headers.names()) {
      List<String> values = headers.getAll(name);
      for (String value : values) {
        h.append(" ");
        h.append(name);
        h.append(": ");
        h.append(value);
        h.append("\n");
        logger.info("{}: {}", name, value);
      }
    }
    return h.toString();
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

  private void proxyRequestResponse(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    List<HttpClientRequest> cReqs, ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    HttpClientRequest cReq = httpClient.requestAbs(ctx.request().method(),
      makeUrl(mi, ctx), res1 -> {
      if (proxyHttpFail(pc, mi, res1)) {
        return;
      }
      HttpClientResponse res = res1.result();
      fixupXOkapiToken(mi.getModuleDescriptor(), ctx.request().headers(), res.headers());
      Iterator<ModuleInstance> newIt;
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        newIt = getNewIterator(it, mi);
      } else {
        newIt = it;
      }
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
    copyHeaders(cReq, ctx, mi);
    if (bcontent != null) {
      pc.trace("proxyRequestResponse request buf '" + bcontent + "'");
      for (HttpClientRequest r : cReqs) {
        r.end(bcontent);
      }
      cReq.end(bcontent);
    } else {
      cReq.setChunked(true);
      for (HttpClientRequest r : cReqs) {
        r.setChunked(true);
      }
      stream.handler(data -> {
        pc.trace("proxyRequestResponse request chunk '"
          + data.toString() + "'");
        cReq.write(data);
        for (HttpClientRequest r : cReqs) {
          r.write(data);
        }
      });
      stream.endHandler(v -> {
        pc.trace("proxyRequestResponse request complete");
        for (HttpClientRequest r : cReqs) {
          r.end();
        }
        cReq.end();
      });
      stream.exceptionHandler(e
        -> pc.warn("proxyRequestResponse: content exception ", e));
      stream.resume();
    }
    log(pc, cReq);
  }

  private void proxyHeaders(Iterator<ModuleInstance> it, ProxyContext pc,
    ReadStream<Buffer> stream, Buffer bcontent,
    List<HttpClientRequest> cReqs, ModuleInstance mi) {

    RoutingContext ctx = pc.getCtx();
    HttpClientRequest cReq = httpClient.requestAbs(ctx.request().method(),
      makeUrl(mi, ctx), res1 -> {
      if (proxyHttpFail(pc, mi, res1)) {
        return;
      }
      HttpClientResponse res = res1.result();
      Iterator<ModuleInstance> newIt;
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        newIt = getNewIterator(it, mi);
        if (!newIt.hasNext()) {
          relayToResponse(ctx.response(), res, pc);
          makeTraceHeader(mi, res.statusCode(), pc);
          proxyResponseImmediate(pc, res, null, cReqs);
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
          -> proxyR(newIt, pc, stream, bcontent, cReqs));
      } else {
        relayToResponse(ctx.response(), res, pc);
        makeTraceHeader(mi, res.statusCode(), pc);
        proxyResponseImmediate(pc, stream, bcontent, cReqs);
      }
    });
    copyHeaders(cReq, ctx, mi);
    cReq.end();
    log(pc, cReq);
  }

  private void proxyRedirect(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    List<HttpClientRequest> cReqs, ModuleInstance mi) {

    pc.trace("ProxyNull " + mi.getModuleDescriptor().getId());
    pc.closeTimer();
    // if no more entries in it, proxyR will return 404
    proxyR(it, pc, stream, bcontent, cReqs);
  }

  private void proxyInternal(Iterator<ModuleInstance> it,
    ProxyContext pc, ReadStream<Buffer> stream, Buffer bcontent,
    List<HttpClientRequest> cReqs, ModuleInstance mi) {

    proxyStreamToBuffer(stream, bcontent, res
      -> proxyInternalBuffer(it, pc, res, cReqs, mi)
    );
  }

  private void proxyInternalBuffer(Iterator<ModuleInstance> it,
    ProxyContext pc, Buffer bcontent, List<HttpClientRequest> cReqs,
    ModuleInstance mi) {

    String req = bcontent.toString();
    pc.debug("proxyInternalBuffer " + req);
    RoutingContext ctx = pc.getCtx();

    for (HttpClientRequest r : cReqs) {
      r.end(bcontent);
    }
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
    List<HttpClientRequest> cReqs) {

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
      final String pathPattern = mi.getRoutingEntry().getPathPattern();
      if (pathPattern != null) {
        ctx.request().headers().set(XOkapiHeaders.MATCH_PATH_PATTERN, pathPattern);
      }
      switch (pType) {
        case REQUEST_ONLY:
          proxyRequestOnly(it, pc, stream, bcontent, cReqs, mi);
          break;
        case REQUEST_RESPONSE:
          proxyRequestResponse(it, pc, stream, bcontent, cReqs, mi);
          break;
        case HEADERS:
          proxyHeaders(it, pc, stream, bcontent, cReqs, mi);
          break;
        case REDIRECT:
          proxyRedirect(it, pc, stream, bcontent, cReqs, mi);
          break;
        case INTERNAL:
          proxyInternal(it, pc, stream, bcontent, cReqs, mi);
          break;
        case REQUEST_RESPONSE_1_0:
          proxyRequestResponse10(it, pc, stream, bcontent, cReqs, mi);
          break;
        case REQUEST_LOG:
          proxyRequestLog(it, pc, stream, bcontent, cReqs, mi);
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

    final String phase = mi.getRoutingEntry().getPhase();
    if (phase != null) {
      String pth = mi.getRoutingEntry().getPathPattern();
      if (pth == null) {
        pth = mi.getRoutingEntry().getPath();
      }
      String filt = mi.getRoutingEntry().getPhase() + " " + pth;
      pc.debug("Adding " + XOkapiHeaders.FILTER + ": " + filt);
      // The auth filter needs all kinds of special headers
      ctx.request().headers().add(XOkapiHeaders.FILTER, filt);

      boolean badAuth = pc.getAuthRes() != 0 && (pc.getAuthRes() < 200 || pc.getAuthRes() >= 300);
      switch (phase) {
        case XOkapiHeaders.FILTER_AUTH:
          authHeaders(pc.getModList(), ctx.request().headers(), pc);
          break;
        case XOkapiHeaders.FILTER_PRE:
          // pass request headers and failed auth result
          if (badAuth) {
            ctx.request().headers().add(XOkapiHeaders.AUTH_RESULT, "" + pc.getAuthRes());
          }
          break;
        case XOkapiHeaders.FILTER_POST:
          // pass request headers and failed handler/auth result
          if (pc.getHandlerRes() > 0) {
            String hresult = String.valueOf(pc.getHandlerRes());
            ctx.request().headers().set(XOkapiHeaders.HANDLER_RESULT, hresult);
            ctx.request().headers().set(XOkapiHeaders.HANDLER_HEADERS, Json.encode(pc.getHandlerHeaders()));
          } else if (badAuth) {
            ctx.request().headers().set(XOkapiHeaders.AUTH_RESULT, "" + pc.getAuthRes());
            ctx.request().headers().set(XOkapiHeaders.AUTH_HEADERS, Json.encode(pc.getAuthHeaders()));
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

    String curTenantId = pc.getTenant(); // is often the supertenant
    MultiMap headersIn = pc.getCtx().request().headers();
    callSystemInterface(curTenantId, headersIn, tenant, inst, request, fut);
  }

  public void callSystemInterface(String curTenantId, MultiMap headersIn,
    Tenant tenant, ModuleInstance inst,
    String request, Handler<ExtendedAsyncResult<OkapiClient>> fut) {

    if (!headersIn.contains(XOkapiHeaders.URL)) {
      headersIn.set(XOkapiHeaders.URL, okapiUrl);
    }
    String tenantId = tenant.getId(); // the tenant we are about to enable
    String authToken = headersIn.get(XOkapiHeaders.TOKEN);
    if (tenantId.equals(curTenantId)) {
      doCallSystemInterface(headersIn, tenantId, authToken, inst, null, request, fut);
      return;
    }
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
      logger.debug("callSystemInterface: No auth for " + tenantId
        + " calling with tenant header only");
      doCallSystemInterface(headersIn, tenantId, null, inst, null, request, fut);
    });
  }

  /**
   * Helper to get a new authtoken before invoking doCallSystemInterface.
   *
   */
  private void authForSystemInterface(ModuleDescriptor authMod, RoutingEntry filt,
    String tenantId, ModuleInstance inst, String request, MultiMap headers,
    Handler<ExtendedAsyncResult<OkapiClient>> fut) {
    logger.debug("Calling doCallSystemInterface to get auth token");
    RoutingEntry re = inst.getRoutingEntry();
    String modPerms = "";

    if (re != null) {
      String[] modulePermissions = re.getModulePermissions();
      Map<String, String[]> mpMap = new HashMap<>();
      if (modulePermissions != null) {
        mpMap.put(inst.getModuleDescriptor().getId(), modulePermissions);
        logger.debug("authForSystemInterface: Found modPerms: {}", modPerms);
      } else {
        logger.debug("authForSystemInterface: Got RoutingEntry, but null modulePermissions");
      }
      modPerms = Json.encode(mpMap);
    } else {
      logger.debug("authForSystemInterface: re is null, can't find modPerms");
    }
    ModuleInstance authInst = new ModuleInstance(authMod, filt, inst.getPath(), HttpMethod.HEAD, inst.isHandler());
    doCallSystemInterface(headers, tenantId, null, authInst, modPerms, "", res -> {
      if (res.failed()) {
        logger.warn("Auth check for systemInterface failed!");
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
      logger.debug("authForSystemInterface: Got token {}", token);
      doCallSystemInterface(headers, tenantId, token, inst, null, request, fut);
    });
  }

  /**
   * Actually make a request to a system interface, like _tenant. Assumes we are
   * operating as the correct tenant.
   */
  private void doCallSystemInterface(MultiMap headersIn,
    String tenantId, String authToken, ModuleInstance inst, String modPerms,
    String request, Handler<ExtendedAsyncResult<OkapiClient>> fut) {

    discoveryManager.get(inst.getModuleDescriptor().getId(), gres -> {
      if (gres.failed()) {
        logger.warn("doCallSystemInterface on " + inst.getModuleDescriptor().getId() + " "
          + inst.getPath()
          + " failed. Could not find the module in discovery", gres.cause());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      DeploymentDescriptor instance = pickInstance(gres.result());
      if (instance == null) {
        fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("11100",
          inst.getModuleDescriptor().getId(), inst.getPath())));
        return;
      }
      String baseurl = instance.getUrl();
      Map<String, String> headers = sysReqHeaders(headersIn, tenantId, authToken, inst, modPerms);
      headers.put(XOkapiHeaders.URL_TO, baseurl);
      logger.info("syscall {}", baseurl + inst.getPath());
      OkapiClient cli = new OkapiClient(baseurl, vertx, headers);
      String reqId = inst.getPath().replaceFirst("^[/_]*([^/]+).*", "$1");
      cli.newReqId(reqId); // "tenant" or "tenantpermissions"
      cli.enableInfoLog();
      if (inst.isWithRetry()) {
        cli.setClosedRetry(40000);
      }
      cli.request(inst.getMethod(), inst.getPath(), request, cres -> {
        cli.close();
        if (cres.failed()) {
          String msg = messages.getMessage("11101", inst.getMethod(),
            inst.getModuleDescriptor().getId(), inst.getPath(), cres.cause().getMessage());
          logger.warn(msg);
          fut.handle(new Failure<>(ErrorType.INTERNAL, msg));
          return;
        }
        // Pass response headers - needed for unit test, if nothing else
        fut.handle(new Success<>(cli));
      });
    });
  }

  /**
   * Helper to make request headers for the system requests we make. Copies all
   * X- headers over. Adds a tenant, and a token, if we have one.
   */
  private Map<String, String> sysReqHeaders(MultiMap headersIn,
    String tenantId, String authToken, ModuleInstance inst, String modPerms) {

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
  private void storeResponseInfo(ProxyContext pc, ModuleInstance mi, HttpClientResponse res) {
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
