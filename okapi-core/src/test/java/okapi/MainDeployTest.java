package okapi;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.MainDeploy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class MainDeployTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "9130"));
  private Async async;
  private Vertx vertx;
  private RamlDefinition api;

  @Before
  public void setUp(TestContext context) {
    logger.debug("starting MainClusterTest");
    async = context.async();
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
    RestAssured.port = port;
    async.complete();
  }

  @After
  public void tearDown(TestContext context) {
    async = context.async();
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

      c = api.createRestAssured();
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

      c = api.createRestAssured();
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

      c = api.createRestAssured();
      r = c.given().get("/_/proxy/modules")
        .then().statusCode(200).log().ifValidationFails().extract().response();

      Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());
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

      c = api.createRestAssured();
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
}
