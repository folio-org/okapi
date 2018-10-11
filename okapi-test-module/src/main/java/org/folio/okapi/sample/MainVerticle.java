package org.folio.okapi.sample;

/*
 * Test module, to be used in Okapi's own unit tests
 */
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
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

  // Report the request headers in response headers, body, and/or log
  private void headers(RoutingContext ctx, StringBuilder xmlMsg) {
    // Report all headers back (in headers and in the body) if requested
    String tenantReqs = ctx.request().getHeader("X-tenant-reqs");
    if (tenantReqs != null) {
      xmlMsg.append(" Tenant requests: ").append(tenantRequests);
    }
    String allh = ctx.request().getHeader("X-all-headers");
    if (allh != null) {
      String qry = ctx.request().query();
      if (qry != null) {
        ctx.request().headers().add("X-Url-Params", qry);
      }
      if (allh.contains("L")) {
        logger.info("Headers, as seen by okapi-test-module:");
      }
      for (String hdr : ctx.request().headers().names()) {
        String hdrval = ctx.request().getHeader(hdr);
        if (hdrval != null) {
          if (allh.contains("H") && hdr.startsWith("X-")) {
            ctx.response().putHeader(hdr, hdrval);
          }
          if (allh.contains("B")) {
            xmlMsg.append(" ").append(hdr).append(":").append(hdrval).append("\n");
          }
          if (allh.contains("L")) {
            logger.info(hdr + ":" + hdrval);
          }
        }
      }
    }
  }

  private void myStreamHandle(RoutingContext ctx) {
    if (HttpMethod.DELETE.equals(ctx.request().method())) {
      ctx.request().endHandler(x -> HttpResponse.responseText(ctx, 204).end());
      return;
    }

    // hack to return 500
    if (ctx.request().headers().contains("X-Handler-error")) {
      ctx.response().setStatusCode(500).end("It does not work");
      return;
    }

    // both client and Post filter should see handler response code and headers
    ctx.response().setStatusCode(200);
    ctx.response().putHeader("X-Handler-header", "OK");

    final String ctype = ctx.request().headers().get("Content-Type");
    StringBuilder xmlMsg = new StringBuilder();
    if (ctype != null && ctype.toLowerCase().contains("xml")) {
      xmlMsg.append(" (XML) ");
    }
    String hv = ctx.request().getHeader("X-my-header");
    if (hv != null) {
      xmlMsg.append(hv);
    }
    ctx.response().putHeader("Content-Type", "text/plain");

    String stopper = ctx.request().getHeader("X-stop-here");
    if (stopper != null) {
      ctx.response().putHeader("X-Okapi-Stop", stopper);
    }
    headers(ctx, xmlMsg);
    final String xmlMsg2 = xmlMsg.toString(); // it needs to be final, in the callbacks
    String delayStr = ctx.request().getHeader("X-delay");
    if (delayStr != null) {
      ctx.request().pause();
      long delay = Long.parseLong(delayStr);
      ctx.vertx().setTimer(delay, res -> response(xmlMsg2, ctx));
    } else {
      response(xmlMsg2, ctx);
    }
  }

  private void response(String xmlMsg2, RoutingContext ctx) {
    ctx.request().resume();
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
    String tenant = ctx.request().getHeader(XOkapiHeaders.TENANT);
    String meth = ctx.request().method().name();
    logger.info(meth + " " + ctx.request().uri() + " to okapi-test-module"
      + " for tenant " + tenant);
    if (ctx.request().method().equals(HttpMethod.DELETE)) {
      ctx.response().setStatusCode(204);
      ctx.response().end();
    } else {
      ctx.response().setChunked(true);

      final String cont = ctx.request().getHeader("Content-Type");
      logger.debug("Tenant api content type: '" + cont + "'");
      String tok = ctx.request().getHeader(XOkapiHeaders.TOKEN);
      if (tok == null) {
        tok = "";
      } else {
        tok = "-auth";
      }
      this.tenantRequests += meth + "-" + tenant + tok + " ";
      logger.debug("Tenant requests so far: " + tenantRequests);

      Buffer b = Buffer.buffer();
      ctx.request().handler(b::appendBuffer);
      ctx.request().endHandler(x -> {
        try {
          JsonObject j = new JsonObject(b);
          logger.info("module_from=" + j.getString("module_from") + " module_to=" + j.getString("module_to"));
        } catch (DecodeException ex) {
          responseError(ctx, 400, ex.getLocalizedMessage());
          return;
        }
        ctx.response().setStatusCode(200);
        ctx.response().write(meth + " " + ctx.request().uri() + " to okapi-test-module"
          + " for tenant " + tenant + "\n");
        ctx.response().end();
      });
    }
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

 private void myPermissionHandle(RoutingContext ctx) {
    final Buffer incoming = Buffer.buffer();
    ctx.request().handler(incoming::appendBuffer);
    ctx.request().endHandler(x -> {
      String body = incoming.toString();
      body = body.replaceAll("\\s+", " "); // remove newlines etc
      ctx.response().putHeader("X-Tenant-Perms-Result", body);
      ctx.response().end();
    });
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

    router.routeWithRegex("/testb").handler(this::myStreamHandle);
    router.routeWithRegex("/testb/.*").handler(this::myStreamHandle);
    router.get("/testr").handler(this::myStreamHandle);
    router.post("/testr").handler(this::myStreamHandle);
    router.post("/_/tenantpermissions")
      .handler(this::myPermissionHandle);

    router.post("/_/tenant").handler(this::myTenantHandle);
    router.post("/_/tenant/disable").handler(this::myTenantHandle);
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
