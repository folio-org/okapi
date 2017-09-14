package org.folio.okapi.sample;

/*
 * Test module, to be used in Okapi's own unit tests
 */
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import org.folio.okapi.common.XOkapiHeaders;
import static org.folio.okapi.common.HttpResponse.*;
import org.folio.okapi.common.OkapiClient;

public class MainVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger("okapi-test-module");
  private String helloGreeting;
  private String tenantRequests = "";

  public void my_stream_handle(RoutingContext ctx) {
    ctx.response().setStatusCode(200);
    final String ctype = ctx.request().headers().get("Content-Type");
    String xmlMsg = "";
    if (ctype != null && ctype.toLowerCase().contains("xml")) {
      xmlMsg = " (XML) ";
    }
    String hv = ctx.request().getHeader("X-my-header");
    if (hv != null) {
      xmlMsg += hv;
    }
    String tenantReqs = ctx.request().getHeader("X-tenant-reqs");
    if (tenantReqs != null) {
      xmlMsg += " Tenant requests: " + tenantRequests;
    }
    ctx.response().putHeader("Content-Type", "text/plain");

    // Report all headers back (in headers and in the body) if requested
    String allh = ctx.request().getHeader("X-all-headers");
    if (allh != null) {
      String qry = ctx.request().query();
      if ( qry != null )
        ctx.request().headers().add("X-Url-Params", qry);
      for (String hdr : ctx.request().headers().names()) {
        tenantReqs = ctx.request().getHeader(hdr);
        if (tenantReqs != null) {
          if (allh.contains("H")) {
            ctx.response().putHeader(hdr, tenantReqs);
          }
          if (allh.contains("B")) {
            xmlMsg += " " + hdr + ":" + tenantReqs + "\n";
          }
        }
      }
    }
    String stopper = ctx.request().getHeader("X-stop-here");
    if (stopper != null ) {
      ctx.response().putHeader("X-Okapi-Stop", stopper);
    }

    final String xmlMsg2 = xmlMsg; // it needs to be final, in the callbacks

    if (ctx.request().method().equals(HttpMethod.GET)) {
      ctx.request().endHandler(x -> {
        ctx.response().end("It works" + xmlMsg2);
      });
    } else {
      ctx.response().setChunked(true);
      ctx.response().write(helloGreeting + " " + xmlMsg2);
      ctx.request().handler(x -> {
        ctx.response().write(x);
      });
      ctx.request().endHandler(x -> {
        ctx.response().end();
      });
    }
  }

  public void my_tenant_handle(RoutingContext ctx) {
    ctx.response().setStatusCode(200);
    ctx.response().setChunked(true);

    String tenant = ctx.request().getHeader(XOkapiHeaders.TENANT);
    String meth = ctx.request().method().name();
    ctx.response().write(meth + " request to okapi-test-module "
      + "tenant service for tenant " + tenant + "\n");
    logger.info(meth + " request to okapi-test-module "
      + "tenant service for tenant " + tenant);
    final String cont = ctx.request().getHeader("Content-Type");
    logger.debug("Tenant api content type: '" + cont + "'");
    final String module_from = ctx.request().getParam("module_from");
    if (module_from != null) {
      logger.info("module_from=" + module_from);
    }
    final String module_to = ctx.request().getParam("module_to");
    if (module_to != null) {
      logger.info("module_to=" + module_to);
    }
    this.tenantRequests += meth + "-" + tenant + " ";
    logger.debug("Tenant requests so far: " + tenantRequests);
    ctx.request().handler(x -> {
      ctx.response().write(x);
    });
    ctx.request().endHandler(x -> {
      ctx.response().end();
    });
  }

  public void recurse_handle(RoutingContext ctx) {
    String d = ctx.request().getParam("depth");
    if (d == null || d.isEmpty()) {
      d = "1";
    }
    String depthstr = d; // must be final
    int depth = Integer.parseInt(depthstr);
    if (depth < 0) {
      responseError(ctx, 400, "Bad recursion, can not be negative " + depthstr);
    } else if (depth == 0) {
      responseText(ctx, 200);
      ctx.response().end("Recursion done");
    } else {
      OkapiClient ok = new OkapiClient(ctx);
      depth--;
      ok.get("/recurse?depth=" + depth, res -> {
        if (res.succeeded()) {
          responseText(ctx, 200);
          ctx.response().end(depthstr + " " + res.result());
        } else {
          String message = res.cause().getMessage();
          responseError(ctx, 500, "Recurse " + depthstr + " failed with " + message);
        }
      });
    }
  }

  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);

    helloGreeting = System.getenv("helloGreeting");
    if (helloGreeting == null) {
      helloGreeting = "Hello";
    }
    final int port = Integer.parseInt(System.getProperty("port", "8080"));
    logger.info("Starting okapi-test-module "
      + ManagementFactory.getRuntimeMXBean().getName()
      + " on port " + port);

    router.get("/testb").handler(this::my_stream_handle);
    router.post("/testb").handler(this::my_stream_handle);
    router.get("/testr").handler(this::my_stream_handle);
    router.post("/testr").handler(this::my_stream_handle);

    // TODO - Remove the /tenant path when we have switched to /_/tenant everywhere
    router.get("/tenant").handler(this::my_tenant_handle);
    router.post("/tenant").handler(this::my_tenant_handle);
    router.delete("/tenant").handler(this::my_tenant_handle);
    router.get("/_/tenant").handler(this::my_tenant_handle);
    router.post("/_/tenant").handler(this::my_tenant_handle);
    router.delete("/_/tenant").handler(this::my_tenant_handle);

    router.get("/recurse").handler(this::recurse_handle);

    HttpServerOptions so = new HttpServerOptions()            .setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
            .requestHandler(router::accept)
            .listen(
                    port,
                    result -> {
                      if (result.succeeded()) {
                        fut.complete();
                      } else {
                        fut.fail(result.cause());
                        logger.error("okapi-test-module failed: " + result.cause());
                      }
                    }
            );
  }
}
