package org.folio.okapi.web;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.ErrorType;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import static org.folio.okapi.common.ErrorType.USER;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.ModuleManager;
import org.folio.okapi.service.TenantManager;
import org.folio.okapi.util.ProxyContext;

/**
 * Okapi's build-in module. Managing tenants, modules, etc.
 */
public class InternalModule {
  private final ModuleManager modules;
  private final TenantManager tenantManager;

  public InternalModule(ModuleManager modules, TenantManager tenantManager) {
    this.modules = modules;
    this.tenantManager = tenantManager;
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
    String path = ctx.normalisedPath();
    String[] segments = path.split("/");
    pc.debug("internalService '" + ctx.request().method() + "'"
      + " '" + path + "'  nseg=" + segments.length + " :" + Json.encode(segments));
    if (segments.length >= 4 // proper beginning   /__/proxy/
      && segments[0].equals("")
      && segments[1].equals("__")) {
      if (segments[2].equals("proxy")) {
        if (segments[3].equals("tenants")) {
          if (segments.length == 4) { // .../tenants
            if (ctx.request().method() == HttpMethod.GET) {
              listTenants(pc, fut);
              return;
            }
          } else if (segments.length == 5) { // .../tenants/:id
            if (ctx.request().method() == HttpMethod.GET) {
              getTenant(pc, segments[4], fut);
              return;
            }

          }
        } // tenants
      } // proxy

    }
    fut.handle(new Failure<>(NOT_FOUND, "No internal module found for " + path));
  }

}
