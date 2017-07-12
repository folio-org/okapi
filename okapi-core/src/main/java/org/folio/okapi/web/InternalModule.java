package org.folio.okapi.web;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import static io.vertx.core.http.HttpMethod.*;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleDescriptorBrief;
import org.folio.okapi.bean.PullDescriptor;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import static org.folio.okapi.common.ErrorType.USER;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.env.EnvManager;
import org.folio.okapi.pull.PullManager;
import org.folio.okapi.service.ModuleManager;
import org.folio.okapi.service.TenantManager;
import org.folio.okapi.util.LogHelper;
import org.folio.okapi.util.ProxyContext;

/**
 * Okapi's build-in module. Managing /_/ endpoints.
 * 
 * /_/proxy/modules
 * /_/proxy/tenants
 * /_/env
 * /_/test loglevel etc
 *
 * TODO
 * /_/proxy/health
 * /_/proxy/pull
 * /_/deployment
 * /_/discovery
 * /_/version
 *
 * Note that the endpoint /_/invoke/ can not be handled here, as the proxy
 * must read the request body before invoking this built-in module, and
 * /_/invoke uses ctx.reroute(), which assumes the body has not been read.
 *
 *
 */
public class InternalModule {
  private final ModuleManager moduleManager;
  private final TenantManager tenantManager;
  private final EnvManager envManager;
  private final PullManager pullManager;
  private final LogHelper logHelper;
  
  public InternalModule(ModuleManager modules, 
          TenantManager tenantManager, EnvManager envManager,
          PullManager pullManager) {
    this.moduleManager = modules;
    this.tenantManager = tenantManager;
    this.envManager = envManager;
    this.pullManager = pullManager;
    logHelper = new LogHelper();
  }


  private void createTenant(ProxyContext pc, String body,
      Handler<ExtendedAsyncResult<String>> fut ) {
    try {
      final TenantDescriptor td = Json.decodeValue(body, TenantDescriptor.class);
      if (td.getId() == null || td.getId().isEmpty()) {
        td.setId(UUID.randomUUID().toString());
      }
      final String id = td.getId();
      if (!id.matches("^[a-z0-9._-]+$")) {
        fut.handle(new Failure<>(USER, "Invalid tenant id '" + id + "'"));
      } else {
        Tenant t = new Tenant(td);
        tenantManager.insert(t, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
            return;
          }
          RoutingContext ctx = pc.getCtx();
          final String uri = ctx.request().uri() + "/" + id;
          final String s = Json.encodePrettily(t.getDescriptor());
          ctx.response().setStatusCode(201);
          ctx.response().putHeader("Location", uri);
          fut.handle(new Success<>(s));
        });
      }
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void updateTenant (ProxyContext pc, String id, String body,
      Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final TenantDescriptor td = Json.decodeValue(body, TenantDescriptor.class);
      if (!id.equals(td.getId())) {
        fut.handle(new Failure<>(USER, "Tenant.id=" + td.getId() + " id=" + id ));
        return;
      }
      Tenant t = new Tenant(td);
      tenantManager.updateDescriptor(td, res -> {
        if (res.succeeded()) {
          final String s = Json.encodePrettily(t.getDescriptor());
          fut.handle(new Success<>(s));
        } else {
          fut.handle(new Failure<>(NOT_FOUND, res.cause() ));
        }
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void listTenants(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    tenantManager.list(res -> {
      if (res.succeeded()) {
        List<TenantDescriptor> tdl = res.result();
        String s = Json.encodePrettily(tdl);
        fut.handle(new Success<>(s));
      } else {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      }
    });
  }

  private void getTenant(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    tenantManager.get(id, res -> {
      if (res.succeeded()) {
        Tenant te = res.result();
        TenantDescriptor td = te.getDescriptor();
        String s = Json.encodePrettily(td);
        fut.handle(new Success<>(s));
      } else {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      }
    });
  }

  private void deleteTenant(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (XOkapiHeaders.SUPERTENANT_ID.equals(id)) {
      pc.responseError(403, "Can not delete the superTenant " + id);
      return;
    }
    tenantManager.delete(id, res -> {
      if (res.succeeded()) {
        pc.getCtx().response().setStatusCode(204);
        fut.handle(new Success<>(""));
      } else {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      }
    });
  }

  private void enableModuleForTenant(ProxyContext pc, String id, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final TenantModuleDescriptor td = Json.decodeValue(body,
              TenantModuleDescriptor.class);
      final String module_to = td.getId();
      tenantManager.enableAndDisableModule(id, null, module_to, pc, eres -> {
        if (eres.failed()) {
          fut.handle(new Failure<>(eres.getType(), eres.cause()));
          return;
        }
        final String uri = pc.getCtx().request().uri() + "/" + module_to;
        pc.getCtx().response().putHeader("Location", uri);
        pc.getCtx().response().setStatusCode(201);
        fut.handle(new Success<>(Json.encodePrettily(td)));
      });

    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }


  private void disableModuleForTenant(ProxyContext pc, String id, String module,
    Handler<ExtendedAsyncResult<String>> fut) {
      pc.debug("disablemodule t=" + id + " m=" + module);
      tenantManager.enableAndDisableModule(id, module, null, pc, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        pc.getCtx().response().setStatusCode(204);
        fut.handle(new Success<>(""));
      }
    });
  }

  private void updateModuleForTenant(ProxyContext pc, String id, String mod, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final String module_from = mod;
      final TenantModuleDescriptor td = Json.decodeValue(body,
              TenantModuleDescriptor.class);
      final String module_to = td.getId();
      tenantManager.enableAndDisableModule(id, module_from, module_to, pc, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        final String uri = pc.getCtx().request().uri();
        final String regex = "^(.*)/" + module_from + "$";
        final String newuri = uri.replaceAll(regex, "$1/" + module_to);
        pc.getCtx().response().setStatusCode(201);
        pc.getCtx().response().putHeader("Location", newuri);
        fut.handle(new Success<>(Json.encodePrettily(td)));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void listModulesForTenant(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    tenantManager.listModules(id, res -> {
      if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
      }
      List<String> ml = res.result();
      Iterator<String> mli = ml.iterator();  // into a list of objects
      ArrayList<TenantModuleDescriptor> ta = new ArrayList<>();
      while (mli.hasNext()) {
        TenantModuleDescriptor tmd = new TenantModuleDescriptor();
        tmd.setId(mli.next());
        ta.add(tmd);
      }
      String s = Json.encodePrettily(ta);
      fut.handle(new Success<>(s));
    });
  }

  private void getModuleForTenant(ProxyContext pc, String id, String mod,
    Handler<ExtendedAsyncResult<String>> fut) {
    tenantManager.get(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      Tenant t = res.result();
      Set<String> ml = t.listModules();  // Convert the list of module names
      if (ml.contains(mod)) {
        TenantModuleDescriptor tmd = new TenantModuleDescriptor();
        tmd.setId(mod);
        String s = Json.encodePrettily(tmd);
        fut.handle(new Success<>(s));
      } else {
        fut.handle(new Failure<>(NOT_FOUND, mod));
      }
    });
  }

  private void listModulesFromInterface(ProxyContext pc, String id, String intId,
    Handler<ExtendedAsyncResult<String>> fut) {
    tenantManager.listModulesFromInterface(id, intId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      List<ModuleDescriptor> mdL = res.result();
      ArrayList<TenantModuleDescriptor> ta = new ArrayList<>();
      for (ModuleDescriptor md : mdL) {
        TenantModuleDescriptor tmd = new TenantModuleDescriptor();
        tmd.setId(md.getId());
        ta.add(tmd);
      }
      String s = Json.encodePrettily(ta);
      fut.handle(new Success<>(s));
    });
  }

  public void createModule(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final ModuleDescriptor md = Json.decodeValue(body, ModuleDescriptor.class);
      if (md.getId() == null || md.getId().isEmpty()) {
        md.setId(UUID.randomUUID().toString());
      }
      String validerr = md.validate(pc);
      if (!validerr.isEmpty()) {
        fut.handle(new Failure<>(USER, validerr));
        return;
      }
      moduleManager.create(md, cres -> {
        if (cres.failed()) {
          fut.handle(new Failure<>(cres.getType(), cres.cause()));
          return;
        }
        final String s = Json.encodePrettily(md);
        final String uri = pc.getCtx().request().uri() + "/" + md.getId();
        pc.getCtx().response().putHeader("Location", uri);
        pc.getCtx().response().setStatusCode(201);
        fut.handle(new Success<>(s));
      });
    } catch (DecodeException ex) {
      pc.debug("Failed to decode md: " + pc.getCtx().getBodyAsString());
      fut.handle(new Failure<>(USER, ex));
    }
  }

  public void getModule(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    final String q = "{ \"id\": \"" + id + "\"}";
    moduleManager.get(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  public void listModules(ProxyContext pc, 
    Handler<ExtendedAsyncResult<String>> fut) {
    moduleManager.getAllModules(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      List<ModuleDescriptorBrief> ml = new ArrayList<>(res.result().size());
      for (ModuleDescriptor md : res.result()) {
        ml.add(new ModuleDescriptorBrief(md));
      }
      String s = Json.encodePrettily(ml);
      fut.handle(new Success<>(s));
    });
  }

  public void updateModule(ProxyContext pc, String id, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final ModuleDescriptor md = Json.decodeValue(body, ModuleDescriptor.class);
      if (!id.equals(md.getId())) {
        fut.handle(new Failure<>(USER, "Module.id=" + md.getId() + " id=" + id));
        return;
      }
      String validerr = md.validate(pc);
      if (!validerr.isEmpty()) {
        fut.handle(new Failure<>(USER, validerr));
        return;
      }
      moduleManager.update(md, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        final String s = Json.encodePrettily(md);
        fut.handle(new Success<>(s));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
      pc.responseError(400, ex);
    }
  }

  public void deleteModule(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    moduleManager.delete(id, res -> {
        if (res.failed()) {
          pc.error("delete moduile failed: " + res.getType()
            + ":" + res.cause().getMessage());
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
      pc.getCtx().response().setStatusCode(204);
      fut.handle(new Success<>(""));
    });
  }


  public void listEnv(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    envManager.get(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        final String s = Json.encodePrettily(res.result());
        fut.handle(new Success<>(s));
      }
    });
  }

  public void getEnv(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (id == null) {
      fut.handle(new Failure<>(USER, "id missing"));
      return;
    }
    envManager.get(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        final String s = Json.encodePrettily(res.result());
        fut.handle(new Success<>(s));
      }
    });
  }

  public void createEnv(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final EnvEntry pmd = Json.decodeValue(body, EnvEntry.class);
      envManager.add(pmd, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          final String js = Json.encodePrettily(pmd);
          final String uri = pc.getCtx().request().uri() + "/" + pmd.getName();
          pc.getCtx().response().putHeader("Location", uri);
          pc.getCtx().response().setStatusCode(201);
          fut.handle(new Success<>(js));
        }
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  public void deleteEnv(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (id == null) {
      fut.handle(new Failure<>(USER, "id missing"));
      return;
    }
    envManager.remove(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        pc.getCtx().response().setStatusCode(204);
        fut.handle(new Success<>(""));
      }
    });
  }

  public void pullModules(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final PullDescriptor pmd = Json.decodeValue(body, PullDescriptor.class);
      pullManager.pull(pmd, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          fut.handle(new Success<>(Json.encodePrettily(res.result())));
        }
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }


  /**
   * Pretty simplistic health check.
   */
  private void getHealth(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    fut.handle(new Success<>("[ ]"));
  }


  private void getRootLogLevel(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    String lev = logHelper.getRootLogLevel();
    LogHelper.LogLevelInfo li = new LogHelper.LogLevelInfo(lev);
    String rj = Json.encode(li);
    fut.handle(new Success<>(rj));
  }

  public void setRootLogLevel(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    final LogHelper.LogLevelInfo inf = Json.decodeValue(body,
            LogHelper.LogLevelInfo.class);
    logHelper.setRootLogLevel(inf.getLevel());
    fut.handle(new Success<>(body));
    // Should at least return the actual log level, not whatever we post
    // We can post FOOBAR, and nothing changes...
  }


  /**
   * Dispatcher for all the built-in services.
   *
   * @param req The request body
   * @param pc Proxy context, gives a ctx, path, and method
   * @param fut Callback with the response body
   *
   * Note that there are restrictions what we can do with the ctx. We can set a
   * result code (defaults to 200 OK) in successful operations, but be aware
   * that only if this is the last module in the pipeline, will this code be
   * returned to the caller. Often that is the case. We can look at the request,
   * at least the (normalized) path and method, but the previous filters may
   * have done something to them already.
   *
   */
  public void internalService(String req, ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    RoutingContext ctx = pc.getCtx();
    String p = ctx.normalisedPath();
    String[] segments = p.split("/");
    int n = segments.length;
    HttpMethod m = ctx.request().method();
    pc.debug("internalService '" + ctx.request().method() + "'"
      + " '" + p + "'  nseg=" + n + " :" + Json.encode(segments));
    if (p.endsWith("/")) {
      n = 0; // force a notfound error for trailing slash
    }
    // default to json replies, error code overrides to text/plain
    pc.getCtx().response().putHeader("Content-Type", "application/json");
    if (n >= 4 && p.startsWith("/_/proxy/")){ // need at least /_/proxy/something
      if (segments[3].equals("modules")
              && moduleManager != null) {
        // /_/proxy/modules
        if (n == 4 && m.equals(GET)) {
            listModules(pc, fut);
            return;
        }
        if (n == 4 && m.equals(POST)) {
            createModule(pc, req, fut);
            return;
        }
        // /_/proxy/modules/:id
        if (n == 5 && m.equals(GET) ) {
            getModule(pc, segments[4], fut);
            return;
        }
        if (n == 5 && m.equals(PUT) ) {
            updateModule(pc, segments[4], req, fut);
            return;
        }
        if (n == 5 && m.equals(DELETE) ) {
            deleteModule(pc, segments[4], fut);
            return;
        }
      } // /_/proxy/modules

      if (segments[3].equals("tenants")
              && tenantManager != null) {
        // /_/proxy/tenants
        if (n == 4 && m.equals(GET) ) {
            listTenants(pc, fut);
            return;
        }
        if (n == 4 && m.equals(POST)) {
            createTenant(pc, req, fut);
            return;
        }
        // /_/proxy/tenants/:id
        if (n == 5 && m.equals(GET) ) {
            getTenant(pc, segments[4], fut);
            return;
        }
        if (n == 5 && m.equals(PUT) ) {
            updateTenant(pc, segments[4], req, fut);
            return;
        }
        if (n == 5 && m.equals(DELETE) ) {
            deleteTenant(pc, segments[4], fut);
            return;
        }
        // /_/proxy/tenants/:id/modules
        if (n==6 && m.equals(GET)&& segments[5].equals("modules")) {
          listModulesForTenant(pc, segments[4], fut);
          return;
        }
        if (n==6 && m.equals(POST)&& segments[5].equals("modules")) {
          enableModuleForTenant(pc, segments[4], req, fut);
          return;
        }
        // /_/proxy/tenants/:id/modules/:mod
        if (n == 7 && m.equals(GET) && segments[5].equals("modules")){
          getModuleForTenant(pc, segments[4], segments[6], fut);
          return;
        }
        if (n == 7 && m.equals(PUT) && segments[5].equals("modules")){
          updateModuleForTenant(pc,  segments[4], segments[6], req, fut);
          return;
        }
        if (n == 7 && m.equals(POST) && segments[5].equals("modules")){
          updateModuleForTenant(pc,  segments[4], segments[6], req, fut);
          return;
        }
        if (n == 7 && m.equals(DELETE) && segments[5].equals("modules")){
          disableModuleForTenant(pc, segments[4], segments[6], fut);
          return;
        }
        // /_/proxy/tenants/:id/interfaces/:int
        if (n == 7 && m.equals(GET) && segments[5].equals("interfaces")){
          listModulesFromInterface(pc, segments[4], segments[6], fut);
          return;
        }
      } // /_/proxy/tenants

      // /_/proxy/pull/modules
      if (n == 5 && segments[3].equals("pull") && segments[4].equals("modules")
              && m.equals(POST) && pullManager != null){
        pullModules(pc, req, fut);
        return;
      }
      // /_/proxy/health
      if (n == 4 && segments[3].equals("health") && m.equals(GET)){
        getHealth(pc, fut);
        return;
      }

    } // _/proxy

    if (n >= 2 && p.startsWith("/_/env") 
            && segments[2].equals("env")){ // not envXX or such

      // /_/env
      if (n == 3 && m.equals(GET)  ) {
        listEnv(pc, fut);
        return;
      }
      if (n == 3 && m.equals(POST)  ) {
        createEnv(pc, req, fut);
        return;
      }
      // /_/env/name
      if (n == 4 && m.equals(GET)  ) {
        getEnv(pc, segments[3], fut);
        return;
      }
      if (n == 4 && m.equals(DELETE)  ) {
        deleteEnv(pc, segments[3], fut);
        return;
      }

    } // env


    if (n >= 2 && p.startsWith("/_/test/")){
      if (n == 4 && m.equals(GET) && segments[3].equals("loglevel")) {
        getRootLogLevel(pc,fut);
        return;
      }
      if (n == 4 && m.equals(POST) && segments[3].equals("loglevel")) {
        setRootLogLevel(pc,req,fut);
        return;
      }
    }

    // If we get here, nothing matched.
    String slash = "";
    if (p.endsWith("/")) {
      slash = " (try without a trailing slash)";
    }
    fut.handle(new Failure<>(NOT_FOUND, "No internal module found for "
            + m + " " + p + slash));
  }

}
