
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.HashMap;
import org.folio.okapi.auth.MainVerticle;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class AuthModuleTest {
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
  public void testNoTokenNoTenant(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.get("/testb", res -> {
      assertTrue(res.failed());
      assertEquals(ErrorType.INTERNAL, res.getType());
      async.complete();
    });
  }

  @Test
  public void testNoTenant(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.setOkapiToken("a.b.c");
    cli.get("/testb", res -> {
      assertTrue(res.failed());
      assertEquals(ErrorType.INTERNAL, res.getType());
      async.complete();
    });
  }

  @Test
  public void testBadToken(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.setOkapiToken("a.b");

    cli.get("/testb", res -> {
      assertTrue(res.failed());
      assertEquals(ErrorType.INTERNAL, res.getType());
      async.complete();
    });
  }

  @Test
  public void testBadTokenJwt(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.setOkapiToken("a.b.c");

    cli.get("/testb", res -> {
      assertTrue(res.failed());
      assertEquals(ErrorType.INTERNAL, res.getType());
      async.complete();
    });
  }

  @Test
  public void testBadTokenPayload(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.setOkapiToken("dummyJwt.b.c");

    cli.get("/testb", res -> {
      assertTrue(res.failed());
      assertEquals(ErrorType.INTERNAL, res.getType());
      async.complete();
    });
  }

  @Test
  public void testBadLogin(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    JsonObject j = new JsonObject();
    j.put("tenant", "my-lib");
    j.put("username", "foo");
    j.put("password", "bar");
    String body = j.encodePrettily();

    cli.post("/authn/login", body, res -> {
      assertTrue(res.failed());
      assertEquals(ErrorType.INTERNAL, res.getType());
      async.complete();
    });
  }

  @Test
  public void testGetLogin(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    cli.get("/authn/login", res -> {
      assertTrue(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void testOkLogin(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    JsonObject j = new JsonObject();
    j.put("tenant", "my-lib");
    j.put("username", "foo");
    j.put("password", "foo-password");
    String body = j.encodePrettily();

    cli.post("/authn/login", body, res -> {
      assertTrue(res.succeeded());
      cli.setOkapiToken(cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
      testNormal(cli, async);
    });
  }

  private void testNormal(OkapiClient cli, Async async) {
    cli.get("/some", res -> {
      assertTrue(res.succeeded());
      async.complete();
    });
  }
}
