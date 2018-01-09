package org.folio.okapi.sample;

/*
 * Test module, to be used in Okapi's own unit tests
 */
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Map;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.XOkapiHeaders;

import static org.folio.okapi.common.HttpResponse.*;
import org.folio.okapi.common.ModuleVersionReporter;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.OkapiLogger;

@java.lang.SuppressWarnings({"squid:S1192"})
public class MainVerticle extends AbstractVerticle {

  private final Logger logger = OkapiLogger.get();
  private String helloGreeting;
  private String tenantRequests = "";

  private void myStreamHandle(RoutingContext ctx) {
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> HttpResponse.responseText(ctx, 204).end());
      return;
    }
    ctx.response().setStatusCode(200);
    final String ctype = ctx.request().headers().get("Content-Type");
    StringBuilder xmlMsg = new StringBuilder();
    if (ctype != null && ctype.toLowerCase().contains("xml")) {
      xmlMsg.append(" (XML) ");
    }
    String hv = ctx.request().getHeader("X-my-header");
    if (hv != null) {
      xmlMsg.append(hv);
    }
    String tenantReqs = ctx.request().getHeader("X-tenant-reqs");
    if (tenantReqs != null) {
      xmlMsg.append(" Tenant requests: ").append(tenantRequests);
    }
    ctx.response().putHeader("Content-Type", "text/plain");

    // Report all headers back (in headers and in the body) if requested
    String allh = ctx.request().getHeader("X-all-headers");
    if (allh != null) {
      String qry = ctx.request().query();
      if (qry != null) {
        ctx.request().headers().add("X-Url-Params", qry);
      }
      for (String hdr : ctx.request().headers().names()) {
        tenantReqs = ctx.request().getHeader(hdr);
        if (tenantReqs != null) {
          if (allh.contains("H") && hdr.startsWith("X-")) {
            ctx.response().putHeader(hdr, tenantReqs);
          }
          if (allh.contains("B")) {
            xmlMsg.append(" ").append(hdr).append(":").append(tenantReqs).append("\n");
          }
          if (allh.contains("L")) {
            logger.info(hdr + ":" + tenantReqs);
          }
        }
      }
    }
    String stopper = ctx.request().getHeader("X-stop-here");
    if (stopper != null) {
      ctx.response().putHeader("X-Okapi-Stop", stopper);
    }

    final String xmlMsg2 = xmlMsg.toString(); // it needs to be final, in the callbacks

    if (ctx.request().method().equals(HttpMethod.GET)) {
      ctx.request().endHandler(x -> ctx.response().end("It works" + xmlMsg2));
    } else {
      ctx.response().setChunked(true);
      ctx.response().write(helloGreeting + " " + xmlMsg2);
      ctx.request().handler(x -> ctx.response().write(x));
      ctx.request().endHandler(x -> ctx.response().end());
    }
  }

  private void myTenantHandle(RoutingContext ctx) {
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
    ctx.request().handler(x -> ctx.response().write(x));
    ctx.request().endHandler(x -> ctx.response().end());
  }

  private void recurseHandle(RoutingContext ctx) {
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
        ok.close();
        if (res.succeeded()) {
          MultiMap respH = ok.getRespHeaders();
          for (Map.Entry<String, String> e : respH.entries()) {
            if (e.getKey().startsWith("X-") || e.getKey().startsWith("x-")) {
              ctx.response().headers().add(e.getKey(), e.getValue());
            }
          }
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
    String bName = ManagementFactory.getRuntimeMXBean().getName();

    ModuleVersionReporter m = new ModuleVersionReporter("org.folio.okapi/okapi-test-module");
    m.logStart();
    logger.info("Starting okapi-test-module "
      + bName + " on port " + port);

    router.get("/testb").handler(this::myStreamHandle);
    router.post("/testb").handler(this::myStreamHandle);
    router.delete("/testb").handler(this::myStreamHandle);
    router.get("/testr").handler(this::myStreamHandle);
    router.post("/testr").handler(this::myStreamHandle);

    router.get("/_/tenant").handler(this::myTenantHandle);
    router.post("/_/tenant").handler(this::myTenantHandle);
    router.delete("/_/tenant").handler(this::myTenantHandle);

    router.get("/recurse").handler(this::recurseHandle);

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
      .requestHandler(router::accept)
      .listen(
        port,
        result -> {
          if (result.succeeded()) {
            final String pidFile = System.getProperty("pidFile");
            if (pidFile != null && !pidFile.isEmpty()) {
              final String pid = bName.split("@")[0];
              try (FileWriter fw = new FileWriter(pidFile)) {
                fw.write(pid);
                logger.info("Writing " + pid);
              } catch (IOException ex) {
                logger.error(ex);
              }
            }
            fut.complete();
          } else {
            fut.fail(result.cause());
            logger.error("okapi-test-module failed: " + result.cause());
          }
        });
  }
}
