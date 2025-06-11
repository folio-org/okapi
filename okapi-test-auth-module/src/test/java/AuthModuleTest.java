
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.folio.okapi.auth.MainVerticle;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ErrorTypeException;
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
    vertx.deployVerticle(MainVerticle.class.getName(), opt).onComplete(context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testNoTokenNoTenant(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.get("/notokentenant").onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testNoTokenNoTenantPermRequired(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.PERMISSIONS_REQUIRED, "foo,bar");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.get("/notokentenant").onComplete(context.asyncAssertFailure(res -> {
      context.assertEquals("401: Permissions required: foo,bar", res.getMessage());
    }));
  }

  @Test
  public void testNoTenant(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.setOkapiToken("a.b.c");
    cli.get("/notenant").onComplete(context.asyncAssertFailure(res -> {
      context.assertEquals(ErrorType.USER, ErrorTypeException.getType(res));
    }));
  }

  @Test
  public void testBadToken(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.setOkapiToken("a.b");

    cli.get("/badtoken").onComplete(context.asyncAssertFailure(res -> {
      context.assertEquals(ErrorType.USER, ErrorTypeException.getType(res));
    }));
  }

  @Test
  public void testBadTokenJwt(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.setOkapiToken("a.b.c");

    cli.get("/badjwt").onComplete(context.asyncAssertFailure(res -> {
      context.assertEquals(ErrorType.USER, ErrorTypeException.getType(res));
    }));
  }

  @Test
  public void testBadTokenPayload(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.setOkapiToken("dummyJwt.b.c");

    cli.get("/badpayload").onComplete(context.asyncAssertFailure(res -> {
      context.assertEquals(ErrorType.USER, ErrorTypeException.getType(res));
    }));
  }

  @Test
  public void testBadLogin(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    JsonObject j = new JsonObject();
    j.put("tenant", "my-lib");
    j.put("username", "foo");
    j.put("password", "badpassword");
    String body = j.encodePrettily();

    cli.post("/authn/login", body).onComplete(context.asyncAssertFailure(res -> {
      context.assertEquals(ErrorType.USER, ErrorTypeException.getType(res));
    }));
  }

  @Test
  public void testEmptyLogin(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.post("/authn/login", "").onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testBadJsonLogin(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);
    cli.post("/authn/login", "{").onComplete(context.asyncAssertFailure(res -> {
      context.assertEquals(ErrorType.USER, ErrorTypeException.getType(res));
    }));
  }

  @Test
  public void testGetLogin(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    cli.get("/authn/login").onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testOkLogin(TestContext context) {
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

    cli.post("/authn/login", body).onComplete(context.asyncAssertSuccess(res -> {
      cli.setOkapiToken(cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
      cli.get("/normal").onComplete(context.asyncAssertSuccess(res2 -> {
        cli.post("/normal", "{}").onComplete(context.asyncAssertSuccess());
      }));
    }));
  }

  @Test
  public void testPostTenant1(TestContext context) {
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

    cli.post("/authn/login", body).onComplete(context.asyncAssertSuccess(res -> {
      cli.setOkapiToken(cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
      cli.post("/_/tenant", "{}").onComplete(context.asyncAssertSuccess(res2 -> {
        String p = cli.getRespHeaders().get(XOkapiHeaders.PERMISSIONS);
        context.assertEquals("magic", p);
        headers.put(XOkapiHeaders.PERMISSIONS, p);
        cli.setHeaders(headers);
        cli.setOkapiToken(cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
        cli.post("/_/tenant", "{}").onComplete(context.asyncAssertSuccess(res3 -> {
          cli.get("/authn/listTenants").onComplete(context.asyncAssertSuccess(res4 -> {
            context.assertEquals(new JsonArray(List.of("my-lib")).encodePrettily(), res4);
          }));
        }));
      }));
    }));
  }

  @Test
  public void testPostTenant2(TestContext context) {
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

    cli.post("/authn/login", body).onComplete(context.asyncAssertSuccess(res -> {
      cli.setOkapiToken(cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
      cli.post("/_/tenant", "{}").onComplete(context.asyncAssertFailure());
    }));
  }

  @Test
  public void testModulePermissions(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put("Content-Type", "application/json");

    JsonObject modulePermissions = new JsonObject();
    modulePermissions.put("modulea-1.0.0", new JsonArray().add("perm1"));
    modulePermissions.put("moduleb-1.0.0", new JsonArray().add("perm2").add("perm3"));
    headers.put(XOkapiHeaders.MODULE_PERMISSIONS, modulePermissions.encode());

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    cli.get("/normal").onComplete(context.asyncAssertSuccess(res -> {
      JsonObject permsEcho = new JsonObject(cli.getRespHeaders().get(XOkapiHeaders.MODULE_TOKENS));
      context.assertTrue(permsEcho.containsKey("modulea-1.0.0"));
      context.assertTrue(permsEcho.containsKey("moduleb-1.0.0"));
    }));
  }

  @Test
  public void testPermissionsRequired(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put("Content-Type", "application/json");

    {
      JsonObject j = new JsonObject();
      j.put("tenant", "my-lib");
      j.put("username", "foo");
      j.put("password", "foo-password");
      j.put("permissions", new JsonArray(Arrays.asList("perm-a")));
      String body = j.encodePrettily();
      OkapiClient cli = new OkapiClient(URL, vertx, headers);

      cli.post("/authn/login", body).onComplete(context.asyncAssertSuccess(res -> {

        headers.put(XOkapiHeaders.TOKEN, cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
        headers.put(XOkapiHeaders.PERMISSIONS_REQUIRED, "perm-a");
        OkapiClient cli2 = new OkapiClient(URL, vertx, headers);
        cli2.get("/normal").onComplete(context.asyncAssertSuccess());

        headers.put(XOkapiHeaders.TOKEN, cli.getRespHeaders().get(XOkapiHeaders.TOKEN));
        headers.put(XOkapiHeaders.PERMISSIONS_REQUIRED, "perm-a,perm-b");
        OkapiClient cli3 = new OkapiClient(URL, vertx, headers);
        cli3.get("/normal").onComplete(context.asyncAssertFailure());
      }));
    }
  }

  @Test
  public void testFilterResponse(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put(XOkapiHeaders.FILTER, "pre");
    headers.put("X-filter-pre", "404");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    cli.get("/normal").onComplete(context.asyncAssertFailure(res -> {
      context.assertEquals(ErrorType.NOT_FOUND, ErrorTypeException.getType(res));
    }));
   }

  @Test
  public void testFilterError(TestContext context) {
    HashMap<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.URL, URL);
    headers.put(XOkapiHeaders.TENANT, "my-lib");
    headers.put(XOkapiHeaders.FILTER, "pre");
    headers.put("X-filter-pre-error", "true");

    OkapiClient cli = new OkapiClient(URL, vertx, headers);

    cli.get("/normal").onComplete(context.asyncAssertFailure(res -> {
      context.assertEquals(ErrorType.INTERNAL, ErrorTypeException.getType(res));
    }));
  }

  @Test
  public void testFilterRequestHeaders(TestContext context) {
    for (String phase : Arrays.asList(XOkapiHeaders.FILTER_PRE,
        XOkapiHeaders.FILTER_POST)) {
      HashMap<String, String> headers = new HashMap<>();
      headers.put(XOkapiHeaders.URL, URL);
      headers.put(XOkapiHeaders.TENANT, "my-lib");
      headers.put(XOkapiHeaders.FILTER, phase);

      headers.put("X-request-" + phase + "-error", "true");
      headers.put(XOkapiHeaders.REQUEST_IP, "10.0.0.1");
      headers.put(XOkapiHeaders.REQUEST_TIMESTAMP, "123");
      headers.put(XOkapiHeaders.REQUEST_METHOD, "GET");

      OkapiClient cli = new OkapiClient(URL, vertx, headers);

      cli.get("/normal").onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals(ErrorType.INTERNAL, ErrorTypeException.getType(res));
      }));
    }
  }

}
