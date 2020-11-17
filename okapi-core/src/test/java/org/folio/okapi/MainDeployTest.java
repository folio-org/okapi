package org.folio.okapi;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class MainDeployTest {

  private final Logger logger = OkapiLogger.get();
  private static final int port = 9230;
  private static RamlDefinition api;

  @BeforeClass
  public static void setupBeforeClass(TestContext context) {
    System.setProperty("vertx.logger-delegate-factory-class-name",
        "io.vertx.core.logging.Log4jLogDelegateFactory");
    // can't set Verticle options so we set a property instead
    System.setProperty("port", Integer.toString(port));
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
    RestAssured.port = port;
  }

  @AfterClass
  public static void after(TestContext context) {
    System.clearProperty("port");
  }

  @Test
  public void testInitWithException(TestContext context) {
    new MainDeploy().init(null, context.asyncAssertFailure(throwable -> {
      context.assertTrue(throwable instanceof NullPointerException);
    }));
  }

  @Test
  public void testHelp(TestContext context) {
    String[] args = {"help"};
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertSuccess(vertx -> context.assertNull(vertx)));
  }

  @Test
  public void testBadOption(TestContext context) {
    String[] args = {"-bad-option"};
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertFailure());
  }

  @Test
  public void testBadMode(TestContext context) {
    String[] args = {"bad"};
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertFailure());
  }

  @Test
  public void testNoArgs(TestContext context) {
    String[] args = {};
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertFailure());
  }

  @Test
  public void testDevMode(TestContext context) {
    String[] args = {"dev"};

    Async async = context.async();
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertSuccess(vertx -> {
      RestAssuredClient c;
      Response r;

      c = api.createRestAssured3();
      r = c.given().get("/_/version")
        .then().statusCode(200).log().ifValidationFails().extract().response();

      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
      vertx.close(context.asyncAssertSuccess(x -> async.complete()));
    }));
    async.await();
  }

  @Test
  public void testDeploymentMode(TestContext context) {
    String[] args = {"deployment"};

    Async async = context.async();
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertSuccess(vertx -> {
      RestAssuredClient c;
      Response r;

      c = api.createRestAssured3();
      r = c.given().get("/_/deployment/modules")
        .then().statusCode(200).log().ifValidationFails().extract().response();

      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
      vertx.close(context.asyncAssertSuccess(x -> async.complete()));
    }));
    async.await();
  }

  @Test
  public void testProxyMode(TestContext context) {
    String[] args = {"proxy"};

    Async async = context.async();
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertSuccess(vertx -> {
      RestAssuredClient c;
      Response r;

      c = api.createRestAssured3();
      r = c.given().get("/_/proxy/modules")
        .then().statusCode(200).log().ifValidationFails().extract().response();

      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
      vertx.close(context.asyncAssertSuccess(x -> async.complete()));
    }));
    async.await();
  }

  @Test
  public void testConfFileOk(TestContext context) {
    String[] args = {"-conf", "src/test/resources/okapi1.json"};

    Async async = context.async();
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertSuccess(vertx -> {
      RestAssuredClient c;
      Response r;

      c = api.createRestAssured3();
      r = c.given().get("/_/proxy/modules")
        .then().statusCode(200).log().ifValidationFails().extract().response();

      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
      vertx.close(context.asyncAssertSuccess(x -> async.complete()));
    }));
    async.await();
  }

  @Test
  public void testConfFileNotFound(TestContext context) {
    String[] args = {"-conf", "src/test/resources/okapiNotFound.json"};

    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertFailure());
  }

  @Test
  public void testClusterMode(TestContext context) {
    String[] args = {"cluster"};

    Async async = context.async();
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertSuccess(vertx -> {
      RestAssuredClient c;
      Response r;

      c = api.createRestAssured3();

      r = c.given().get("/_/proxy/modules")
        .then().statusCode(200).log().ifValidationFails().extract().response();
      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
      vertx.close(context.asyncAssertSuccess(x -> async.complete()));
    }));
    async.await();
  }

  @Test
  public void testClusterModeFail1(TestContext context) {
    String[] args = {"cluster", "-cluster-host", "foobar", "-cluster-port", "5701"};

    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertFailure());
  }

  @Test
  public void testClusterModeFail2(TestContext context) {
    String[] args = {"cluster", "-hazelcast-config-file", "foobar"};
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertFailure());
  }

  @Test
  public void testClusterModeFail3(TestContext context) {
    String[] args = {"cluster", "-hazelcast-config-cp", "foobar"};
    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertFailure());
  }

  @Test
  public void testClusterModeFail4(TestContext context) {
    String[] args = {"cluster", "-hazelcast-config-url", "foobar"};

    MainDeploy d = new MainDeploy();
    d.init(args, context.asyncAssertFailure());
  }

  @Test
  public void testOkapiSamePort(TestContext context) {
    String[] args = {"dev"};

    Async async = context.async();
    MainDeploy d1 = new MainDeploy();
    d1.init(args, context.asyncAssertSuccess(vertx -> {
      MainDeploy d2 = new MainDeploy();
      d2.init(args, context.asyncAssertFailure(
          x -> vertx.close(context.asyncAssertSuccess(y -> async.complete()))));
    }));
    async.await();
  }

}
