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
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import static org.folio.okapi.common.ErrorType.USER;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.service.ModuleManager;
import org.folio.okapi.service.TenantManager;
import org.folio.okapi.util.ProxyContext;

/**
 * Okapi's build-in module. Managing tenants, modules, etc.
 */
public class InternalModule {
  private final ModuleManager moduleManager;
  private final TenantManager tenantManager;

  public InternalModule(ModuleManager modules, TenantManager tenantManager) {
    this.moduleManager = modules;
    this.tenantManager = tenantManager;
  }


  public void createTenant(ProxyContext pc, String body,
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

  public void updateTenant (ProxyContext pc, String id, String body,
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

  public void deleteTenant(ProxyContext pc, String id,
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
  public void enableModuleForTenant(ProxyContext pc, String id, String body,
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
        //pc.responseJson(201, Json.encodePrettily(td), uri);
        pc.getCtx().response().putHeader("Location", uri);
        pc.getCtx().response().setStatusCode(201);
        fut.handle(new Success<>(Json.encodePrettily(td)));
      });

    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }


  public void disableModuleForTenant(ProxyContext pc, String id, String module,
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

  public void updateModuleForTenant(ProxyContext pc, String id, String mod, String body,
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

  public void listModulesForTenant(ProxyContext pc, String id,
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

  public void getModuleForTenant(ProxyContext pc, String id, String mod,
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

  public void listModulesFromInterface(ProxyContext pc, String id, String intId,
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
    if (n >= 4 ){ // need at least /__/proxy/something
      if (p.startsWith("/__/proxy/tenants")
              && tenantManager != null) {
        if (n == 4 && m.equals(GET) && segments[3].equals("tenants")) {
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
        if (n == 7 && m.equals(DELETE) && segments[5].equals("modules")){
          disableModuleForTenant(pc, segments[4], segments[6], fut);
          return;
        }
        // /_/proxy/tenants/:id/interfaces/:int
        if (n == 7 && m.equals(GET) && segments[5].equals("interfaces")){
          listModulesFromInterface(pc, segments[4], segments[6], fut);
          return;
        }
      } // tenants

    }
    String slash = "";
    if (p.endsWith("/")) {
      slash = " (try without a trailing slash)";
    }

    fut.handle(new Failure<>(NOT_FOUND, "No internal module found for "
            + m + " " + p + slash));
  }

}
