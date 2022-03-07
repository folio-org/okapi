
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.HashMap;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.header.MainVerticle;


@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class HeaderModuleTest {

  private static Vertx vertx;
  private static final int PORT = 9230;
  private static final String URL = "http://localhost:" + Integer.toString(PORT);
  private static final Logger logger = OkapiLogger.get();

  public HeaderModuleTest() {
  }

  @BeforeClass
  public static void setUp(TestContext context) {
    logger.debug("setUp");
    vertx = Vertx.vertx();

    System.setProperty("port", Integer.toString(PORT));

    DeploymentOptions opt = new DeploymentOptions();
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @AfterClass
  public static void tearDown(TestContext context) {
    System.clearProperty("port");
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testMyHeaderDefault(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    Async async = context.async();
    cli.get("/testb", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("foo", cli.getRespHeaders().get("X-my-header"));
      async.complete();
    });
    async.await();
  }

  @Test
  public void testMyHeaderValue(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put("X-my-header", "hello");
    Async async = context.async();
    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    cli.get("/testb", res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals("hello,foo", cli.getRespHeaders().get("X-my-header"));
      async.complete();
    });
    async.await();
  }

  @Test
  public void testPermissionsFail(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, "testlib");
    Async async = context.async();
    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.post("/_/tenantPermissions", "{", res -> {
      context.assertTrue(res.failed());
      async.complete();
    });
    async.await();
  }

  @Test
  public void testPermissionsOK(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, "testlib");
    JsonObject perm = new JsonObject("{\"k\": \"v\"}");
    {
      Async async = context.async();
      OkapiClient cli = new OkapiClient(URL, vertx, headers);
      cli.post("/_/tenantPermissions", perm.encode(), res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("POST test-header-module /_/tenantPermissions 200 -",
            cli.getRespHeaders().get(XOkapiHeaders.TRACE));
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      OkapiClient cli = new OkapiClient(URL, vertx, headers);
      cli.get("/permResult", res -> {
        context.assertEquals(perm, new JsonArray(res.result()).getJsonObject(0));
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      headers.replace(XOkapiHeaders.TENANT, "other");
      OkapiClient cli = new OkapiClient(URL, vertx, headers);
      cli.get("/permResult", res -> {
        context.assertNull(res.result());
        async.complete();
      });
      async.await();
    }
  }
}
