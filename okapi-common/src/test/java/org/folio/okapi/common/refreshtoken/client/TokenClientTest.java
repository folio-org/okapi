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
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.refreshtoken.tokencache.TokenCache;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TokenClientTest {
  private static final Logger log = LogManager.getLogger(TokenClientTest.class);

  static Vertx vertx;

  private static final int MOCK_PORT = 9230;

  private static final String OKAPI_URL = "http://localhost:" + MOCK_PORT;

  private static final String TENANT_OK = "diku";
  private static final String USER_OK = "dikuuser";
  private static final String PASSWORD_OK = "abc123";

  private static boolean enableLoginWithExpiry = false;

  WebClient webClient;

  TokenCache tokenCache;

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
          || !USER_OK.equals(username) ||  !PASSWORD_OK.equals(password)) {
        response.setStatusCode(400);
        response.putHeader("Content-Type", "text/plain");
        response.end("Bad tenant/username/password");
        return;
      }
      log.info("login with expiry ok");
      response.setStatusCode(201);
      response.putHeader("Set-Cookie",
          Cookie.cookie("otherToken", "validtoken")
              .setMaxAge(3000)
              .setSecure(true)
              .setPath("/")
              .setHttpOnly(true)
              .setSameSite(CookieSameSite.NONE).encode());

      response.putHeader("Set-Cookie",
          Cookie.cookie("folioAccessToken", "validtoken")
              .setMaxAge(300)
              .setSecure(true)
              .setPath("/")
              .setHttpOnly(true)
              .setSameSite(CookieSameSite.NONE)
              .encode());
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
    webClient = WebClient.create(vertx);
    tokenCache = TokenCache.create(10);
  }

  @After
  public void after() {
    webClient.close();
  }

  @Test
  public void nullCache(TestContext context) {
    Buffer xmlBody = Buffer.buffer("<hi/>");
    TokenClient tokenClient = new TokenClient(OKAPI_URL, webClient, null /* cache */,
        TENANT_OK, USER_OK, () -> Future.succeededFuture(PASSWORD_OK));
    tokenClient.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .map(response -> {
          Assert.assertEquals(xmlBody, response.bodyAsBuffer());
          return null;
        })
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void nullPassword(TestContext context) {
    Buffer xmlBody = Buffer.buffer("<hi/>");
    TokenClient tokenClient = new TokenClient(OKAPI_URL, webClient, tokenCache,
        TENANT_OK, USER_OK, () -> Future.succeededFuture(null));

    tokenClient.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_BAD_REQUEST))
        .compose(request -> request.sendBuffer(xmlBody))
        .map(response -> {
          Assert.assertEquals(xmlBody, response.bodyAsBuffer());
          return null;
        })
        .onComplete(context.asyncAssertFailure(
            t -> assertThat(t.getMessage(), is("Bad tenant/username/password"))
        ));
  }

  @Test
  public void legacy(TestContext context) {
    Buffer xmlBody = Buffer.buffer("<hi/>");
    TokenClient tokenClient = new TokenClient(OKAPI_URL, webClient, tokenCache,
        TENANT_OK, USER_OK, () -> Future.succeededFuture(PASSWORD_OK));
    Future<Void> f = Future.succeededFuture();
    f = f.compose(x -> tokenClient.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .map(response -> {
          Assert.assertEquals(xmlBody, response.bodyAsBuffer());
          return null;
        }));
    f = f.compose(x -> tokenClient.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .map(response -> {
          Assert.assertEquals(xmlBody, response.bodyAsBuffer());
          return null;
        }));
    f.onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void withExpiry(TestContext context) {
    enableLoginWithExpiry = true;
    Buffer xmlBody = Buffer.buffer("<hi/>");
    TokenClient tokenClient = new TokenClient(OKAPI_URL, webClient, tokenCache,
        TENANT_OK, USER_OK, () -> Future.succeededFuture(PASSWORD_OK));
    Future<Void> f = Future.succeededFuture();
    f = f.compose(x -> tokenClient.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .map(response -> {
          Assert.assertEquals(xmlBody, response.bodyAsBuffer());
          return null;
        }));
    f = f.compose(x -> tokenClient.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_CREATED))
        .compose(request -> request.sendBuffer(xmlBody))
        .map(response -> {
          Assert.assertEquals(xmlBody, response.bodyAsBuffer());
          return null;
        }));
    f.onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void badPasswordWithExpiry(TestContext context) {
    enableLoginWithExpiry = true;
    Buffer xmlBody = Buffer.buffer("<hi/>");
    TokenClient tokenClient = new TokenClient(OKAPI_URL, webClient, tokenCache,
        TENANT_OK, USER_OK, () -> Future.succeededFuture("bad"));
    tokenClient.getToken(webClient.postAbs(OKAPI_URL + "/echo")
            .putHeader("Content-Type", "text/xml")
            .expect(ResponsePredicate.SC_BAD_REQUEST))
        .compose(request -> request.sendBuffer(xmlBody))
        .map(response -> {
          Assert.assertEquals(xmlBody, response.bodyAsBuffer());
          return null;
        })
        .onComplete(context.asyncAssertFailure(
            t -> assertThat(t.getMessage(), is("Bad tenant/username/password"))
        ));
  }

}
