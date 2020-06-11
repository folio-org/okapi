
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.get("/testb", res -> {
      cli.close();
      context.assertTrue(res.succeeded());
      context.assertEquals("foo", cli.getRespHeaders().get("X-my-header"));
      test2(context, async);
    });
  }

  private void test2(TestContext context, Async async) {

    HashMap<String, String> headers = new HashMap<>();
    headers.put("X-my-header", "hello");
    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.get("/testb", res -> {
      cli.close();
      context.assertTrue(res.succeeded());
      context.assertEquals("hello,foo", cli.getRespHeaders().get("X-my-header"));
      test3(context, async);
    });

  }

  public void test3(TestContext context, Async async) {

    HashMap<String, String> headers = new HashMap<>();

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.post("/_/tenantPermissions", "{", res1 -> {
      context.assertTrue(res1.failed());
      JsonObject perm = new JsonObject("{\"k\": \"v\"}");
      cli.post("/_/tenantPermissions", perm.encode(), res2 -> {
        context.assertTrue(res2.succeeded());
        cli.get("/permResult", res3 -> {
          cli.close();
          context.assertEquals(perm.encode(), new JsonArray(res3.result()).getJsonObject(0).encode());
          async.complete();
        });
      });
    });
  }

}
