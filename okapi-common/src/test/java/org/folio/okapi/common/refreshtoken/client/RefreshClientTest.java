package org.folio.okapi.common.refreshtoken.client;

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
import org.folio.okapi.common.refreshtoken.tokencache.RefreshTokenCache;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(VertxUnitRunner.class)
public class RefreshClientTest {

  private static final Logger log = LogManager.getLogger(RefreshClientTest.class);

  static Vertx vertx;

  private static final int MOCK_PORT = 9230;

  private static final String OKAPI_URL = "http://localhost:" + MOCK_PORT;

  private static final String TENANT_OK = "diku";

  private static final String VALID_REFRESH_TOKEN = "diku";

  WebClient webClient;

  private static boolean enableRefresh;

  private static boolean returnCookies;

  private static int cookieAge;

  private static int countWithRefresh;

  RefreshTokenCache tokenCache;

  @BeforeClass
  public static void beforeClass(TestContext context) {
    vertx = Vertx.vertx();
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.post("/authn/refresh").handler(ctx -> {
      HttpServerRequest request = ctx.request();
      HttpServerResponse response = ctx.response();
      if (!enableRefresh) {
        response.setStatusCode(404);
        response.putHeader("Content-Type", "text/plain");
        response.end("Not found");
        return;
      }
      countWithRefresh++;
      String refreshToken = null;
      for (Cookie c : request.cookies()) {
        if (c.getName().equals(Constants.COOKIE_REFRESH_TOKEN)) {
          refreshToken = c.getValue();
        }
      }
      if (!TENANT_OK.equals(request.getHeader(XOkapiHeaders.TENANT))
          || !VALID_REFRESH_TOKEN.equals(refreshToken)) {
        response.setStatusCode(400);
        response.putHeader("Content-Type", "text/plain");
        response.end("Missing/bad refresh token");
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
            Cookie.cookie(Constants.COOKIE_REFRESH_TOKEN, refreshToken)
                .setMaxAge(86000)
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
    enableRefresh = true;
    returnCookies = true;
    cookieAge = 300;
    webClient = WebClient.create(vertx);
    tokenCache = new RefreshTokenCache(10);
    countWithRefresh = 0;
  }

  @After
  public void after() {
    webClient.close();
  }

  Client getRefreshClient(RefreshTokenCache theCache, String refreshToken) {
    return Client.createRefreshClient(
        new ClientOptions().webClient(webClient).okapiUrl(OKAPI_URL),
        theCache, TENANT_OK, refreshToken);
  }

  @Test
  public void refreshOk(TestContext context) {
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = getRefreshClient(tokenCache, VALID_REFRESH_TOKEN);
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
          assertThat(countWithRefresh, is(1));
        }));
  }

  @Test
  public void refreshNullCache(TestContext context) {
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = getRefreshClient(null, VALID_REFRESH_TOKEN);
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
          assertThat(countWithRefresh, is(2));
        }));
  }

  @Test
  public void refreshExpiryAge0(TestContext context) {
    cookieAge = 0;
    Buffer xmlBody = Buffer.buffer("<hi/>");
    Client client = getRefreshClient(tokenCache, VALID_REFRESH_TOKEN);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .onComplete(context.asyncAssertSuccess(response -> {
          assertThat(response.bodyAsBuffer(), is(xmlBody));
          assertThat(countWithRefresh, is(1));
        }));
  }

  @Test
  public void refreshNotFound(TestContext context) {
    enableRefresh = false;
    Client client = getRefreshClient(tokenCache, VALID_REFRESH_TOKEN);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .expect(ResponsePredicate.SC_CREATED))
        .onComplete(context.asyncAssertFailure(cause ->
          assertThat(cause.getMessage(), is("Not found"))
        ));
  }

  @Test
  public void refreshNoCookiesReturned(TestContext context) {
    returnCookies = false;
    Client client = getRefreshClient(tokenCache, VALID_REFRESH_TOKEN);
    client.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .expect(ResponsePredicate.SC_CREATED))
        .onComplete(context.asyncAssertFailure(cause ->
            assertThat(cause.getMessage(), is("/authn/refresh did not return access token"))
        ));
  }

}
