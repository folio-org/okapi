
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
    cli.get("/recurse?depth=2", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("2 1 Recursion done", cli.getResponsebody());
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
    cli.post("/_/tenant", "{\"module_from\": \"m-1.0.0\", \"module_to\":\"m-1.0.1\", "
      + "\"parameters\" : [ {\"key\": \"a\",  \"value\" : \"b\"} ] }", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("POST /_/tenant to okapi-test-module for "
        + "tenant my-lib\n",
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
        "It worksmy X-delay:2\n"
     + " X-Okapi-Url:http://localhost:9230\n"
     + " X-all-headers:HBL\n"
     + " X-Okapi-Match-Path-Pattern:/testb\n"
     + " X-my-header:my\n"
     + " X-Okapi-Tenant:my-lib\n"
     + " Content-Length:0\n"
     + " Host:localhost:9230\n"
     + " X-Url-Params:q=a\n",
        cli.getResponsebody());
      async.complete();
    });
  }
}
