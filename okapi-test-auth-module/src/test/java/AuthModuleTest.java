
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.auth.MainVerticle;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class AuthModuleTest {
  private Vertx vertx;
  private static final int PORT = 9230;
  private static final String URL = "http://localhost:" + Integer.toString(PORT);
  private final Logger logger = OkapiLogger.get();

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
    cli.get("/notokentenant", res -> {
      context.assertTrue(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void testNoTokenNoTenantPermRequired(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.PERMISSIONS_REQUIRED, "foo,bar");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.get("/notokentenant", res -> {
      context.assertTrue(res.failed());
      context.assertEquals("401: Permissions required: foo,bar", res.cause().getMessage());
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
    cli.get("/notenant", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.USER, res.getType());
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

    cli.get("/badtoken", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.USER, res.getType());
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

    cli.get("/badjwt", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.USER, res.getType());
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

    cli.get("/badpayload", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.USER, res.getType());
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
    j.put("password", "badpassword");
    String body = j.encodePrettily();

    cli.post("/authn/login", body, res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.USER, res.getType());
      async.complete();
    });
  }

  @Test
  public void testEmptyLogin(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.post("/authn/login", "", res -> {
      context.assertTrue(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void testBadJsonLogin(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.post("/authn/login", "{", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.USER, res.getType());
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
      context.assertTrue(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void testOkLogin(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put("Content-Type", "application/json");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    JsonObject j = new JsonObject();
    j.put("tenant", "my-lib");
    j.put("username", "foo");
    j.put("password", "foo-password");
    String body = j.encodePrettily();

    cli.post("/authn/login", body, res -> {
      context.assertTrue(res.succeeded());
      cli.setOkapiToken(cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
      testNormal(context, cli, async);
    });
  }

  private void testNormal(TestContext context, OkapiClient cli, Async async) {
    cli.get("/normal", res -> {
      if (res.succeeded()) {
        cli.post("/normal", "{}", res2 -> {
          context.assertTrue(res2.succeeded());
          async.complete();
        });
      } else {
        context.assertTrue(res.succeeded());
        async.complete();
      }
    });
  }

  @Test
  public void testPostTenant1(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put("Content-Type", "application/json");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    JsonObject j = new JsonObject();
    j.put("tenant", "my-lib");
    j.put("username", "foo");
    j.put("password", "foo-password");
    String body = j.encodePrettily();

    cli.post("/authn/login", body, res -> {
      context.assertTrue(res.succeeded());
      cli.setOkapiToken(cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
      cli.post("/_/tenant", "{}", res2 -> {
        context.assertTrue(res2.succeeded());
        async.complete();
      });
    });
  }

  @Test
  public void testPostTenant2(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put("Content-Type", "application/json");
    headers.put(XOkapiHeaders.PERMISSIONS_REQUIRED, "a,b");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    JsonObject j = new JsonObject();
    j.put("tenant", "my-lib");
    j.put("username", "foo");
    j.put("password", "foo-password");
    String body = j.encodePrettily();

    cli.post("/authn/login", body, res -> {
      context.assertTrue(res.succeeded());
      cli.setOkapiToken(cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
      cli.post("/_/tenant", "{}", res2 -> {
        context.assertTrue(res2.failed());
        async.complete();
      });
    });
  }

  @Test
  public void testModulePermissions(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put("Content-Type", "application/json");

    JsonObject modulePermissions = new JsonObject();
    modulePermissions.put("modulea-1.0.0", new JsonArray().add("perm1"));
    modulePermissions.put("moduleb-1.0.0", new JsonArray().add("perm2").add("perm3"));
    headers.put(XOkapiHeaders.MODULE_PERMISSIONS, modulePermissions.encode());

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    JsonObject j = new JsonObject();
    j.put("tenant", "my-lib");
    j.put("username", "foo");
    j.put("password", "foo-password");
    String body = j.encodePrettily();

    cli.get("/normal", res -> {
      context.assertTrue(res.succeeded());
      JsonObject permsEcho = new JsonObject(cli.getRespHeaders().get(XOkapiHeaders.MODULE_TOKENS));
      context.assertTrue(permsEcho.containsKey("modulea-1.0.0"));
      context.assertTrue(permsEcho.containsKey("moduleb-1.0.0"));
      async.complete();
    });
  }

  @Test
  public void testPermissionsRequired(TestContext context) {

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put("Content-Type", "application/json");

    {
      Async async = context.async();
      JsonObject j = new JsonObject();
      j.put("tenant", "my-lib");
      j.put("username", "foo");
      j.put("password", "foo-password");
      j.put("permissions", new JsonArray(Arrays.asList("perm-a")));
      String body = j.encodePrettily();
      OkapiClient cli = new OkapiClient(URL, vertx, headers);

      cli.post("/authn/login", body, res -> {
        context.assertTrue(res.succeeded());
        headers.put(XOkapiHeaders.TOKEN, cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      headers.put(XOkapiHeaders.PERMISSIONS_REQUIRED, "perm-a");
      OkapiClient cli = new OkapiClient(URL, vertx, headers);
      cli.get("/normal", res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      headers.put(XOkapiHeaders.PERMISSIONS_REQUIRED, "perm-a,perm-b");
      OkapiClient cli = new OkapiClient(URL, vertx, headers);
      cli.get("/normal", res -> {
        context.assertTrue(res.failed());
        async.complete();
      });
      async.await();
    }
  }


  @Test
  public void testFilterResponse(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put(XOkapiHeaders.FILTER, "pre");
    headers.put("X-filter-pre", "404");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    cli.get("/normal", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.NOT_FOUND, res.getType());
      async.complete();
    });
  }

  @Test
  public void testFilterError(TestContext context) {
    Async async = context.async();

    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put(XOkapiHeaders.FILTER, "pre");
    headers.put("X-filter-pre-error", "true");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    cli.get("/normal", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      async.complete();
    });
  }

  @Test
  public void testFilterRequestHeaders(TestContext context) {
    for (String phase : Arrays.asList(XOkapiHeaders.FILTER_PRE,
        XOkapiHeaders.FILTER_POST)) {
      Async async = context.async();
      HashMap<String, String> headers = new HashMap<>();
      headers.put(XOkapiHeaders.URL, URL);
      headers.put(XOkapiHeaders.TENANT, "my-lib");
      headers.put(XOkapiHeaders.FILTER, phase);

      headers.put("X-request-" + phase + "-error", "true");
      headers.put(XOkapiHeaders.REQUEST_IP, "10.0.0.1");
      headers.put(XOkapiHeaders.REQUEST_TIMESTAMP, "123");
      headers.put(XOkapiHeaders.REQUEST_METHOD, "GET");

      OkapiClient cli = new OkapiClient(URL, vertx, headers);

      cli.get("/normal", res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, res.getType());
        async.complete();
      });
    }
  }

}
