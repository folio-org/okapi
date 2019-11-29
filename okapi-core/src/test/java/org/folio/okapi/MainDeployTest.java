package org.folio.okapi;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.common.OkapiLogger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class MainDeployTest {

  private final Logger logger = OkapiLogger.get();
  private final int port = 9230;
  private Async async;
  private Vertx vertx;
  private RamlDefinition api;

  @Before
  public void setUp(TestContext context) {
    // can't set Verticle options so we set a property instead
    System.setProperty("port", Integer.toString(port));
    async = context.async();
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
    RestAssured.port = port;
    async.complete();
  }

  @After
  public void tearDown(TestContext context) {
    async = context.async();
    System.setProperty("port", ""); // disable port by emptying it
    if (vertx == null) {
      async.complete();
    } else {
      vertx.close(x -> {
        vertx = null;
        async.complete();
      });
    }
  }

  @Test
  public void testInitWithException(TestContext context) {
    new MainDeploy().init(null, context.asyncAssertFailure(throwable -> {
      context.assertTrue(throwable instanceof NullPointerException);
    }));
  }

  @Test
  public void testHelp(TestContext context) {
    async = context.async();

    String[] args = {"help"};
    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertTrue(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void testBadOption(TestContext context) {
    async = context.async();

    String[] args = {"-bad-option"};
    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertFalse(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void testBadMode(TestContext context) {
    async = context.async();

    String[] args = {"bad"};
    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertFalse(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void testNoArgs(TestContext context) {
    async = context.async();

    String[] args = {};
    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      Assert.assertFalse(res.succeeded());
      vertx = res.succeeded() ? res.result() : null;
      async.complete();
    });
  }

  @Test
  public void testDevMode(TestContext context) {
    async = context.async();

    String[] args = {"dev"};

    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertTrue("main1 " + res.cause(), res.succeeded());

      RestAssuredClient c;
      Response r;

      c = api.createRestAssured3();
      r = c.given().get("/_/version")
        .then().statusCode(200).log().ifValidationFails().extract().response();

      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
      async.complete();
    });
  }

  @Test
  public void testDeploymentMode(TestContext context) {
    async = context.async();

    String[] args = {"deployment"};

    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertTrue("main1" + res.cause(), res.succeeded());

      RestAssuredClient c;
      Response r;

      c = api.createRestAssured3();
      r = c.given().get("/_/deployment/modules")
        .then().statusCode(200).log().ifValidationFails().extract().response();

      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
      async.complete();
    });
  }

  @Test
  public void testProxyMode(TestContext context) {
    async = context.async();

    String[] args = {"proxy"};

    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertTrue("main1 " + res.cause(), res.succeeded());

      RestAssuredClient c;
      Response r;

      c = api.createRestAssured3();
      r = c.given().get("/_/proxy/modules")
        .then().statusCode(200).log().ifValidationFails().extract().response();

      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
      async.complete();
    });
  }

  @Test
  public void testConfFileOk(TestContext context) {
    async = context.async();

    String[] args = {"-conf", "src/test/resources/okapi1.json"};

    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertTrue("main1 " + res.cause(), res.succeeded());

      RestAssuredClient c;
      Response r;

      c = api.createRestAssured3();
      r = c.given().get("/_/proxy/modules")
        .then().statusCode(200).log().ifValidationFails().extract().response();

      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
      async.complete();
    });
  }

  @Test
  public void testConfFileNotFound(TestContext context) {
    async = context.async();

    String[] args = {"-conf", "src/test/resources/okapiNotFound.json"};

    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      Assert.assertTrue(res.failed());
      async.complete();
    });
  }

  @Test
  public void testClusterMode(TestContext context) {
    async = context.async();

    String[] args = {"cluster"};

    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertTrue("main1 " + res.cause(), res.succeeded());

      RestAssuredClient c;
      Response r;

      c = api.createRestAssured3();

      r = c.given().get("/_/proxy/modules")
        .then().statusCode(200).log().ifValidationFails().extract().response();
      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
      async.complete();
    });
  }

  @Test
  public void testClusterModeFail1(TestContext context) {
    async = context.async();

    String[] args = {"cluster", "-cluster-host", "foobar", "-cluster-port", "5701"};

    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertTrue("main1 " + res.cause(), res.failed());
      async.complete();
    });
  }

  @Test
  public void testClusterModeFail2(TestContext context) {
    async = context.async();

    String[] args = {"cluster", "-hazelcast-config-file", "foobar"};

    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertTrue("main1 " + res.cause(), res.failed());
      async.complete();
    });
  }

  @Test
  public void testClusterModeFail3(TestContext context) {
    async = context.async();

    String[] args = {"cluster", "-hazelcast-config-cp", "foobar"};

    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertTrue("main1 " + res.cause(), res.failed());
      async.complete();
    });
  }

  @Test
  public void testClusterModeFail4(TestContext context) {
    async = context.async();

    String[] args = {"cluster", "-hazelcast-config-url", "foobar"};

    MainDeploy d = new MainDeploy();
    d.init(args, res -> {
      vertx = res.succeeded() ? res.result() : null;
      Assert.assertTrue("main1 " + res.cause(), res.failed());
      async.complete();
    });
  }

  @Test
  public void testOkapiSamePort(TestContext context) {
    async = context.async();

    String[] args = {"dev"};

    MainDeploy d1 = new MainDeploy();
    d1.init(args, res1 -> {
      vertx = res1.succeeded() ? res1.result() : null;
      Assert.assertTrue("d1 " + res1.cause(), res1.succeeded());

      MainDeploy d2 = new MainDeploy();
      d2.init(args, res2 -> {
        Assert.assertTrue("d2 " + res2.cause(), res2.failed());
        async.complete();
      });
    });
  }
}
