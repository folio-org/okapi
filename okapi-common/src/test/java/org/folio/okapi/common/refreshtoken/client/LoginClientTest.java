package org.folio.okapi.common.refreshtoken.client;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Constants;
import org.folio.okapi.common.XOkapiHeaders;
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
  private static final Logger log = LogManager.getLogger(LoginClientTest.class);

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
        log.info("legacy login failed");
        response.setStatusCode(400);
        response.putHeader("Content-Type", "text/plain");
        response.end("Bad tenant/username/password");
        return;
      }
      log.info("legacy login OK");
      response.setStatusCode(201);
      response.putHeader(XOkapiHeaders.TOKEN, "validtoken");
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
      log.info("login with expiry ok");
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
      response.headers().forEach((n, v) -> log.info("{}:{}", n, v));
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
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response ->
            assertThat(response.bodyAsBuffer(), is(xmlBody))
        ));
  }

  @Test
  public void nullPassword(TestContext context) {
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = Client.createLoginClient(
        new ClientOptions().webClient(webClient).okapiUrl(OKAPI_URL),
        tokenCache,
        TENANT_OK, USER_OK, () -> Future.succeededFuture(null));
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_BAD_REQUEST))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertFailure(t -> {
          assertThat(t.getMessage(), is("Bad tenant/username/password"));
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
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> assertThat(response.bodyAsBuffer(), is(xmlBody))))
        .compose(x -> client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED)))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> {
          assertThat(response.bodyAsBuffer(), is(xmlBody));
          assertThat(countLoginWithExpiry, is(0));
        }));
  }

  @Test
  public void withExpiry(TestContext context) {
    enableLoginWithExpiry = true;
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = getLoginClient(tokenCache);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> assertThat(response.bodyAsBuffer(), is(xmlBody))))
        .compose(x -> client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED)))
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
            .expect(ResponsePredicate.SC_CREATED))
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
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = getLoginClient(tokenCache);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertFailure(response -> {
          assertThat(response.getMessage(), is("/authn/login-with-expiry did not return access token"));
        }));
  }

  @Test
  public void withExpiryNullCache(TestContext context) {
    enableLoginWithExpiry = true;
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = getLoginClient(null);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> assertThat(response.bodyAsBuffer(), is(xmlBody))))
        .compose(x -> client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED)))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> {
          assertThat(response.bodyAsBuffer(), is(xmlBody));
          assertThat(countLoginWithExpiry, is(2));
        }));
  }

  @Test
  public void badPasswordWithExpiry(TestContext context) {
    enableLoginWithExpiry = true;
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = Client.createLoginClient(
        new ClientOptions().webClient(webClient).okapiUrl(OKAPI_URL),
        tokenCache,
        TENANT_OK, USER_OK, () -> Future.succeededFuture("bad"));
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_BAD_REQUEST))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertFailure(e -> {
          assertThat(countLoginWithExpiry, is(1));
          assertThat(e, Matchers.instanceOf(ClientException.class));
          assertThat(e.getMessage(), is("Bad tenant/username/password"));
        }));
  }

}
