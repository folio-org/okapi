package org.folio.okapi.util;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import java.util.List;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.common.XOkapiHeaders;

/**
 * Customized CORS handling.
 *
 * <p>If a module API specifies <b>delegateCORS</b> to true in RAML, Okapi will
 * delegate CORS handling to the module if and only if the API is invoked
 * through {@code /_/invoke/tenant/<tenantId>/moduleAPI}
 */
public class CorsHelper {

  private static final String CHECK_DELEGATE_CORS = "check-delegate-CORS";
  private static final String DELEGATE_CORS = "delegate-CORS";

  private CorsHelper() {
  }

  /**
   * Add CORS handler to {@link Router}.
   *
   * @param router - {@link Router}
   */
  public static void addCorsHandler(Router router) {

    router.routeWithRegex("^/_/invoke/tenant/[^/ ]+/.*").handler(ctx -> {
      ctx.data().put(CHECK_DELEGATE_CORS, true);
      ctx.next();
    });

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
          .allowedHeader(XOkapiHeaders.REQUEST_ID) //expose response headers
          .allowedHeader(XOkapiHeaders.MODULE_ID)
          .exposedHeader(HttpHeaders.LOCATION.toString())
          .exposedHeader(XOkapiHeaders.TRACE)
          .exposedHeader(XOkapiHeaders.TOKEN)
          .exposedHeader(XOkapiHeaders.AUTHORIZATION)
          .exposedHeader(XOkapiHeaders.REQUEST_ID)
          .exposedHeader(XOkapiHeaders.MODULE_ID).handle(ctx);
      }
    });
  }

  /**
   * Check CORS delegate to decide if reroute if necessary.
   *
   * @param ctx             - {@link RoutingContext}
   * @param moduleInstances a list of {@link ModuleInstance}
   * @return true if reroute if needed, false otherwise
   */
  public static boolean checkCorsDelegate(
      RoutingContext ctx, List<ModuleInstance> moduleInstances) {
    if (ctx.data().containsKey(CHECK_DELEGATE_CORS)) {
      ctx.data().remove(CHECK_DELEGATE_CORS);
      if (moduleInstances.stream().anyMatch(mi ->
          mi.isHandler() && mi.getRoutingEntry().isDelegateCors())) {
        ctx.data().put(DELEGATE_CORS, true);
        return true;
      }
    }
    return false;
  }

}
