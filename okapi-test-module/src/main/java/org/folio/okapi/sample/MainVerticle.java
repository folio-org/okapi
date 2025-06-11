package org.folio.okapi.sample;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.ModuleVersionReporter;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.XOkapiHeaders;


/*
 * Test module, to be used in Okapi's own unit tests
 */

@java.lang.SuppressWarnings({"squid:S1192"})
public class MainVerticle extends AbstractVerticle {

  private final Logger logger = OkapiLogger.get();
  private String helloGreeting;
  private String tenantRequests = "";
  private JsonArray tenantParameters;

  // Report the request headers in response headers, body, and/or log
  private void headers(RoutingContext ctx, StringBuilder xmlMsg) {
    // Report all headers back (in headers and in the body) if requested
    String tenantReqs = ctx.request().getHeader("X-tenant-reqs");
    if (tenantReqs != null) {
      xmlMsg.append(" Tenant requests: ").append(tenantRequests);
    }
    if (ctx.request().getHeader("X-tenant-parameters") != null) {
      xmlMsg.append(" Tenant parameters: ");
      if (tenantParameters != null) {
        xmlMsg.append(tenantParameters.encodePrettily());
      }
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
            logger.info("{}:{}", hdr, hdrval);
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
    ctx.response().setStatusCode(200);
    ctx.response().putHeader("X-Handler-header", "OK");

    final String ctype = ctx.request().headers().get("Content-Type");
    final String accept = ctx.request().headers().get("Accept");
    // see if POSTed text should be converted to XML.. To simulate a real handler
    // with request/response of different content types
    final boolean xmlConversion = accept != null && accept.toLowerCase().contains("text/xml")
        && ctype != null && ctype.contains("text/plain");

    final StringBuilder msg = new StringBuilder();
    String hv = ctx.request().getHeader("X-my-header");
    if (hv != null) {
      msg.append(hv);
    }
    if (xmlConversion) {
      ctx.response().putHeader("Content-Type", "text/xml");
    } else if (ctype != null) {
      ctx.response().putHeader("Content-Type", ctype);
    }

    String stopper = ctx.request().getHeader("X-stop-here");
    if (stopper != null) {
      ctx.response().putHeader("X-Okapi-Stop", stopper);
    }
    headers(ctx, msg);
    String delayStr = ctx.request().getHeader("X-delay");
    if (delayStr != null) {
      ctx.request().pause();
      long delay = Long.parseLong(delayStr);
      ctx.vertx().setTimer(delay, res -> response(msg.toString(), xmlConversion, ctx));
    } else {
      response(msg.toString(), xmlConversion, ctx);
    }
  }

  private void response(String msg, boolean xmlConversion, RoutingContext ctx) {
    if (ctx.response().closed()) {
      logger.info("Already closed");
    }
    if (ctx.request().method().equals(HttpMethod.GET)) {
      ctx.request().endHandler(x -> ctx.response().end("It works" + msg));
    } else {
      ctx.response().setChunked(true);
      if (xmlConversion) {
        ctx.response().write("<test>");
      }
      ctx.response().write(helloGreeting + " " + msg);
      ctx.request().handler(x -> {
        ctx.response().write(x);
        if (xmlConversion) {
          ctx.response().write("</test>");
        }
      });
      ctx.request().endHandler(x -> ctx.response().end());
    }
    ctx.request().resume();
  }

  @SuppressWarnings("javasecurity:S5145")  // suppress
  // "Change this code to not log user-controlled data.
  //  Logging should not be vulnerable to injection attacks"
  // because Okapi validates the path against the ModuleDescriptor,
  // and this module is used for unit tests only.
  private void log(String method, String path, String tenant) {
    logger.info("{} {} to okapi-test-module for tenant {}", method, path, tenant);
  }

  private void myTenantHandle(RoutingContext ctx) {
    String tenant = ctx.request().getHeader(XOkapiHeaders.TENANT);
    String meth = ctx.request().method().name();
    log(meth, ctx.request().path(), tenant);
    tenantParameters = null;
    if (ctx.request().method().equals(HttpMethod.DELETE)) {
      ctx.response().setStatusCode(204);
      ctx.response().end();
    } else {
      ctx.response().setChunked(true);

      final String cont = ctx.request().getHeader("Content-Type");
      logger.debug("Tenant api content type: '{}'", cont);
      String tok = ctx.request().getHeader(XOkapiHeaders.TOKEN);
      if (tok == null) {
        tok = "";
      } else {
        tok = "-auth";
      }
      this.tenantRequests += meth + "-" + tenant + tok + " ";
      logger.debug("Tenant requests so far: {}", tenantRequests);

      Buffer b = Buffer.buffer();
      ctx.request().handler(b::appendBuffer);
      ctx.request().endHandler(x -> {
        try {
          JsonObject j = new JsonObject(b);
          logger.info("module_from={} module_to={}",
              j.getString("module_from"), j.getString("module_to"));
          tenantParameters = j.getJsonArray("parameters");
        } catch (DecodeException | ClassCastException ex) {
          HttpResponse.responseError(ctx, 400, ex.getLocalizedMessage());
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
    final String depthstr = d;
    int depth = Integer.parseInt(depthstr);
    if (depth < 0) {
      HttpResponse.responseError(ctx, 400, "Bad recursion, depth cannot be negative: " + depth);
    } else if (depth == 0) {
      HttpResponse.responseText(ctx, 200);
      ctx.response().end("Recursion done");
    } else {
      OkapiClient ok = new OkapiClient(ctx);
      depth--;
      ok.get("/recurse?depth=" + depth, res -> {
        if (res.succeeded()) {
          HttpResponse.responseText(ctx, 200);
          ctx.response().end(depthstr + " " + res.result());
        } else {
          String message = res.cause().getMessage();
          HttpResponse.responseError(ctx, 500, "Recurse " + depthstr + " failed with " + message);
        }
      });
    }
  }

  private void myPermissionHandle(RoutingContext ctx) {
    ctx.request().endHandler(x -> ctx.response().end());
  }

  @Override
  public void start(Promise<Void> promise) throws IOException {
    helloGreeting = System.getenv("helloGreeting");
    if (helloGreeting == null) {
      helloGreeting = "Hello";
    }
    final int port = Integer.parseInt(
        System.getProperty("http.port", System.getProperty("port", "8080")));
    String name = ManagementFactory.getRuntimeMXBean().getName();

    ModuleVersionReporter m = new ModuleVersionReporter("org.folio.okapi/okapi-test-module");
    m.logStart();
    logger.info("Starting okapi-test-module {} on port {}", name, port);

    Router router = Router.router(vertx);
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
    Future<Void> future = vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(port)
        .compose(result -> {
          final String pidFile = System.getProperty("pidFile");
          if (pidFile != null && !pidFile.isEmpty()) {
            final String pid = name.split("@")[0];
            try (FileWriter fw = new FileWriter(pidFile)) {
              fw.write(pid);
              logger.info("Writing {}", pid);
            } catch (IOException ex) {
              logger.error(ex);
              return Future.failedFuture(ex);
            }
          }
          return Future.succeededFuture();
        });
    future.onComplete(promise::handle);
  }
}
