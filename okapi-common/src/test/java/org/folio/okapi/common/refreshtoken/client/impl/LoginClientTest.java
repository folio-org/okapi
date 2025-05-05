package org.folio.okapi.common.refreshtoken.client.impl;

import static org.folio.okapi.common.ChattyResponsePredicate.SC_BAD_REQUEST;
import static org.folio.okapi.common.ChattyResponsePredicate.SC_CREATED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.okapi.common.Constants;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.refreshtoken.client.Client;
import org.folio.okapi.common.refreshtoken.client.ClientException;
import org.folio.okapi.common.refreshtoken.client.ClientOptions;
import org.folio.okapi.common.refreshtoken.tokencache.TenantUserCache;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class LoginClientTest {
  static Vertx vertx;

  private static final int MOCK_PORT = 9230;

  private static final String OKAPI_URL = "http://localhost:" + MOCK_PORT;

  private static final String TENANT_OK = "diku";
  private static final String USER_OK = "dikuuser";
  private static final String PASSWORD_OK = "abc123";

  private static boolean enableLoginWithExpiry = false;

  private static boolean returnCookies;

  private static int cookieAge;

  private static int countLoginWithExpiry;

  WebClient webClient;

  TenantUserCache tokenCache;

  @BeforeClass
  public static void beforeClass(TestContext context) {
    vertx = Vertx.vertx();
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    // legacy login
    router.post("/authn/login").handler(ctx -> {
      HttpServerRequest request = ctx.request();
      HttpServerResponse response = ctx.response();
      if (!"application/json".equals(request.getHeader("Content-Type"))) {
        response.setStatusCode(400);
        response.putHeader("Content-Type", "text/plain");
        response.end("Bad Request");
        return;
      }
      JsonObject login = ctx.body().asJsonObject();
      String username = login.getString("username");
      String password = login.getString("password");
      if (!TENANT_OK.equals(request.getHeader(XOkapiHeaders.TENANT))
          || !USER_OK.equals(username) || !PASSWORD_OK.equals(password)) {
        response.setStatusCode(400);
        response.putHeader("Content-Type", "text/plain");
        response.end("Bad tenant/username/password");
        return;
      }
      response.setStatusCode(201);
      if (returnCookies) {
        response.putHeader(XOkapiHeaders.TOKEN, "validtoken");
      }
      response.putHeader("Content-Type", "application/json");
      response.end(login.encode());
    });
    router.post("/authn/login-with-expiry").handler(ctx -> {
      HttpServerRequest request = ctx.request();
      HttpServerResponse response = ctx.response();
      if (!enableLoginWithExpiry) {
        response.setStatusCode(404);
        response.putHeader("Content-Type", "text/plain");
        response.end("Not found");
        return;
      }
      countLoginWithExpiry++;
      if (!"application/json".equals(request.getHeader("Content-Type"))) {
        response.setStatusCode(400);
        response.putHeader("Content-Type", "text/plain");
        response.end("Bad Request");
        return;
      }
      JsonObject login = ctx.body().asJsonObject();
      String username = login.getString("username");
      String password = login.getString("password");
      if (!TENANT_OK.equals(request.getHeader(XOkapiHeaders.TENANT))
          || !USER_OK.equals(username) || !PASSWORD_OK.equals(password)) {
        response.setStatusCode(400);
        response.putHeader("Content-Type", "text/plain");
        response.end("Bad tenant/username/password");
        return;
      }
      response.setStatusCode(201);
      if (returnCookies) {
        response.addCookie(Cookie.cookie("a", "validtoken")
                .setMaxAge(3000)
                .setSecure(true)
                .setPath("/")
                .setHttpOnly(true)
                .setSameSite(CookieSameSite.NONE));
        response.addCookie(
            Cookie.cookie(Constants.COOKIE_ACCESS_TOKEN, "validtoken")
                .setMaxAge(cookieAge)
                .setSecure(true)
                .setPath("/")
                .setHttpOnly(true)
                .setSameSite(CookieSameSite.NONE));
        response.addCookie(
            Cookie.cookie("tokenafter", "z")
                .setMaxAge(3000)
                .setSecure(true)
                .setPath("/")
                .setHttpOnly(true)
                .setSameSite(CookieSameSite.NONE));
      }
      response.putHeader("Content-Type", "application/json");
      response.end("{}");
    });

    // module example call ; note different content-type than for login
    router.post("/echo").handler(ctx -> {
      HttpServerRequest request = ctx.request();
      HttpServerResponse response = ctx.response();
      if (request.getHeader(XOkapiHeaders.TOKEN) == null) {
        response.setStatusCode(401);
        response.putHeader("Content-Type", "text/plain");
        response.end("No token provided");
        return;
      }
      if (request.getHeader(XOkapiHeaders.TENANT) == null) {
        response.setStatusCode(401);
        response.putHeader("Content-Type", "text/plain");
        response.end("No tenant header provided");
        return;
      }
      if (!"text/xml".equals(request.getHeader("Content-Type"))) {
        response.setStatusCode(400);
        response.putHeader("Content-Type", "text/plain");
        response.end("Only test/xml supported");
        return;
      }
      response.setStatusCode(201);
      response.putHeader("Content-Type", "text/xml");
      response.end(ctx.body().asString());
    });
    vertx.createHttpServer().requestHandler(router).listen(MOCK_PORT)
        .onComplete(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterClass(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Before
  public void before() {
    enableLoginWithExpiry = false;
    returnCookies = true;
    cookieAge = 300;
    webClient = WebClient.create(vertx);
    tokenCache = new TenantUserCache(10);
    countLoginWithExpiry = 0;
  }

  @After
  public void after() {
    webClient.close();
  }

  @Test
  public void nullCache(TestContext context) {
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = Client.createLoginClient(
        new ClientOptions().webClient(webClient).okapiUrl(OKAPI_URL),
        null /* cache */,
        TENANT_OK, USER_OK, () -> Future.succeededFuture(PASSWORD_OK));
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response ->
            assertThat(response.bodyAsBuffer(), is(xmlBody))
        ));
  }

  @Test
  public void nullPassword(TestContext context) {
    Client client = Client.createLoginClient(
        new ClientOptions().webClient(webClient).okapiUrl(OKAPI_URL),
        tokenCache,
        TENANT_OK, USER_OK, () -> Future.succeededFuture(null));
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_BAD_REQUEST))
        .onComplete(context.asyncAssertFailure(t -> {
          assertThat(t.getMessage(), is("Login failed. POST /authn/login "
              + "for tenant 'diku' and username 'dikuuser' returned status 400: "
              + "Bad tenant/username/password"));
          assertThat(countLoginWithExpiry, is(0));
        }));
  }

  Client getLoginClient(TenantUserCache theCache) {
    return Client.createLoginClient(
        new ClientOptions().webClient(webClient).okapiUrl(OKAPI_URL),
        theCache,
        TENANT_OK, USER_OK, () -> Future.succeededFuture(PASSWORD_OK));
  }

  @Test
  public void legacy(TestContext context) {
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = getLoginClient(tokenCache);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> assertThat(response.bodyAsBuffer(), is(xmlBody))))
        .compose(x -> client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_CREATED)))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> {
          assertThat(response.bodyAsBuffer(), is(xmlBody));
          assertThat(countLoginWithExpiry, is(0));
        }));
  }

  @Test
  public void legacyNoToken(TestContext context) {
    returnCookies = false;
    Client client = getLoginClient(tokenCache);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_CREATED))
        .onComplete(context.asyncAssertFailure(cause ->
            assertThat(cause.getMessage(), is("Login failed. POST /authn/login "
                + "for tenant 'diku' and username 'dikuuser' did not return token."))
        ));
  }

  @Test
  public void withExpiry(TestContext context) {
    enableLoginWithExpiry = true;
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = getLoginClient(tokenCache);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> assertThat(response.bodyAsBuffer(), is(xmlBody))))
        .compose(x -> client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_CREATED)))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> {
          assertThat(response.bodyAsBuffer(), is(xmlBody));
          assertThat(countLoginWithExpiry, is(1));
        }));
  }

  @Test
  public void withExpiryAge0(TestContext context) {
    enableLoginWithExpiry = true;
    cookieAge = 0;
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = getLoginClient(tokenCache);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> {
          assertThat(response.bodyAsBuffer(), is(xmlBody));
          assertThat(countLoginWithExpiry, is(1));
        }));
  }

  @Test
  public void withExpiryNoCookies(TestContext context) {
    enableLoginWithExpiry = true;
    returnCookies = false;
    Client client = getLoginClient(tokenCache);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_CREATED))
        .onComplete(context.asyncAssertFailure(response -> {
          assertThat(response.getMessage(), is("Login failed. POST /authn/login-with-expiry "
              + "for tenant 'diku' and username 'dikuuser' did not return access token"));
        }));
  }

  @Test
  public void withExpiryNullCache(TestContext context) {
    enableLoginWithExpiry = true;
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = getLoginClient(null);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> assertThat(response.bodyAsBuffer(), is(xmlBody))))
        .compose(x -> client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_CREATED)))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> {
          assertThat(response.bodyAsBuffer(), is(xmlBody));
          assertThat(countLoginWithExpiry, is(2));
        }));
  }

  @Test
  public void badPasswordWithExpiry(TestContext context) {
    enableLoginWithExpiry = true;
    Client client = Client.createLoginClient(
        new ClientOptions().webClient(webClient).okapiUrl(OKAPI_URL),
        tokenCache,
        TENANT_OK, USER_OK, () -> Future.succeededFuture("bad"));
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(SC_BAD_REQUEST))
        .onComplete(context.asyncAssertFailure(e -> {
          assertThat(countLoginWithExpiry, is(1));
          assertThat(e, Matchers.instanceOf(ClientException.class));
          assertThat(e.getMessage(), is("Login failed. POST /authn/login-with-expiry "
              + "for tenant 'diku' and username 'dikuuser' returned status 400: Bad tenant/username/password"));
        }));
  }

  @Test
  public void getTokenLegacyMalformedUrl(TestContext context) {
    new LoginClient(new ClientOptions()
        .webClient(webClient)
        .okapiUrl("x"), tokenCache, TENANT_OK, USER_OK, () -> Future.succeededFuture(PASSWORD_OK))
        .getTokenLegacy(new JsonObject())
        .onComplete(context.asyncAssertFailure(e ->
            assertThat(e.getMessage(), is("java.net.MalformedURLException: no protocol: x/authn/login"))));
  }

  @Test
  public void getTokenWithExpiryMalformedUrl(TestContext context) {
    new LoginClient(new ClientOptions()
        .webClient(webClient)
        .okapiUrl("x"), tokenCache, TENANT_OK, USER_OK, () -> Future.succeededFuture(PASSWORD_OK))
        .getTokenWithExpiry(new JsonObject())
        .onComplete(context.asyncAssertFailure(e ->
            assertThat(e.getMessage(), is("java.net.MalformedURLException: no protocol: x/authn/login-with-expiry"))));
  }

  private void assertBody(String contentType, String bufferString, String expectedBody) {
    var buffer = Buffer.buffer(bufferString);
    HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
    when(httpResponse.bodyAsBuffer()).thenReturn(buffer);
    when(httpResponse.getHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentType);
    assertThat(LoginClient.body(httpResponse), is(expectedBody));
  }

  @Test
  public void bodyEmpty() {
    assertBody("application/json", "", "");
  }

  @Test
  public void bodyNoContentType() {
    assertBody(null, " { } ", " { } ");
  }

  @Test
  public void bodyText() {
    assertBody("text/plain", " { } ", " { } ");
  }

  @Test
  public void bodyJson() {
    assertBody("application/json", " { } ", "{}");
  }

  @Test
  public void bodyJsonSemicolon() {
    assertBody("Application/JSON;xyz", " { } ", "{}");
  }

  @Test
  public void bodyInvalidJson() {
    assertBody("application/json", " } ", " } ");
  }
}
