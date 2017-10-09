
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.HashMap;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.sample.MainVerticle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class SamleModuleTest {

  private Vertx vertx;
  private static final int PORT = 9130;
  private static final String URL = "http://localhost:" + Integer.toString(PORT);
  private final Logger logger = LoggerFactory.getLogger("okapi");

  @Before
  public void setUp(TestContext context) {
    logger.debug("setUp");
    vertx = Vertx.vertx();

    System.setProperty("port", Integer.toString(PORT));

    DeploymentOptions opt = new DeploymentOptions();
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
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
      assertTrue(res.succeeded());
      test2(cli, async);
    });
  }

  public void test2(OkapiClient cli, Async async) {
    cli.post("/testb", "FOO", res -> {
      assertTrue(res.succeeded());
      assertEquals("Hello FOO", cli.getResponsebody());
      test3(cli, async);
    });
  }

  public void test3(OkapiClient cli, Async async) {
    cli.get("/recurse?depth=2", res -> {
      assertTrue(res.succeeded());
      assertEquals("2 1 Recursion done", cli.getResponsebody());
      test4(cli, async);
    });
  }

  public void test4(OkapiClient cli, Async async) {
    cli.get("/_/tenant", res -> {
      assertTrue(res.succeeded());
      assertEquals("GET request to okapi-test-module tenant service for "
        + "tenant my-lib\n",
        cli.getResponsebody());
      async.complete();
    });
  }

  @Test
  public void testAllHeaders(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();

    headers.put("X-all-headers", "HB");
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.enableInfoLog();
    cli.get("/testb", res -> {
      assertTrue(res.succeeded());
      async.complete();
    });
  }
}
