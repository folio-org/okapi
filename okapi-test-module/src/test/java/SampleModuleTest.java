
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
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
  private static final String LS = System.lineSeparator();

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

  @Test
  public void test1(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.get("/testb", res -> {
      context.assertTrue(res.succeeded());
      test2(context, cli, async);
    });
  }

  public void test2(TestContext context, OkapiClient cli, Async async) {
    cli.post("/testb/extra", "FOO", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("Hello FOO", cli.getResponsebody());
      test3(context, cli, async);
    });
  }

  public void test3(TestContext context, OkapiClient cli, Async async) {
    cli.get("/recurse?depth=2", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("2 1 Recursion done", cli.getResponsebody());
      test4(context, cli, async);
    });
  }

  public void test4(TestContext context, OkapiClient cli, Async async) {
    cli.post("/_/tenant", "{\"module_from\": \"m-1.0.0\", \"module_to\":\"m-1.0.1\"}", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("POST /_/tenant to okapi-test-module for "
        + "tenant my-lib\n",
        cli.getResponsebody());
      test5(context, cli, async);
    });
  }

  public void test5(TestContext context, OkapiClient cli, Async async) {
    cli.delete("/_/tenant", res -> {
      context.assertTrue(res.succeeded());
      test7(context, cli, async);
    });
  }

  public void test7(TestContext context, OkapiClient cli, Async async) {
    cli.post("/_/tenant/disable", "{\"module_from\": \"m-1.0.0\"}", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("POST /_/tenant/disable to okapi-test-module for "
        + "tenant my-lib\n",
        cli.getResponsebody());
      test8(context, cli, async);
    });
  }

  public void test8(TestContext context, OkapiClient cli, Async async) {
    cli.post("/_/tenant", "{", res -> {
      context.assertTrue(res.failed());
      test9(context, cli, async);
    });
  }

  public void test9(TestContext context, OkapiClient cli, Async async) {
    cli.post("/_/tenantpermissions", "{}", res -> {
      context.assertTrue(res.succeeded());
      test10(context, cli, async);
    });
  }


  public void test10(TestContext context, OkapiClient cli, Async async) {
    cli.delete("/testb", res -> {
      cli.close();
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
      cli.close();
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
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put(XOkapiHeaders.MATCH_PATH_PATTERN, "/testb");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.enableInfoLog();
    cli.get("/testb?q=a", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals(
        "It worksmy X-delay:2" + LS
     + " X-Okapi-Url:http://localhost:9230" + LS
     + " X-all-headers:HBL" + LS
     + " X-Okapi-Match-Path-Pattern:/testb" + LS
     + " X-my-header:my" + LS
     + " X-Okapi-Tenant:my-lib" + LS
     + " Content-Length:0" + LS
     + " Host:localhost:9230" + LS
     + " X-Url-Params:q=a" + LS,
        cli.getResponsebody());
      async.complete();
    });
  }
}
