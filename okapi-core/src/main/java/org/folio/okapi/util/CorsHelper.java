package org.folio.okapi.util;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import java.util.List;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.managers.TenantManager;

/**
 * Customized CORS handling.
 *
 * <p>If a module API specifies <b>delegateCORS</b> to true in RAML, Okapi will
 * delegate CORS handling to the module if and only if the API is invoked
 * through {@code /_/invoke/tenant/<tenantId>/moduleAPI}
 */
public class CorsHelper {

  public static final String DELEGATE_CORS = "delegate-CORS";
  public static final String DELEGATE_CORS_MODULE_INSTANCE = "delegate-CORS-module-instance";
  private static final int ACCESS_CONTROL_MAX_AGE = 7200;

  private CorsHelper() {
  }

  /**
   * Add CORS handler to {@link Router}.
   *
   * @param router - {@link Router}
   */
  public static void addCorsHandler(Router router, final TenantManager tenantManager) {

    // set delegate CORS for special cases
    router.routeWithRegex("^/_/invoke/tenant/([^/ ]+)(/.*)").handler(ctx -> {
      String tenantId = ctx.pathParam("param0");
      String newPath = ctx.pathParam("param1");
      String meth = ctx.request().getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
      HttpMethod method = meth != null ? HttpMethod.valueOf(meth) : ctx.request().method();
      String moduleId = ctx.request().getHeader(XOkapiHeaders.MODULE_ID);
      List<ModuleInstance> list = tenantManager.getModuleCache(tenantId)
          .lookup(newPath, method, moduleId);
      for (ModuleInstance mi : list) {
        if (mi.isHandler() && mi.getRoutingEntry().isDelegateCors()) {
          ctx.data().put(DELEGATE_CORS, true);
          ctx.data().put(DELEGATE_CORS_MODULE_INSTANCE, mi);
          break;
        }
      }
      ctx.next();
    });

    // check delegate CORS
    router.route().handler(ctx -> {
      if (ctx.data().containsKey(DELEGATE_CORS)) {
        ctx.next();
      } else {
        CorsHandler.create("*")
            .allowedMethod(HttpMethod.PUT)
            .allowedMethod(HttpMethod.PATCH)
            .allowedMethod(HttpMethod.DELETE)
            .allowedMethod(HttpMethod.GET)
            .allowedMethod(HttpMethod.POST)
            .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
            .allowedHeader(XOkapiHeaders.TENANT)
            .allowedHeader(XOkapiHeaders.TOKEN)
            .allowedHeader(XOkapiHeaders.AUTHORIZATION)
            .allowedHeader(XOkapiHeaders.REQUEST_ID) // expose response headers
            .allowedHeader(XOkapiHeaders.MODULE_ID)
            .allowedHeader("Credentials")
            .exposedHeader(HttpHeaders.LOCATION.toString())
            .exposedHeader(XOkapiHeaders.TRACE)
            .exposedHeader(XOkapiHeaders.TOKEN)
            .exposedHeader(XOkapiHeaders.AUTHORIZATION)
            .exposedHeader(XOkapiHeaders.REQUEST_ID)
            .exposedHeader(XOkapiHeaders.MODULE_ID)
            .exposedHeader("Credentials")
            // Allow browsers to cache this CORS response. The cache is per URL.
            .maxAgeSeconds(ACCESS_CONTROL_MAX_AGE)
            .allowCredentials(true)
            .handle(ctx);
      }
    });
  }

}
