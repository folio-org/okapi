
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.After;
import org.junit.Before;
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

  private Vertx vertx;
  private static final int PORT = 9230;
  private static final String URL = "http://localhost:" + Integer.toString(PORT);
  private final Logger logger = OkapiLogger.get();

  public HeaderModuleTest() {
  }

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
    HashMap<String, String> headers = new HashMap<>();
    {
      OkapiClient cli = new OkapiClient(URL, vertx, headers);
      Async async = context.async();
      cli.get("/testb", res -> {
        cli.close();
        context.assertTrue(res.succeeded());
        context.assertEquals("foo", cli.getRespHeaders().get("X-my-header"));
        async.complete();
      });
      async.await();
    }
    headers = new HashMap<>();
    headers.put("X-my-header", "hello");
    {
      Async async = context.async();
      OkapiClient cli = new OkapiClient(URL, vertx, headers);

      cli.get("/testb", res -> {
        cli.close();
        context.assertTrue(res.succeeded());
        context.assertEquals("hello,foo", cli.getRespHeaders().get("X-my-header"));
        async.complete();
      });
      async.await();
    }
    headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, "testlib");
    JsonObject perm = new JsonObject("{\"k\": \"v\"}");
    {
      Async async = context.async();
      OkapiClient cli = new OkapiClient(URL, vertx, headers);
      cli.post("/_/tenantPermissions", "{", res1 -> {
        context.assertTrue(res1.failed());
        cli.post("/_/tenantPermissions", perm.encode(), res2 -> {
          context.assertTrue(res2.succeeded());
          context.assertEquals("POST test-header-module /_/tenantPermissions 200 -",
              cli.getRespHeaders().get(XOkapiHeaders.TRACE));
          async.complete();
        });
      });
      async.await();
    }
    {
      Async async = context.async();
      OkapiClient cli = new OkapiClient(URL, vertx, headers);
      cli.get("/permResult", res -> {
        context.assertEquals(perm, new JsonArray(res.result()).getJsonObject(0));
        cli.close();
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
        cli.close();
        async.complete();
      });
      async.await();
    }
  }
}
