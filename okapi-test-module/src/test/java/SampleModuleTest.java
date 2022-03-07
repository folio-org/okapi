import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.sample.MainVerticle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class SampleModuleTest {

  private Vertx vertx;
  private static final int PORT = 9230;
  private static final String URL = "http://localhost:" + Integer.toString(PORT);
  private final Logger logger = OkapiLogger.get();
  private final String pidFilename = "sample-module.pid";

  @Before
  public void setUp(TestContext context) {
    logger.debug("setUp");
    vertx = Vertx.vertx();

    System.setProperty("port", Integer.toString(PORT));
    System.setProperty("pidFile", pidFilename);

    DeploymentOptions opt = new DeploymentOptions();
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) throws IOException {
    try {
      Files.delete(Paths.get(pidFilename));
    } catch (IOException ex) {
      logger.warn(ex);
    }
    Async async = context.async();
    vertx.close(x -> async.complete());
  }

  private HashMap<String, String> headers = new HashMap<>();

  @Test
  public void testGet(TestContext context) {
    Async async = context.async();

    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put("Content-Type", "text/plain");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.get("/testb", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("It works", cli.getResponsebody());
      testPostText(context, cli, async);
    });
  }

  public void testPostText(TestContext context, OkapiClient cli, Async async) {
    cli.post("/testb/extra", "FOO", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("Hello FOO", cli.getResponsebody());
      testPostXML(context, cli, async);
    });
  }

  public void testPostXML(TestContext context, OkapiClient cli, Async async) {
    headers.put("Accept", "text/xml");
    cli.setHeaders(headers);
    headers.remove("Accept");
    cli.post("/testb/extra", "FOO", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("<test>Hello FOO</test>", cli.getResponsebody());
      testRecurse(context, cli, async);
    });
  }

  public void testRecurse(TestContext context, OkapiClient cli, Async async) {
    cli.setHeaders(headers);
    cli.get("/recurse", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("1 Recursion done", cli.getResponsebody());
      testRecurseDepth(context, cli, async);
    });
  }

  public void testRecurseDepth(TestContext context, OkapiClient cli, Async async) {
    cli.setHeaders(headers);
    cli.get("/recurse?depth=2", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("2 1 Recursion done", cli.getResponsebody());
      testRecurseDepthMinus(context, cli, async);
    });
  }

  public void testRecurseDepthMinus(TestContext context, OkapiClient cli, Async async) {
    cli.setHeaders(headers);
    cli.get("/recurse?depth=-2", res -> {
      context.assertTrue(res.failed());
      testTenantPost(context, cli, async);
    });
  }

  public void testTenantPost(TestContext context, OkapiClient cli, Async async) {
    cli.post("/_/tenant", "{\"module_from\": \"m-1.0.0\", \"module_to\":\"m-1.0.1\"}", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("POST /_/tenant to okapi-test-module for "
        + "tenant my-lib\n",
        cli.getResponsebody());
      testTenantPostWithParameters(context, cli, async);
    });
  }

  public void testTenantPostWithParameters(TestContext context, OkapiClient cli, Async async) {
    headers.put(XOkapiHeaders.TOKEN, "dummy-token");
    cli.post("/_/tenant", "{\"module_from\": \"m-1.0.0\", \"module_to\":\"m-1.0.1\", "
      + "\"parameters\" : [ {\"key\": \"a\",  \"value\" : \"b\"} ] }", res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("POST /_/tenant to okapi-test-module for "
          + "tenant my-lib\n",
          cli.getResponsebody());
        testTenantPostCheck(context, cli, async);
      });
  }

  public void testTenantPostCheck(TestContext context, OkapiClient cli, Async async) {
    headers.put("X-tenant-parameters", "yes");
    cli.setHeaders(headers);
    cli.get("/testb", res -> {
      context.assertTrue(res.succeeded());
      context.assertTrue(cli.getResponsebody().startsWith("It works Tenant parameters"),
          cli.getResponsebody());
      testTenantDelete(context, cli, async);
    });
  }

  public void testTenantDelete(TestContext context, OkapiClient cli, Async async) {
    cli.delete("/_/tenant", res -> {
      context.assertTrue(res.succeeded());
      testTenantDisable(context, cli, async);
    });
  }

  public void testTenantDisable(TestContext context, OkapiClient cli, Async async) {
    cli.post("/_/tenant/disable", "{\"module_from\": \"m-1.0.0\"}", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("POST /_/tenant/disable to okapi-test-module for "
        + "tenant my-lib\n",
        cli.getResponsebody());
      testTenantBadPost(context, cli, async);
    });
  }

  public void testTenantBadPost(TestContext context, OkapiClient cli, Async async) {
    cli.post("/_/tenant", "{", res -> {
      context.assertTrue(res.failed());
      testTenantBadParameters(context, cli, async);
    });
  }

  public void testTenantBadParameters(TestContext context, OkapiClient cli, Async async) {
    cli.post("/_/tenant", "{\"module_from\": \"m-1.0.0\", \"module_to\":\"m-1.0.1\", "
      + "\"parameters\" : {\"key\": \"a\",  \"value\" : \"b\"} }", res -> {
        context.assertTrue(res.failed());
        testPermissionsPost(context, cli, async);
      });
  }

  public void testPermissionsPost(TestContext context, OkapiClient cli, Async async) {
    cli.post("/_/tenantpermissions", "{}", res -> {
      context.assertTrue(res.succeeded());
      testDelete(context, cli, async);
    });
  }

  public void testDelete(TestContext context, OkapiClient cli, Async async) {
    cli.delete("/testb", res -> {
      context.assertTrue(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void test500(TestContext context) {
    Async async = context.async();
    HashMap<String, String> headers = new HashMap<>();
    headers.put("X-Handler-error", "true");
    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.get("/testb", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      async.complete();
    });
  }

  @Test
  public void testAllHeaders(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();

    headers.put("X-all-headers", "HBL");
    headers.put("X-delay", "2");
    headers.put("X-my-header", "my");
    headers.put("X-tenant-reqs", "yes");
    headers.put("X-tenant-parameters", "yes");
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put(XOkapiHeaders.MATCH_PATH_PATTERN, "/testb");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.enableInfoLog();
    cli.get("/testb?q=a", res -> {
      context.assertTrue(res.succeeded());
      String body = cli.getResponsebody();
      context.assertTrue(body.contains("It worksmy "));
      context.assertTrue(body.contains(" X-delay:2"));
      context.assertTrue(body.contains(" X-Url-Params:q=a"));
      context.assertTrue(body.contains(" X-my-header:my"));
      context.assertTrue(body.contains(" X-all-headers:HBL"));

      async.complete();
    });
  }

  @Test
  public void testNoHeaders(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();

    headers.put("X-all-headers", "X");
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.enableInfoLog();
    cli.get("/testb?q=a", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("It works", cli.getResponsebody());
      async.complete();
    });
  }

  @Test
  public void testStop(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();

    headers.put("X-stop-here", "X");
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.enableInfoLog();
    cli.get("/testb", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("X", cli.getRespHeaders().get("X-Okapi-Stop"));
      context.assertEquals("It works", cli.getResponsebody());
      async.complete();
    });
  }

  @Test
  public void testUpload(TestContext context) {
    int bufSz = 200000;
    long bufCnt = 10000;
    long total = bufSz * bufCnt;
    HttpClient client = vertx.createHttpClient();
    for (int loop = 0; loop < 2; loop++) {
      Async async = context.async();
      logger.info("Sending {} GB", total / 1e9);
      client.request(HttpMethod.POST, PORT, "localhost", "/testb", ar -> {
        context.assertTrue(ar.succeeded());
        HttpClientRequest request = ar.result();
        request.putHeader("Content-Type", "text/plain");
        request.putHeader("Accept", "text/plain");
        request.setChunked(true);
        Buffer buffer = Buffer.buffer();
        for (int j = 0; j < bufSz; j++) {
          buffer.appendString("X");
        }
        endRequest(request, buffer, 0, bufCnt);
        request.response(context.asyncAssertSuccess(res -> {
          context.assertEquals(200, res.statusCode());
          AtomicLong cnt = new AtomicLong();
          res.handler(h -> cnt.addAndGet(h.length()));
          res.exceptionHandler(ex -> {
            context.fail(ex);
            async.complete();
          });
          res.endHandler(end -> {
            context.assertEquals(total + 6, cnt.get());
            async.complete();
          });
        }));
      });
      async.await(50000);
    }
  }

  void endRequest(HttpClientRequest req, Buffer buffer, long i, long cnt) {
    if (i == cnt) {
      req.end();
    } else {
      req.write(buffer, res -> endRequest(req, buffer, i + 1, cnt));
    }
  }
}
